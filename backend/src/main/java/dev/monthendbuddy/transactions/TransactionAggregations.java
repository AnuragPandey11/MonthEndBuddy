package dev.monthendbuddy.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import dev.monthendbuddy.common.Period;
import dev.monthendbuddy.common.UserId;

/**
 * Every aggregate over transactions (LLD §4.1), each one SQL statement scoped to {@code user}. Amounts are plain
 * decimals in the user's base currency (v1 has one currency per user); callers wrap them in {@code Money}.
 */
public interface TransactionAggregations {

	TypeTotals totalsByType(UserId user, Period period);

	/** Largest first. Uncategorised rows are one entry with {@code categoryId == null}. */
	List<CategoryTotal> totalsByCategory(UserId user, Period period, TransactionType type);

	/** Only months that have transactions; callers fill gaps when they need a continuous series. */
	List<MonthTotal> monthlyTotals(UserId user, Period period);

	/** Individual expenses in one category, oldest first (anomaly baselines). */
	List<DatedAmount> categoryExpenses(UserId user, UUID categoryId, Period period);

	DataCoverage coverage(UserId user, Period period);

	record TypeTotals(BigDecimal income, long incomeCount, BigDecimal expenses, long expenseCount) {
	}

	record CategoryTotal(UUID categoryId, String categoryName, BigDecimal amount, long count) {
	}

	record MonthTotal(YearMonth month, BigDecimal income, BigDecimal expenses, long count) {
	}

	/** {@code label} is the merchant, or the description when there is no merchant. */
	record DatedAmount(UUID transactionId, LocalDate date, BigDecimal amount, String label) {
	}

	/**
	 * What data exists in a period, so "no transactions recorded" is never reported as "you spent nothing"
	 * (architecture §6.3).
	 */
	record DataCoverage(long transactionCount, LocalDate firstDate, LocalDate lastDate, List<YearMonth> monthsWithData) {

		public boolean isEmpty() {
			return transactionCount == 0;
		}

	}

}
