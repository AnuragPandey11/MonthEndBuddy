package dev.monthendbuddy.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.transactions.TransactionAggregations.DataCoverage;

/** Expenses per category in a period, largest first; {@code pct} is each category's share of {@code total}. */
public record CategoryBreakdown(LocalDate from, LocalDate to, Money total, List<CategoryShare> categories,
		DataCoverage coverage) {

	/** {@code categoryId} is {@code null} for uncategorised spending. */
	public record CategoryShare(UUID categoryId, String name, Money amount, BigDecimal pct, long count) {
	}

}
