package dev.monthendbuddy.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.IntegrationTest;

/**
 * The anomalies planted in the synthetic data (P1.5) produce exactly the expected alerts, and everyday spending
 * produces none (implementation plan P2.8). Uses the committed sample statement, whose dates are fixed. Runs in both
 * event modes (P5.4): {@link PlantedAnomaliesIT} and {@link PlantedAnomaliesKafkaIT}.
 */
abstract class PlantedAnomaliesScenario extends IntegrationTest {

	@Test
	void importingTheSampleStatementRaisesExactlyThePlantedAlerts() throws Exception {
		ApiClient as = api().bearer(api().register().accessToken());
		byte[] sample = Files.readAllBytes(Path.of("..", "sample-data", "synthetic-transactions.csv"));
		String jobId = as.upload("/api/v1/imports", "s.csv", "text/csv", sample).json().get("id").asString();
		as.post("/api/v1/imports/" + jobId + "/commit", null);
		awaitEventsHandled();

		List<String> alerts = new ArrayList<>();
		for (JsonNode a : as.get("/api/v1/alerts").json()) {
			alerts.add(a.get("ruleId").asString() + " " + a.get("periodStart").asString() + " "
					+ a.get("subjectLabel").asString());
		}
		assertThat(alerts).containsExactlyInAnyOrder(
				// Planted: a weekend trip in a category with no history.
				"CATEGORY_SPIKE 2026-10-01 Travel",
				// Planted: an anniversary dinner far above the usual dining bill, which also lifts Dining that month.
				"LARGE_VS_CATEGORY 2026-09-01 The Leela Palace", "CATEGORY_SPIKE 2026-09-01 Dining",
				// Planted: a ₹62,000 laptop. The specific "20.9× your typical Shopping" replaces the plain threshold
				// alert for the same transaction.
				"LARGE_VS_CATEGORY 2026-08-01 Croma", "CATEGORY_SPIKE 2026-08-01 Shopping",
				// The first rent in the data has no history yet to recognise it as routine; later months do not alert.
				"ABSOLUTE_THRESHOLD 2026-05-01 Landlord (UPI)");
	}

}
