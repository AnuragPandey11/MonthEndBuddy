package dev.monthendbuddy.analytics;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.transactions.TransactionAggregations.DataCoverage;

/**
 * Month-over-month expenses, in total and per category (architecture §6.3). {@code deltaPct} is {@code null} and
 * {@code isNew} true when the previous month had nothing in that category. Categories are sorted by increase, largest
 * first.
 */
public record PeriodComparison(YearMonth current, YearMonth previous, Change totals, List<CategoryChange> categories,
		DataCoverage currentCoverage, DataCoverage previousCoverage) {

	public record Change(Money current, Money previous, Money delta, BigDecimal deltaPct, boolean isNew) {
	}

	public record CategoryChange(UUID categoryId, String name, Money current, Money previous, Money delta,
			BigDecimal deltaPct, boolean isNew) {
	}

}
