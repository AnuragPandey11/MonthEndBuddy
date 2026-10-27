package dev.monthendbuddy.analytics;

import java.math.BigDecimal;
import java.time.YearMonth;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.transactions.TransactionAggregations.DataCoverage;

/**
 * Income, expenses, net cash flow and savings rate for one month (architecture §6.3). {@code savingsRate} is
 * {@code null} with {@code savingsRateReason = "NO_INCOME"} when the month has no income.
 */
public record MonthlySummary(YearMonth month, Money income, Money expenses, Money net, BigDecimal savingsRate,
		String savingsRateReason, DataCoverage coverage) {

}
