package dev.monthendbuddy.analytics;

import java.time.YearMonth;
import java.util.List;

import dev.monthendbuddy.common.Money;

/**
 * Monthly income and expenses, one point per calendar month, oldest first. A month without any transactions has zero
 * totals and {@code hasData = false}, so a chart can show "no data" rather than "spent nothing".
 */
public record TrendSeries(List<TrendPoint> points) {

	public record TrendPoint(YearMonth month, Money income, Money expenses, Money net, boolean hasData) {
	}

}
