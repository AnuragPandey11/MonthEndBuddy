package dev.monthendbuddy.notifications;

import static dev.monthendbuddy.transactions.TransactionFixtures.DINING;
import static dev.monthendbuddy.transactions.TransactionFixtures.today;
import static dev.monthendbuddy.transactions.TransactionFixtures.transaction;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.IntegrationTest;

/**
 * Budget thresholds, anomalies and imports become notifications that the user can read (P5.5). Runs in both event
 * modes: {@link NotificationsApiIT} and {@link NotificationsApiKafkaIT}.
 */
abstract class NotificationsApiScenario extends IntegrationTest {

	private ApiClient as;

	@BeforeEach
	void newUser() {
		as = api().bearer(api().register().accessToken());
	}

	@Test
	void aCrossedBudgetAndALargeExpenseEachNotifyOnce() {
		as.post("/api/v1/budgets",
				Map.of("categoryId", DINING, "limit", Map.of("amount", "40000", "currency", "INR")));
		as.post("/api/v1/transactions", transaction("EXPENSE", "33000", today(), "Dinner", "Jeweller", DINING));
		awaitEventsHandled();

		JsonNode list = as.get("/api/v1/notifications").json();
		assertThat(types(list)).containsExactlyInAnyOrder("BUDGET_THRESHOLD", "ANOMALY");
		JsonNode budget = find(list, "BUDGET_THRESHOLD");
		assertThat(budget.get("title").asString()).isEqualTo("Budget 80% used");
		assertThat(budget.get("body").asString()).startsWith("You've spent ₹33,000.00 of a ₹40,000.00 budget in ");
		assertThat(budget.get("link").asString()).isEqualTo("/budgets");
		assertThat(budget.get("readAt").isNull()).isTrue();
	}

	@Test
	void readingANotificationTakesItOutOfTheUnreadList() {
		as.post("/api/v1/transactions", transaction("EXPENSE", "30000", today(), "Meal", "Jeweller", DINING));
		awaitEventsHandled();
		String id = as.get("/api/v1/notifications?unreadOnly=true").json().get(0).get("id").asString();

		ApiClient.Response read = as.post("/api/v1/notifications/" + id + "/read", null);
		assertThat(read.status()).isEqualTo(200);
		String readAt = read.json().get("readAt").asString();
		assertThat(as.get("/api/v1/notifications?unreadOnly=true").json()).isEmpty();
		assertThat(as.get("/api/v1/notifications").json()).hasSize(1);
		// Reading again keeps the first time.
		assertThat(as.post("/api/v1/notifications/" + id + "/read", null).json().get("readAt").asString())
			.isEqualTo(readAt);
		assertThat(as.post("/api/v1/notifications/00000000-0000-7000-8000-000000000000/read", null).status())
			.isEqualTo(404);
	}

	@Test
	void aCommittedImportNotifies() throws Exception {
		byte[] sample = Files.readAllBytes(Path.of("..", "sample-data", "synthetic-transactions.csv"));
		String jobId = as.upload("/api/v1/imports", "s.csv", "text/csv", sample).json().get("id").asString();
		as.post("/api/v1/imports/" + jobId + "/commit", null);
		awaitEventsHandled();

		JsonNode imported = find(as.get("/api/v1/notifications").json(), "IMPORT_COMPLETED");
		assertThat(imported.get("title").asString()).isEqualTo("Import complete");
		assertThat(imported.get("body").asString()).matches("\\d+ transactions imported, dated .+ to .+\\.");
		assertThat(imported.get("link").asString()).isEqualTo("/transactions");
	}

	private static List<String> types(JsonNode list) {
		List<String> types = new ArrayList<>();
		list.forEach(n -> types.add(n.get("type").asString()));
		return types;
	}

	private static JsonNode find(JsonNode list, String type) {
		for (JsonNode n : list) {
			if (type.equals(n.get("type").asString())) {
				return n;
			}
		}
		throw new AssertionError("No " + type + " notification in " + list);
	}

}
