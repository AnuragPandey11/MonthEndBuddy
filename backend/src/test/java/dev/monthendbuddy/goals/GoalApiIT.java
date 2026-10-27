package dev.monthendbuddy.goals;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.IntegrationTest;
import dev.monthendbuddy.transactions.TransactionFixtures;

class GoalApiIT extends IntegrationTest {

	private ApiClient as;

	private final YearMonth current = YearMonth.from(TransactionFixtures.today());

	@BeforeEach
	void newUser() {
		as = api().bearer(api().register().accessToken());
	}

	private static Map<String, Object> money(String amount) {
		return Map.of("amount", amount, "currency", "INR");
	}

	private Response createGoal(String name, String target, String saved, LocalDate targetDate) {
		Map<String, Object> body = new HashMap<>();
		body.put("name", name);
		body.put("targetAmount", money(target));
		body.put("currentAmount", money(saved));
		body.put("targetDate", targetDate.toString());
		return as.post("/api/v1/goals", body);
	}

	@Test
	void goalsTrackProgressAndBecomeAchieved() {
		Response created = createGoal("Laptop", "60000", "15000", current.plusMonths(6).atDay(1));
		assertThat(created.status()).isEqualTo(201);
		JsonNode goal = created.json();
		assertThat(goal.get("remaining").get("amount").asString()).isEqualTo("45000.00");
		assertThat(goal.get("progressPct").decimalValue()).isEqualByComparingTo("25.0");
		assertThat(goal.get("status").asString()).isEqualTo("ACTIVE");

		Map<String, Object> update = new HashMap<>();
		update.put("name", "Laptop");
		update.put("targetAmount", money("60000"));
		update.put("currentAmount", money("61000"));
		update.put("targetDate", current.plusMonths(6).atDay(1).toString());
		update.put("version", 0);
		JsonNode achieved = as.put("/api/v1/goals/" + goal.get("id").asString(), update).json();
		assertThat(achieved.get("status").asString()).isEqualTo("ACHIEVED");
		assertThat(achieved.get("progressPct").decimalValue()).isEqualByComparingTo("100.0");
		assertThat(achieved.get("remaining").get("amount").asString()).isEqualTo("0.00");
		assertThat(as.put("/api/v1/goals/" + goal.get("id").asString(), update).status()).isEqualTo(409);

		assertThat(as.delete("/api/v1/goals/" + goal.get("id").asString()).status()).isEqualTo(204);
		assertThat(as.get("/api/v1/goals").json()).isEmpty();
	}

	@Test
	void targetDateMustBeInTheFuture() {
		Response response = createGoal("Past", "1000", "0", TransactionFixtures.today());
		assertThat(response.status()).isEqualTo(422);
		assertThat(response.json().get("errors").get(0).get("field").asString()).isEqualTo("targetDate");
	}

	@Test
	void requiredMonthlyContributionForAnUnsavedGoal() {
		// Spec §3: "I want to save ₹1,00,000 in eight months."
		JsonNode projection = as.post("/api/v1/goals/projection", Map.of("targetAmount", money("100000"),
				"targetDate", current.plusMonths(8).atDay(1).toString()))
			.json();
		assertThat(projection.get("monthsRemaining").asInt()).isEqualTo(8);
		assertThat(projection.get("requiredMonthly").get("amount").asString()).isEqualTo("12500.00");
		assertThat(projection.get("status").asString()).isEqualTo("ON_TRACK");
		assertThat(projection.get("projectedCompletion").asString()).isEqualTo(current.plusMonths(8).toString());
		assertThat(projection.get("series")).hasSize(9);
		assertThat(projection.get("series").get(8).get("balance").get("amount").asString()).isEqualTo("100000.00");
		assertThat(labels(projection)).contains("goalSaved=DEFAULT_ASSUMPTION", "monthlyContribution=DERIVED",
				"return=DEFAULT_ASSUMPTION");
	}

	@Test
	void whatIfContributionsGiveBehindOrNever() {
		String id = createGoal("Trip", "50000", "10000", current.plusMonths(4).atDay(1)).json().get("id").asString();
		JsonNode required = as.post("/api/v1/goals/" + id + "/projection", null).json();
		assertThat(required.get("requiredMonthly").get("amount").asString()).isEqualTo("10000.00");
		assertThat(labels(required)).contains("goalSaved=USER_PROFILE");

		JsonNode behind = as.post("/api/v1/goals/" + id + "/projection", Map.of("monthlyContribution", money("5000")))
			.json();
		assertThat(behind.get("status").asString()).isEqualTo("BEHIND");
		assertThat(behind.get("projectedCompletion").asString()).isEqualTo(current.plusMonths(8).toString());
		assertThat(labels(behind)).contains("monthlyContribution=USER_STATED");

		JsonNode never = as.post("/api/v1/goals/" + id + "/projection", Map.of("monthlyContribution", money("0")))
			.json();
		assertThat(never.get("status").asString()).isEqualTo("NEVER");
		assertThat(never.get("projectedCompletion").isNull()).isTrue();

		JsonNode passed = as.post("/api/v1/goals/" + id + "/projection",
				Map.of("targetDate", current.atEndOfMonth().toString())).json();
		assertThat(passed.get("status").asString()).isEqualTo("DEADLINE_PASSED");
		assertThat(passed.get("requiredMonthly").isNull()).isTrue();
	}

	private static List<String> labels(JsonNode projection) {
		List<String> labels = new ArrayList<>();
		projection.get("assumptions").forEach(a -> labels.add(a.get("name").asString() + "=" + a.get("label").asString()));
		return labels;
	}

}
