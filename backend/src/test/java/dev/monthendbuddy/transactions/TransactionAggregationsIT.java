package dev.monthendbuddy.transactions;

import static dev.monthendbuddy.transactions.TransactionFixtures.DINING;
import static dev.monthendbuddy.transactions.TransactionFixtures.GROCERIES;
import static dev.monthendbuddy.transactions.TransactionFixtures.SALARY;
import static dev.monthendbuddy.transactions.TransactionFixtures.transaction;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.monthendbuddy.common.Period;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.IntegrationTest;
import dev.monthendbuddy.transactions.TransactionAggregations.CategoryTotal;

/** Aggregates against hand-computed fixtures (implementation plan P2.1). */
class TransactionAggregationsIT extends IntegrationTest {

	@Autowired
	TransactionAggregations aggregations;

	/** Two complete months in the past, so no fixture date is in the future. */
	private final YearMonth month = YearMonth.from(TransactionFixtures.today()).minusMonths(2);

	private UserId user;

	@BeforeEach
	void fixtures() {
		String token = api().register().accessToken();
		ApiClient as = api().bearer(token);
		user = UserId.of(as.get("/api/v1/me").json().get("id").asString());
		post(as, "INCOME", "95000.00", month.atDay(1), "Salary", SALARY);
		post(as, "EXPENSE", "1250.50", month.atDay(1), "Dinner", DINING);
		post(as, "EXPENSE", "749.50", month.atEndOfMonth(), "Lunch", DINING);
		post(as, "EXPENSE", "3200.00", month.atDay(15), "Weekly shop", GROCERIES);
		post(as, "EXPENSE", "99.99", month.atDay(20), "Uncategorised thing", null);
		// Just outside the month on both sides.
		post(as, "EXPENSE", "5000.00", month.minusMonths(1).atEndOfMonth(), "Previous month", DINING);
		post(as, "EXPENSE", "7000.00", month.plusMonths(1).atDay(1), "Next month", DINING);
		// Another user's data must never be counted.
		ApiClient other = api().bearer(api().register().accessToken());
		post(other, "EXPENSE", "123456.00", month.atDay(10), "Not yours", DINING);
	}

	private static void post(ApiClient as, String type, String amount, LocalDate date, String description,
			String category) {
		assertThat(as.post("/api/v1/transactions", transaction(type, amount, date, description, null, category))
			.status()).isEqualTo(201);
	}

	@Test
	void totalsByTypeRespectMonthBoundaries() {
		var totals = aggregations.totalsByType(user, Period.ofMonth(month));
		assertThat(totals.income()).isEqualByComparingTo("95000.00");
		assertThat(totals.incomeCount()).isEqualTo(1);
		assertThat(totals.expenses()).isEqualByComparingTo("5299.99");
		assertThat(totals.expenseCount()).isEqualTo(4);
	}

	@Test
	void emptyPeriodIsZeroWithZeroCountAndEmptyCoverage() {
		Period empty = Period.ofMonth(month.minusMonths(6));
		var totals = aggregations.totalsByType(user, empty);
		assertThat(totals.income()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(totals.expenses()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(totals.expenseCount()).isZero();
		var coverage = aggregations.coverage(user, empty);
		assertThat(coverage.isEmpty()).isTrue();
		assertThat(coverage.firstDate()).isNull();
		assertThat(coverage.monthsWithData()).isEmpty();
	}

	@Test
	void categoryTotalsAreLargestFirstWithUncategorisedAsItsOwnEntry() {
		var categories = aggregations.totalsByCategory(user, Period.ofMonth(month), TransactionType.EXPENSE);
		assertThat(categories).extracting(CategoryTotal::categoryName).containsExactly("Groceries", "Dining", null);
		assertThat(categories.get(1).amount()).isEqualByComparingTo("2000.00");
		assertThat(categories.get(1).count()).isEqualTo(2);
		assertThat(categories.get(2).categoryId()).isNull();
		assertThat(aggregations.totalsByCategory(user, Period.ofMonth(month), TransactionType.INCOME))
			.singleElement()
			.satisfies(c -> assertThat(c.categoryName()).isEqualTo("Salary"));
	}

	@Test
	void monthlyTotalsListOnlyMonthsWithData() {
		Period threeMonths = new Period(month.minusMonths(1).atDay(1), month.plusMonths(1).atEndOfMonth());
		var months = aggregations.monthlyTotals(user, threeMonths);
		assertThat(months).extracting(TransactionAggregations.MonthTotal::month)
			.containsExactly(month.minusMonths(1), month, month.plusMonths(1));
		assertThat(months.get(1).income()).isEqualByComparingTo("95000.00");
		assertThat(months.get(1).expenses()).isEqualByComparingTo("5299.99");
		assertThat(months.get(0).expenses()).isEqualByComparingTo("5000.00");
		assertThat(aggregations.coverage(user, threeMonths).monthsWithData())
			.containsExactly(month.minusMonths(1), month, month.plusMonths(1));
	}

	@Test
	void categoryExpensesAreIndividualAmountsOldestFirst() {
		var dining = aggregations.categoryExpenses(user, UUID.fromString(DINING),
				new Period(month.minusMonths(1).atDay(1), month.plusMonths(1).atEndOfMonth()));
		assertThat(dining).extracting(d -> d.amount().toPlainString())
			.containsExactly("5000.0000", "1250.5000", "749.5000", "7000.0000");
	}

}
