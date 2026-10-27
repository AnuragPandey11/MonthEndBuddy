package dev.monthendbuddy.analytics;

import static dev.monthendbuddy.transactions.TransactionFixtures.GROCERIES;
import static dev.monthendbuddy.transactions.TransactionFixtures.SALARY;
import static dev.monthendbuddy.transactions.TransactionFixtures.transaction;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.IntegrationTest;
import dev.monthendbuddy.transactions.TransactionFixtures;

/** "Can I afford a ₹60,000 laptop?" (spec §3) against three complete months of data (P2.7). */
class AffordabilityApiIT extends IntegrationTest {

	private ApiClient as;

	private final YearMonth current = YearMonth.from(TransactionFixtures.today());

	@BeforeEach
	void threeMonthsOfData() {
		as = api().bearer(api().register().accessToken());
		for (int back = 1; back <= 3; back++) {
			YearMonth m = current.minusMonths(back);
			as.post("/api/v1/transactions", transaction("INCOME", "95000", m.atDay(1), "Salary", null, SALARY));
			as.post("/api/v1/transactions", transaction("EXPENSE", "65000", m.atDay(5), "Spending", null, GROCERIES));
		}
	}

	private static Map<String, Object> money(String amount) {
		return Map.of("amount", amount, "currency", "INR");
	}

	private JsonNode simulate(Map<String, Object> body) {
		Response response = as.post("/api/v1/simulations/purchase", body);
		assertThat(response.status()).as(response.body()).isEqualTo(200);
		return response.json();
	}

	@Test
	void withoutKnownSavingsTheAnswerIsInsufficientData() {
		JsonNode result = simulate(Map.of("purchaseAmount", money("60000")));
		assertThat(result.get("verdict").asString()).isEqualTo("INSUFFICIENT_DATA");
		assertThat(result.get("dataQuality").get(0).get("code").asString()).isEqualTo("MISSING_INPUT");
		assertThat(result.get("monthlySurplus").get("amount").asString()).isEqualTo("30000.00");
		assertThat(result.get("monthsOfData").asInt()).isEqualTo(3);
	}

	@Test
	void savingsFromSettingsDecideTheVerdictAndAreLabelledAsProfileData() {
		Response patched = as.patch("/api/v1/me/settings", Map.of("currentSavings", money("200000")));
		assertThat(patched.status()).as(patched.body()).isEqualTo(200);

		JsonNode result = simulate(Map.of("purchaseAmount", money("60000")));
		assertThat(result.get("verdict").asString()).isEqualTo("AFFORDABLE_WITH_TRADEOFFS");
		assertThat(result.get("savingsAfterPurchase").get("amount").asString()).isEqualTo("140000.00");
		assertThat(result.get("emergencyBuffer").get("amount").asString()).isEqualTo("195000.00");
		assertThat(result.get("averageMonthlyIncome").get("amount").asString()).isEqualTo("95000.00");
		assertThat(result.get("recoveryMonths").decimalValue()).isEqualByComparingTo("2.0");
		assertThat(labels(result)).contains("currentSavings=USER_PROFILE", "averageMonthlyIncome=DERIVED",
				"emergencyBuffer=DEFAULT_ASSUMPTION");
	}

	@Test
	void aStatedSavingsFigureOverridesSettingsAndGoalsAddTheirNeeds() {
		as.post("/api/v1/goals", Map.of("name", "Trip", "targetAmount", money("50000"), "currentAmount", money("10000"),
				"targetDate", current.plusMonths(4).atDay(1).toString()));
		JsonNode result = simulate(Map.of("purchaseAmount", money("60000"), "currentSavingsOverride", money("500000")));
		assertThat(result.get("verdict").asString()).isEqualTo("AFFORDABLE");
		assertThat(result.get("goalNeed").get("amount").asString()).isEqualTo("10000.00");
		assertThat(result.get("recoveryMonths").decimalValue()).isEqualByComparingTo("3.0");
		assertThat(result.get("goalImpacts").get(0).get("name").asString()).isEqualTo("Trip");
		assertThat(result.get("goalImpacts").get(0).get("delayMonths").asInt()).isEqualTo(2);
		assertThat(labels(result)).contains("currentSavings=USER_STATED", "goalNeed=DERIVED", "savingsSetAside=DERIVED");
		// The stated figure is a total: the ₹10,000 already saved toward Trip is set aside before the purchase.
		assertThat(result.get("savingsSetAside").get("amount").asString()).isEqualTo("10000.00");
		assertThat(result.get("availableSavings").get("amount").asString()).isEqualTo("490000.00");
		assertThat(result.get("savingsAfterPurchase").get("amount").asString()).isEqualTo("430000.00");

		assertThat(simulate(Map.of("purchaseAmount", money("600000"), "currentSavingsOverride", money("500000")))
			.get("verdict")
			.asString()).isEqualTo("NOT_AFFORDABLE");
	}

	@Test
	void goalMoneyIsNotFreeToSpendAndGoalsHoldingMoreThanSavingsAreFlagged() {
		as.patch("/api/v1/me/settings", Map.of("currentSavings", money("80000")));
		as.post("/api/v1/goals", Map.of("name", "Phone", "targetAmount", money("80000"), "currentAmount", money("20000"),
				"targetDate", current.plusMonths(3).atDay(1).toString()));

		JsonNode result = simulate(Map.of("purchaseAmount", money("60000")));
		assertThat(result.get("currentSavings").get("amount").asString()).isEqualTo("80000.00");
		assertThat(result.get("savingsSetAside").get("amount").asString()).isEqualTo("20000.00");
		assertThat(result.get("availableSavings").get("amount").asString()).isEqualTo("60000.00");
		assertThat(result.get("savingsAfterPurchase").get("amount").asString()).isEqualTo("0.00");
		assertThat(result.get("dataQuality")).isEmpty();

		as.post("/api/v1/goals", Map.of("name", "Car", "targetAmount", money("500000"), "currentAmount",
				money("70000"), "targetDate", current.plusMonths(24).atDay(1).toString()));
		JsonNode over = simulate(Map.of("purchaseAmount", money("1000")));
		assertThat(over.get("availableSavings").get("amount").asString()).isEqualTo("-10000.00");
		assertThat(over.get("verdict").asString()).isEqualTo("NOT_AFFORDABLE");
		assertThat(over.get("dataQuality").get(0).get("code").asString()).isEqualTo("GOALS_EXCEED_SAVINGS");
	}

	@Test
	void aNewUserHasLowDataAndNoAverages() {
		ApiClient fresh = api().bearer(api().register().accessToken());
		JsonNode result = fresh.post("/api/v1/simulations/purchase",
				Map.of("purchaseAmount", money("1000"), "currentSavingsOverride", money("5000")))
			.json();
		assertThat(result.get("verdict").asString()).isEqualTo("INSUFFICIENT_DATA");
		assertThat(result.get("averageMonthlyIncome").isNull()).isTrue();
		assertThat(result.get("dataQuality").get(0).get("code").asString()).isEqualTo("LOW_DATA");
	}

	@Test
	void settingsUpdatesAreValidatedAndPartial() {
		assertThat(as.patch("/api/v1/me/settings", Map.of("timezone", "Mars/Olympus")).status()).isEqualTo(422);
		assertThat(as.patch("/api/v1/me/settings", Map.of("emergencyBufferMonths", 25)).status()).isEqualTo(422);
		assertThat(as.patch("/api/v1/me/settings", Map.of("anomalyMultiplier", 1.2)).status()).isEqualTo(422);
		JsonNode updated = as.patch("/api/v1/me/settings", Map.of("emergencyBufferMonths", 6, "timezone", "Europe/London"))
			.json();
		assertThat(updated.get("emergencyBufferMonths").asInt()).isEqualTo(6);
		assertThat(updated.get("timezone").asString()).isEqualTo("Europe/London");
		assertThat(updated.get("largeTxnThreshold").get("amount").asString()).isEqualTo("25000.00");
		assertThat(updated.get("currentSavings").isNull()).isTrue();
	}

	private static List<String> labels(JsonNode result) {
		List<String> labels = new ArrayList<>();
		result.get("assumptions").forEach(a -> labels.add(a.get("name").asString() + "=" + a.get("label").asString()));
		return labels;
	}

}
