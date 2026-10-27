package dev.monthendbuddy.analytics;

import static dev.monthendbuddy.transactions.TransactionFixtures.DINING;
import static dev.monthendbuddy.transactions.TransactionFixtures.today;
import static dev.monthendbuddy.transactions.TransactionFixtures.transaction;
import static dev.monthendbuddy.transactions.TransactionFixtures.withVersion;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.IntegrationTest;

/** Alerts are raised after writes, explained by their numbers, resolvable, and follow transaction changes. */
class AnomalyApiIT extends IntegrationTest {

	private ApiClient as;

	@BeforeEach
	void newUser() {
		as = api().bearer(api().register().accessToken());
	}

	private String spend(String amount, int daysAgo, String merchant) {
		return as.post("/api/v1/transactions",
				transaction("EXPENSE", amount, today().minusDays(daysAgo), "Meal", merchant, DINING))
			.json()
			.get("id")
			.asString();
	}

	@Test
	void aLargeExpenseIsExplainedByItsCategoryHistory() {
		for (int i = 0; i < 5; i++) {
			spend("400", 20 + i, "Cafe " + i);
		}
		spend("2000", 1, "The Leela Palace");
		JsonNode alerts = as.get("/api/v1/alerts").json();
		assertThat(alerts).singleElement().satisfies(a -> {
			assertThat(a.get("ruleId").asString()).isEqualTo("LARGE_VS_CATEGORY");
			assertThat(a.get("subjectLabel").asString()).isEqualTo("The Leela Palace");
			assertThat(a.get("explanation").get("median").asString()).isEqualTo("400.00");
			assertThat(a.get("message").asString())
				.isEqualTo("₹2,000.00 at The Leela Palace is 5.0× your typical Dining expense of ₹400.00 "
						+ "(median of 5 in the last 90 days).");
		});
	}

	@Test
	void dismissedAlertsStayResolvedAndExpectedOnesDoNotReFire() {
		String id = spend("30000", 2, "Jeweller");
		JsonNode alert = as.get("/api/v1/alerts").json().get(0);
		assertThat(alert.get("ruleId").asString()).isEqualTo("ABSOLUTE_THRESHOLD");

		Response expected = as.patch("/api/v1/alerts/" + alert.get("id").asString(), Map.of("status", "EXPECTED"));
		assertThat(expected.status()).isEqualTo(200);
		assertThat(expected.json().get("status").asString()).isEqualTo("EXPECTED");
		assertThat(as.get("/api/v1/alerts").json()).isEmpty();
		assertThat(as.get("/api/v1/alerts?status=EXPECTED").json()).hasSize(1);

		// Editing the transaction re-runs the rules, but the resolved alert is not raised again.
		as.put("/api/v1/transactions/" + id,
				withVersion(transaction("EXPENSE", "31000", today().minusDays(2), "Meal", "Jeweller", DINING), 0));
		assertThat(as.get("/api/v1/alerts").json()).isEmpty();

		assertThat(as.patch("/api/v1/alerts/" + alert.get("id").asString(), Map.of("status", "OPEN")).status())
			.isEqualTo(422);
	}

	@Test
	void deletingATransactionRemovesItsOpenAlerts() {
		String id = spend("30000", 2, "Jeweller");
		assertThat(as.get("/api/v1/alerts").json()).hasSize(1);
		as.delete("/api/v1/transactions/" + id);
		assertThat(as.get("/api/v1/alerts").json()).isEmpty();
	}

	@Test
	void incomeNeverRaisesAlerts() {
		as.post("/api/v1/transactions", transaction("INCOME", "900000", today(), "Bonus", null, null));
		assertThat(as.get("/api/v1/alerts").json()).isEmpty();
	}

}
