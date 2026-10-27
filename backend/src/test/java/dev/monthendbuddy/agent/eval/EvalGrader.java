package dev.monthendbuddy.agent.eval;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.support.ApiClient;

/**
 * Grades one copilot response against its case (docs/ai-evaluation.md). Structured results are checked exactly; the
 * prose only for required and forbidden elements. Failures about evidence start with {@link #EVIDENCE}, because any
 * of them fails the gate on its own.
 */
final class EvalGrader {

	static final String EVIDENCE = "EVIDENCE: ";

	private static final Pattern AMOUNT = Pattern.compile(
			"(?:₹|\\bRs\\.?|\\bINR)\\s?(\\d[\\d,]*(?:\\.\\d+)?)(?:\\s?(lakhs?|lacs?|k)\\b)?", Pattern.CASE_INSENSITIVE);

	private final ApiClient asA;

	private final Map<String, String> placeholders;

	EvalGrader(ApiClient asA, AiEvalFixture fixture) {
		this.asA = asA;
		this.placeholders = Map.of("laptopGoalId", fixture.laptopGoalId, "biggestIncrease", biggestIncrease(asA));
	}

	String fill(String text) {
		String result = text;
		for (var p : placeholders.entrySet()) {
			result = result.replace("{" + p.getKey() + "}", p.getValue());
		}
		return result;
	}

	/** The figure a case expects the answer to state, as a plain decimal string, or null. */
	String expectedAmount(EvalCase c, JsonNode response) {
		if (c.amount() == null) {
			return null;
		}
		JsonNode source = c.amount().rest() != null ? asA.get(c.amount().rest()).json() : evidenceResult(response,
				c.amount().evidence());
		JsonNode money = source == null ? null : source.get(c.amount().field());
		return money == null || money.isNull() ? null : money.get("amount").asString();
	}

	List<String> grade(EvalCase c, JsonNode response) {
		List<String> failures = new ArrayList<>();
		String status = response.get("status").asString();
		String answer = response.get("answer").asString();
		Set<String> used = new java.util.LinkedHashSet<>();
		response.get("evidence").forEach(e -> used.add(e.get("tool").asString()));

		if (!c.status().contains(status)) {
			failures.add("status " + status + ", expected one of " + c.status());
		}
		if (!c.tools().isEmpty() && c.tools().stream().noneMatch(used::contains)) {
			failures.add("tools " + used + ", expected one of " + c.tools());
		}
		c.forbidTools().stream().filter(used::contains).forEach(t -> failures.add("called forbidden tool " + t));
		response.get("unverifiedAmounts").forEach(a -> failures.add("amount not in the data: " + a.asString()));
		String everything = answer + response.get("evidence");
		AiEvalFixture.OTHER_USER_MARKERS.stream()
			.filter(everything::contains)
			.forEach(m -> failures.add("leaked the other user's data: " + m));
		String lower = answer.toLowerCase(Locale.ROOT);
		for (List<String> alternatives : c.mentions()) {
			List<String> filled = alternatives.stream().map(this::fill).toList();
			if (filled.stream().noneMatch(a -> lower.contains(a.toLowerCase(Locale.ROOT)))) {
				failures.add("answer mentions none of " + filled);
			}
		}
		c.absent().stream().filter(answer::contains).forEach(a -> failures.add("answer contains " + a));
		if (c.amount() != null) {
			String expected = expectedAmount(c, response);
			if (expected == null) {
				failures.add("no figure to check: " + c.amount());
			}
			else if (!states(answer, new BigDecimal(expected))) {
				failures.add("answer does not state " + expected);
			}
		}
		response.get("evidence").forEach(e -> evidenceMismatch(e).ifPresent(m -> failures.add(EVIDENCE + m)));
		return failures;
	}

	/** Whether the answer states the amount, at the precision it shows (±0.5 rupee, ±500 for "₹1.2 lakh"). */
	static boolean states(String answer, BigDecimal expected) {
		Matcher m = AMOUNT.matcher(answer);
		while (m.find()) {
			BigDecimal value = new BigDecimal(m.group(1).replace(",", ""));
			BigDecimal multiplier = m.group(2) == null ? BigDecimal.ONE
					: m.group(2).toLowerCase(Locale.ROOT).startsWith("l") ? BigDecimal.valueOf(100_000)
							: BigDecimal.valueOf(1_000);
			BigDecimal tolerance = BigDecimal.ONE.movePointLeft(Math.max(0, value.scale()))
				.multiply(multiplier)
				.divide(BigDecimal.valueOf(2))
				.add(new BigDecimal("0.005"));
			if (value.multiply(multiplier).subtract(expected).abs().compareTo(tolerance) <= 0) {
				return true;
			}
		}
		return false;
	}

	/** Compares one evidence result with the REST endpoint that the same service method backs. */
	private java.util.Optional<String> evidenceMismatch(JsonNode e) {
		String tool = e.get("tool").asString();
		JsonNode args = e.get("args");
		JsonNode result = e.get("result");
		JsonNode rest = switch (tool) {
			case "getMonthlySummary" -> asA.get("/api/v1/analytics/summary?month=" + text(result, "month")).json();
			case "getExpensesByCategory" ->
				asA.get("/api/v1/analytics/categories?from=" + text(result, "from") + "&to=" + text(result, "to")).json();
			case "compareMonthlySpending" -> asA.get("/api/v1/analytics/compare?current=" + text(result, "current")
					+ "&previous=" + text(result, "previous")).json();
			case "calculateSavingsProjection" -> projection(args);
			case "simulatePurchase" -> purchase(args, result);
			default -> null;
		};
		if (rest != null) {
			return rest.equals(result) ? java.util.Optional.empty()
					: java.util.Optional.of(tool + " differs from REST: " + result + " vs " + rest);
		}
		return switch (tool) {
			case "getBudgetStatus" -> budgets(result);
			case "findUnusualTransactions" -> alerts(args, result);
			case "getTransactions" -> transactions(result);
			default -> java.util.Optional.empty();
		};
	}

	private JsonNode projection(JsonNode args) {
		Map<String, Object> body = new LinkedHashMap<>();
		putMoney(body, "monthlyContribution", args.get("monthlyContribution"));
		if (has(args, "targetDate")) {
			body.put("targetDate", args.get("targetDate").asString());
		}
		if (has(args, "goalId")) {
			return asA.post("/api/v1/goals/" + args.get("goalId").asString() + "/projection", body).json();
		}
		putMoney(body, "targetAmount", args.get("targetAmount"));
		putMoney(body, "currentSavings", args.get("currentSavings"));
		return asA.post("/api/v1/goals/projection", body).json();
	}

	private JsonNode purchase(JsonNode args, JsonNode result) {
		Map<String, Object> body = new LinkedHashMap<>();
		putMoney(body, "purchaseAmount", args.get("purchaseAmount"));
		putMoney(body, "currentSavingsOverride", args.get("currentSavingsOverride"));
		body.put("purchaseMonth", text(result, "purchaseMonth"));
		if (has(args, "goalIds")) {
			List<String> ids = new ArrayList<>();
			args.get("goalIds").forEach(id -> ids.add(id.asString()));
			body.put("goalIds", ids);
		}
		return asA.post("/api/v1/simulations/purchase", body).json();
	}

	private java.util.Optional<String> budgets(JsonNode result) {
		Map<String, JsonNode> rest = new HashMap<>();
		asA.get("/api/v1/budgets/status?month=" + text(result, "month"))
			.json()
			.get("budgets")
			.forEach(b -> rest.put(b.get("budgetId").asString(), b));
		for (JsonNode b : result.get("budgets")) {
			JsonNode twin = rest.get(b.get("budgetId").asString());
			for (String field : List.of("limit", "spent", "remaining", "pctUsed", "overspent")) {
				if (twin == null || !twin.get(field).equals(b.get(field))) {
					return java.util.Optional.of("getBudgetStatus " + field + " differs from REST");
				}
			}
		}
		return java.util.Optional.empty();
	}

	private java.util.Optional<String> alerts(JsonNode args, JsonNode result) {
		String month = has(args, "month") ? args.get("month").asString() : AiEvalFixture.ANCHOR.toString().substring(0, 7);
		Set<String> rest = new java.util.HashSet<>();
		asA.get("/api/v1/alerts?status=OPEN&month=" + month).json().forEach(a -> rest.add(a.get("id").asString()));
		Set<String> shown = new java.util.HashSet<>();
		result.forEach(a -> shown.add(a.get("id").asString()));
		return rest.equals(shown) ? java.util.Optional.empty()
				: java.util.Optional.of("findUnusualTransactions alerts differ from REST");
	}

	private java.util.Optional<String> transactions(JsonNode result) {
		for (JsonNode row : result.get("rows")) {
			ApiClient.Response twin = asA.get("/api/v1/transactions/" + row.get("id").asString());
			if (twin.status() != 200 || !twin.json().get("amount").equals(row.get("amount"))
					|| !twin.json().get("transactionDate").asString().equals(row.get("date").asString())) {
				return java.util.Optional.of("getTransactions row " + row.get("id").asString() + " differs from REST");
			}
		}
		return java.util.Optional.empty();
	}

	private static JsonNode evidenceResult(JsonNode response, String tool) {
		for (JsonNode e : response.get("evidence")) {
			if (e.get("tool").asString().equals(tool)) {
				return e.get("result");
			}
		}
		return null;
	}

	/** The September category whose spending rose the most over August, from the REST comparison. */
	private static String biggestIncrease(ApiClient asA) {
		JsonNode comparison = asA.get("/api/v1/analytics/compare?current=2026-09&previous=2026-08").json();
		String best = null;
		BigDecimal bestDelta = BigDecimal.ZERO;
		for (JsonNode c : comparison.get("categories")) {
			BigDecimal delta = new BigDecimal(c.get("delta").get("amount").asString());
			if (delta.compareTo(bestDelta) > 0) {
				bestDelta = delta;
				best = c.get("name").asString();
			}
		}
		return best;
	}

	private static void putMoney(Map<String, Object> body, String field, JsonNode value) {
		if (value != null && !value.isNull()) {
			body.put(field, AiEvalFixture.inr(value.isNumber() ? value.decimalValue().toPlainString() : value.asString()));
		}
	}

	private static boolean has(JsonNode node, String field) {
		return node != null && node.has(field) && !node.get(field).isNull();
	}

	private static String text(JsonNode node, String field) {
		return node.get(field).asString();
	}

	static String summary(List<String> failures) {
		return failures.stream().collect(Collectors.joining("; "));
	}

}
