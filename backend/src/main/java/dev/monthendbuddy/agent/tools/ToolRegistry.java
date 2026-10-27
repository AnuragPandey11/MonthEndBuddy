package dev.monthendbuddy.agent.tools;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import dev.monthendbuddy.agent.llm.ToolCall;
import dev.monthendbuddy.agent.llm.ToolSpec;
import dev.monthendbuddy.common.error.NotFoundException;
import dev.monthendbuddy.common.error.ValidationException;

/**
 * The allow-list of tools the model may call (LLD §8.2). {@link #execute} never throws: every failure becomes a
 * structured error that goes back to the model, which can then correct its arguments. Results are capped at about
 * 8 KB by trimming the longest lists, and marked {@code "truncated": true} when that happens.
 */
@Component
public class ToolRegistry {

	static final int MAX_RESULT_BYTES = 8 * 1024;

	/** Added to every argument error: the spike saw the model invent figures after a tool error (ADR 0001). */
	static final String RETRY_HINT = " Call the tool again with corrected arguments; never answer without the data.";

	private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

	private final Map<String, FinanceTool<?>> tools;

	private final Validator validator;

	private final JsonMapper json;

	public ToolRegistry(List<FinanceTool<?>> tools, Validator validator, JsonMapper json) {
		this.tools = tools.stream()
			.sorted(Comparator.comparing(FinanceTool::name))
			.collect(Collectors.toMap(FinanceTool::name, t -> t, (a, b) -> {
				throw new IllegalStateException("Duplicate tool name " + a.name());
			}, LinkedHashMap::new));
		this.validator = validator;
		this.json = json;
	}

	public List<ToolSpec> specs() {
		return tools.values().stream().map(t -> new ToolSpec(t.name(), t.description(), t.inputType())).toList();
	}

	public String statusLabel(String toolName) {
		FinanceTool<?> tool = tools.get(toolName);
		return tool == null ? "Checking" : tool.statusLabel();
	}

	public ToolExecution execute(ToolCall call, ToolContext context) {
		long started = System.nanoTime();
		JsonNode arguments = parseArguments(call.argumentsJson());
		ToolResult result = run(call, arguments, context);
		JsonNode evidence = cap(payload(result));
		JsonNode payload = evidence.deepCopy();
		FinanceTool<?> tool = tools.get(call.name());
		if (tool != null && payload.get("data") instanceof ObjectNode data) {
			tool.uiOnlyFields().forEach(data::remove);
		}
		return new ToolExecution(call, arguments, result, payload, evidence.get("data"),
				(System.nanoTime() - started) / 1_000_000);
	}

	private ToolResult run(ToolCall call, JsonNode arguments, ToolContext context) {
		FinanceTool<?> tool = tools.get(call.name());
		if (tool == null) {
			log.warn("Model called an unknown tool");
			return ToolResult.error(ToolError.UNKNOWN_TOOL, null,
					"No tool named '" + safeName(call.name()) + "'. Available: " + String.join(", ", tools.keySet()));
		}
		if (arguments == null) {
			return invalid(null, "Arguments must be a JSON object.");
		}
		try {
			return runTyped(tool, arguments, context);
		}
		catch (DatabindException e) {
			String field = e.getPath()
				.stream()
				.map(ref -> ref.getPropertyName() != null ? ref.getPropertyName() : "[" + ref.getIndex() + "]")
				.collect(Collectors.joining("."));
			return invalid(field.isEmpty() ? null : field,
					"Could not read this value; check its type and format (dates are YYYY-MM-DD, months YYYY-MM).");
		}
		catch (ValidationException e) { // includes an unsupported currency
			var first = e.errors().getFirst();
			return invalid(first.field(), first.message() + ".");
		}
		catch (NotFoundException e) {
			return ToolResult.error(ToolError.NOT_FOUND, null, e.getMessage());
		}
		catch (RuntimeException e) {
			log.error("Tool {} failed", tool.name(), e);
			return ToolResult.error(ToolError.TOOL_FAILED, null, "The tool failed; do not retry it this turn");
		}
	}

	private <I> ToolResult runTyped(FinanceTool<I> tool, JsonNode arguments, ToolContext context) {
		I input = json.treeToValue(arguments, tool.inputType());
		Set<ConstraintViolation<I>> violations = validator.validate(input);
		if (!violations.isEmpty()) {
			ConstraintViolation<I> first = violations.stream()
				.min(Comparator.comparing(v -> v.getPropertyPath().toString()))
				.orElseThrow();
			return invalid(first.getPropertyPath().toString(), first.getMessage() + ".");
		}
		return tool.execute(input, context);
	}

	private static ToolResult invalid(String field, String message) {
		return ToolResult.error(ToolError.INVALID_ARGUMENT, field, message + RETRY_HINT);
	}

	/** Missing or empty arguments mean "no arguments"; anything that is not a JSON object gives null. */
	private JsonNode parseArguments(String argumentsJson) {
		if (argumentsJson == null || argumentsJson.isBlank()) {
			return json.createObjectNode();
		}
		try {
			JsonNode node = json.readTree(argumentsJson);
			return node != null && node.isObject() ? node : null;
		}
		catch (JacksonException e) {
			return null;
		}
	}

	private JsonNode payload(ToolResult result) {
		ObjectNode payload = json.createObjectNode();
		if (!result.ok()) {
			payload.set("error", json.valueToTree(result.error()));
			return payload;
		}
		payload.set("data", json.valueToTree(result.data()));
		if (result.provenance() != null) {
			payload.set("provenance", json.valueToTree(result.provenance()));
		}
		if (!result.assumptions().isEmpty()) {
			payload.set("assumptions", json.valueToTree(result.assumptions()));
		}
		return payload;
	}

	/**
	 * Drops rows from the end of the longest list until the payload fits; tools already bound their output, so this
	 * is a backstop.
	 */
	JsonNode cap(JsonNode payload) {
		boolean truncated = false;
		while (size(payload) > MAX_RESULT_BYTES) {
			ArrayNode longest = longestArray(payload, null);
			if (longest == null || longest.isEmpty()) {
				ObjectNode tooLarge = json.createObjectNode();
				tooLarge.set("error", json.valueToTree(new ToolError(ToolError.TOOL_FAILED, null,
						"The result was too large; ask for a shorter period")));
				return tooLarge;
			}
			// Remove about a tenth of the list at a time, so a list just over the cap loses only a few rows.
			int remove = Math.max(1, longest.size() / 10);
			for (int i = 0; i < remove; i++) {
				longest.remove(longest.size() - 1);
			}
			truncated = true;
		}
		if (truncated && payload instanceof ObjectNode object) {
			object.put("truncated", true);
		}
		return payload;
	}

	private int size(JsonNode node) {
		return json.writeValueAsString(node).getBytes(StandardCharsets.UTF_8).length;
	}

	private static ArrayNode longestArray(JsonNode node, ArrayNode best) {
		if (node.isArray() && (best == null || node.size() > best.size())) {
			best = (ArrayNode) node;
		}
		for (JsonNode child : node) {
			best = longestArray(child, best);
		}
		return best;
	}

	private static String safeName(String name) {
		if (name == null) {
			return "";
		}
		String cleaned = name.replaceAll("[^A-Za-z0-9_]", "");
		return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
	}

}
