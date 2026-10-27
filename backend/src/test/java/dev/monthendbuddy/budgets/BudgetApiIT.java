package dev.monthendbuddy.budgets;

import static dev.monthendbuddy.transactions.TransactionFixtures.DINING;
import static dev.monthendbuddy.transactions.TransactionFixtures.GROCERIES;
import static dev.monthendbuddy.transactions.TransactionFixtures.SALARY;
import static dev.monthendbuddy.transactions.TransactionFixtures.today;
import static dev.monthendbuddy.transactions.TransactionFixtures.transaction;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.IntegrationTest;

class BudgetApiIT extends IntegrationTest {

	private ApiClient as;

	private final YearMonth month = YearMonth.from(today()).minusMonths(1);

	@BeforeEach
	void newUser() {
		as = api().bearer(api().register().accessToken());
	}

	private Response createBudget(String categoryId, String limit) {
		Map<String, Object> body = new HashMap<>();
		body.put("categoryId", categoryId);
		body.put("limit", Map.of("amount", limit, "currency", "INR"));
		return as.post("/api/v1/budgets", body);
	}

	private void spend(String amount, int day, String category, String description) {
		as.post("/api/v1/transactions", transaction("EXPENSE", amount, month.atDay(day), description, null, category));
	}

	@Test
	void statusShowsSpentRemainingPercentageAndTheLargestTransactions() {
		assertThat(createBudget(null, "10000").status()).isEqualTo(201);
		assertThat(createBudget(DINING, "6000").status()).isEqualTo(201);
		spend("5000", 3, DINING, "Anniversary dinner");
		spend("3400", 4, DINING, "Team lunch");
		spend("1200", 5, GROCERIES, "Weekly shop");
		as.post("/api/v1/transactions", transaction("INCOME", "95000", month.atDay(1), "Salary", null, SALARY));

		JsonNode report = as.get("/api/v1/budgets/status?month=" + month).json();
		JsonNode overall = report.get("budgets").get(0);
		assertThat(overall.get("categoryName").asString()).isEqualTo("Overall");
		assertThat(overall.get("spent").get("amount").asString()).isEqualTo("9600.00");
		assertThat(overall.get("remaining").get("amount").asString()).isEqualTo("400.00");
		assertThat(overall.get("pctUsed").decimalValue()).isEqualByComparingTo("96.0");
		assertThat(overall.get("thresholdsCrossed")).extracting(JsonNode::asInt).containsExactly(80);

		JsonNode dining = report.get("budgets").get(1);
		assertThat(dining.get("categoryName").asString()).isEqualTo("Dining");
		assertThat(dining.get("spent").get("amount").asString()).isEqualTo("8400.00");
		assertThat(dining.get("remaining").get("amount").asString()).isEqualTo("-2400.00");
		assertThat(dining.get("overspent").asBoolean()).isTrue();
		assertThat(dining.get("pctUsed").decimalValue()).isEqualByComparingTo("140.0");
		assertThat(dining.get("thresholdsCrossed")).extracting(JsonNode::asInt).containsExactly(80, 100);
		assertThat(dining.get("topTransactions").get(0).get("description").asString()).isEqualTo("Anniversary dinner");
		assertThat(dining.get("topTransactions")).hasSize(2);
	}

	@Test
	void oneOverallBudgetAndOnePerCategory() {
		assertThat(createBudget(null, "10000").status()).isEqualTo(201);
		Response second = createBudget(null, "20000");
		assertThat(second.status()).isEqualTo(409);
		assertThat(second.json().get("code").asString()).isEqualTo("budget_exists");
		assertThat(createBudget(DINING, "6000").status()).isEqualTo(201);
		assertThat(createBudget(DINING, "7000").status()).isEqualTo(409);
		assertThat(createBudget(GROCERIES, "7000").status()).isEqualTo(201);
	}

	@Test
	void budgetsAreForUsableExpenseCategoriesOnly() {
		assertThat(createBudget(SALARY, "1000").status()).isEqualTo(422);
		String theirs = api().bearer(api().register().accessToken())
			.post("/api/v1/categories", Map.of("name", "Theirs", "kind", "EXPENSE"))
			.json()
			.get("id")
			.asString();
		assertThat(createBudget(theirs, "1000").status()).isEqualTo(422);
		assertThat(createBudget(DINING, "0").status()).isEqualTo(422);
	}

	@Test
	void budgetsCanBeUpdatedWithVersionChecksAndDeleted() {
		JsonNode created = createBudget(DINING, "6000").json();
		String id = created.get("id").asString();
		assertThat(created.get("alertThresholds")).extracting(JsonNode::asInt).containsExactly(80, 100);

		Response updated = as.put("/api/v1/budgets/" + id, Map.of("limit", Map.of("amount", "7500", "currency", "INR"),
				"alertThresholds", List.of(100, 50, 50), "version", 0));
		assertThat(updated.status()).isEqualTo(200);
		assertThat(updated.json().get("limit").get("amount").asString()).isEqualTo("7500.00");
		assertThat(updated.json().get("alertThresholds")).extracting(JsonNode::asInt).containsExactly(50, 100);
		assertThat(as.put("/api/v1/budgets/" + id, Map.of("limit", Map.of("amount", "1", "currency", "INR"), "version",
				0)).status()).isEqualTo(409);
		assertThat(as.put("/api/v1/budgets/" + id, Map.of("limit", Map.of("amount", "1", "currency", "INR"),
				"alertThresholds", List.of(250), "version", 1)).status()).isEqualTo(422);

		assertThat(as.delete("/api/v1/budgets/" + id).status()).isEqualTo(204);
		assertThat(as.get("/api/v1/budgets").json()).isEmpty();
	}

}
