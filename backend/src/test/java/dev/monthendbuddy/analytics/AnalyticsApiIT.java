package dev.monthendbuddy.analytics;

import static dev.monthendbuddy.transactions.TransactionFixtures.DINING;
import static dev.monthendbuddy.transactions.TransactionFixtures.GROCERIES;
import static dev.monthendbuddy.transactions.TransactionFixtures.SALARY;
import static dev.monthendbuddy.transactions.TransactionFixtures.transaction;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.IntegrationTest;
import dev.monthendbuddy.transactions.TransactionFixtures;

/** Dashboard endpoints against fixtures; every figure is compared as the exact API string (P2.2). */
class AnalyticsApiIT extends IntegrationTest {

	private final YearMonth current = YearMonth.from(TransactionFixtures.today());

	private final YearMonth previous = current.minusMonths(1);

	private final YearMonth twoBack = current.minusMonths(2);

	private ApiClient as;

	@BeforeEach
	void fixtures() {
		as = api().bearer(api().register().accessToken());
		// Two months back: income and spending.
		post("INCOME", "95000.00", twoBack.atDay(1), SALARY);
		post("EXPENSE", "64300.00", twoBack.atDay(5), GROCERIES);
		// Last month: no income at all, dining is new.
		post("EXPENSE", "1250.50", previous.atDay(3), DINING);
		post("EXPENSE", "749.50", previous.atDay(4), DINING);
		post("EXPENSE", "3000.00", previous.atDay(9), GROCERIES);
		post("EXPENSE", "99.99", previous.atDay(10), null);
	}

	private void post(String type, String amount, LocalDate date, String category) {
		assertThat(as.post("/api/v1/transactions", transaction(type, amount, date, "Fixture", null, category)).status())
			.isEqualTo(201);
	}

	@Test
	void monthlySummaryWithIncomeHasASavingsRate() {
		JsonNode summary = as.get("/api/v1/analytics/summary?month=" + twoBack).json();
		assertThat(summary.get("month").asString()).isEqualTo(twoBack.toString());
		assertThat(summary.get("income").get("amount").asString()).isEqualTo("95000.00");
		assertThat(summary.get("expenses").get("amount").asString()).isEqualTo("64300.00");
		assertThat(summary.get("net").get("amount").asString()).isEqualTo("30700.00");
		assertThat(summary.get("savingsRate").decimalValue()).isEqualByComparingTo("32.3");
		assertThat(summary.get("coverage").get("transactionCount").asInt()).isEqualTo(2);
	}

	@Test
	void monthWithoutIncomeHasNoSavingsRateAndEmptyMonthsSayNoData() {
		JsonNode summary = as.get("/api/v1/analytics/summary?month=" + previous).json();
		assertThat(summary.get("savingsRate").isNull()).isTrue();
		assertThat(summary.get("savingsRateReason").asString()).isEqualTo("NO_INCOME");
		assertThat(summary.get("net").get("amount").asString()).isEqualTo("-5099.99");

		JsonNode empty = as.get("/api/v1/analytics/summary?month=" + current.minusMonths(8)).json();
		assertThat(empty.get("expenses").get("amount").asString()).isEqualTo("0.00");
		assertThat(empty.get("coverage").get("transactionCount").asInt()).isZero();
	}

	@Test
	void futureMonthIsRejected() {
		Response response = as.get("/api/v1/analytics/summary?month=" + current.plusMonths(1));
		assertThat(response.status()).isEqualTo(422);
		assertThat(response.json().get("errors").get(0).get("field").asString()).isEqualTo("period");
	}

	@Test
	void categoryBreakdownHasSharesLargestFirst() {
		JsonNode breakdown = as.get("/api/v1/analytics/categories?from=" + previous.atDay(1) + "&to="
				+ previous.atEndOfMonth()).json();
		assertThat(breakdown.get("total").get("amount").asString()).isEqualTo("5099.99");
		List<String> names = new ArrayList<>();
		breakdown.get("categories").forEach(c -> names.add(c.get("name").asString()));
		assertThat(names).containsExactly("Groceries", "Dining", "Uncategorised");
		JsonNode dining = breakdown.get("categories").get(1);
		assertThat(dining.get("amount").get("amount").asString()).isEqualTo("2000.00");
		assertThat(dining.get("pct").decimalValue()).isEqualByComparingTo("39.2");
		assertThat(dining.get("count").asInt()).isEqualTo(2);
		assertThat(breakdown.get("categories").get(2).get("categoryId").isNull()).isTrue();
		assertThat(as.get("/api/v1/analytics/categories?from=" + previous.atDay(1)).status()).isEqualTo(422);
	}

	@Test
	void trendsHaveOnePointPerMonthAndFlagMonthsWithoutData() {
		JsonNode points = as.get("/api/v1/analytics/trends?months=4").json().get("points");
		assertThat(points).hasSize(4);
		assertThat(points.get(0).get("month").asString()).isEqualTo(current.minusMonths(3).toString());
		assertThat(points.get(0).get("hasData").asBoolean()).isFalse();
		assertThat(points.get(0).get("expenses").get("amount").asString()).isEqualTo("0.00");
		assertThat(points.get(1).get("income").get("amount").asString()).isEqualTo("95000.00");
		assertThat(points.get(3).get("month").asString()).isEqualTo(current.toString());
		assertThat(as.get("/api/v1/analytics/trends?months=25").status()).isEqualTo(422);
	}

	@Test
	void comparisonFlagsNewCategoriesAndSortsByIncrease() {
		JsonNode comparison = as.get("/api/v1/analytics/compare?current=" + previous + "&previous=" + twoBack).json();
		assertThat(comparison.get("totals").get("current").get("amount").asString()).isEqualTo("5099.99");
		assertThat(comparison.get("totals").get("delta").get("amount").asString()).isEqualTo("-59200.01");
		assertThat(comparison.get("totals").get("deltaPct").decimalValue()).isEqualByComparingTo("-92.1");
		JsonNode first = comparison.get("categories").get(0);
		assertThat(first.get("name").asString()).isEqualTo("Dining");
		assertThat(first.get("isNew").asBoolean()).isTrue();
		assertThat(first.get("deltaPct").isNull()).isTrue();
		JsonNode last = comparison.get("categories").get(comparison.get("categories").size() - 1);
		assertThat(last.get("name").asString()).isEqualTo("Groceries");
		assertThat(last.get("delta").get("amount").asString()).isEqualTo("-61300.00");
	}

	@Test
	void anotherUsersAnalyticsAreEmpty() {
		ApiClient stranger = api().bearer(api().register().accessToken());
		JsonNode summary = stranger.get("/api/v1/analytics/summary?month=" + twoBack).json();
		assertThat(summary.get("income").get("amount").asString()).isEqualTo("0.00");
		assertThat(summary.get("coverage").get("transactionCount").asInt()).isZero();
	}

}
