package dev.monthendbuddy.agent.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import jakarta.validation.Validation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import dev.monthendbuddy.agent.llm.ToolCall;
import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.NotFoundException;

class ToolRegistryTest {

	record EchoInput(@NotNull LocalDate day, @Max(50) Integer rows) {
	}

	/** Returns {@code rows} long strings, or fails the way a service would for special inputs. */
	static class EchoTool implements FinanceTool<EchoInput> {

		@Override
		public String name() {
			return "echo";
		}

		@Override
		public String description() {
			return "Echoes";
		}

		@Override
		public Class<EchoInput> inputType() {
			return EchoInput.class;
		}

		@Override
		public String statusLabel() {
			return "Echoing";
		}

		@Override
		public ToolResult execute(EchoInput input, ToolContext context) {
			if (input.day().getYear() == 1999) {
				throw new NotFoundException("Goal");
			}
			if (input.day().getYear() == 2000) {
				throw new IllegalStateException("boom");
			}
			int rows = input.rows() == null ? 1 : input.rows();
			return ToolResult.ok(IntStream.range(0, rows).mapToObj(i -> "row " + i + " " + "x".repeat(400)).toList(),
					null, null);
		}

	}

	private final JsonMapper json = JsonMapper.builder().build();

	private final ToolRegistry registry = new ToolRegistry(List.of(new EchoTool()), 
			Validation.buildDefaultValidatorFactory().getValidator(), json);

	private final ToolContext context = new ToolContext(new UserId(java.util.UUID.randomUUID()),
			ZoneId.of("Asia/Kolkata"), CurrencyCode.INR, LocalDate.of(2026, 10, 27));

	@Test
	void runsAValidCall() {
		ToolExecution execution = run("echo", "{\"day\":\"2026-10-01\",\"rows\":2}");
		assertThat(execution.ok()).isTrue();
		assertThat(execution.payload().get("data")).hasSize(2);
		assertThat(execution.arguments().get("rows").asInt()).isEqualTo(2);
	}

	@Test
	void unknownToolIsRejectedWithTheAllowList() {
		JsonNode error = run("deleteEverything", "{}").payload().get("error");
		assertThat(error.get("code").asString()).isEqualTo("UNKNOWN_TOOL");
		assertThat(error.get("message").asString()).contains("echo");
	}

	@Test
	void badJsonIsAnInvalidArgument() {
		assertThat(code(run("echo", "{day: oops"))).isEqualTo("INVALID_ARGUMENT");
		assertThat(code(run("echo", "[1,2]"))).isEqualTo("INVALID_ARGUMENT");
	}

	@Test
	void unparseableValueNamesTheField() {
		JsonNode error = run("echo", "{\"day\":\"last tuesday\"}").payload().get("error");
		assertThat(error.get("code").asString()).isEqualTo("INVALID_ARGUMENT");
		assertThat(error.get("field").asString()).isEqualTo("day");
	}

	@Test
	void validationViolationNamesTheField() {
		JsonNode error = run("echo", "{\"day\":\"2026-10-01\",\"rows\":500}").payload().get("error");
		assertThat(error.get("code").asString()).isEqualTo("INVALID_ARGUMENT");
		assertThat(error.get("field").asString()).isEqualTo("rows");
		assertThat(code(run("echo", "{}"))).isEqualTo("INVALID_ARGUMENT");
	}

	@Test
	void notFoundFromAServiceIsNotFound() {
		assertThat(code(run("echo", "{\"day\":\"1999-01-01\"}"))).isEqualTo("NOT_FOUND");
	}

	@Test
	void unexpectedFailureIsAToolError() {
		assertThat(code(run("echo", "{\"day\":\"2000-01-01\"}"))).isEqualTo("TOOL_FAILED");
	}

	@Test
	void oversizedResultIsTrimmedAndMarked() {
		ToolExecution execution = run("echo", "{\"day\":\"2026-10-01\",\"rows\":50}");
		assertThat(json.writeValueAsString(execution.payload()).length()).isLessThanOrEqualTo(ToolRegistry.MAX_RESULT_BYTES);
		assertThat(execution.payload().get("truncated").asBoolean()).isTrue();
		assertThat(execution.payload().get("data").size()).isBetween(1, 49);
		assertThat(execution.evidenceData()).isEqualTo(execution.payload().get("data"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "null", "\"day\"", "{\"day\":null}", "{\"day\":{\"$ne\":1}}", "{\"rows\":\"many\"}",
			"\u0000\u0001", "{\"day\":\"2026-10-01\",\"userId\":\"someone-else\"}" })
	void neverThrows(String arguments) {
		ToolExecution execution = run("echo", arguments);
		assertThat(execution.payload()).isNotNull();
	}

	@Test
	void nullNameNeverThrows() {
		assertThat(code(run(null, null))).isEqualTo("UNKNOWN_TOOL");
	}

	private ToolExecution run(String tool, String arguments) {
		return registry.execute(new ToolCall("call_1", tool, arguments), context);
	}

	private static String code(ToolExecution execution) {
		return execution.payload().get("error").get("code").asString();
	}

}
