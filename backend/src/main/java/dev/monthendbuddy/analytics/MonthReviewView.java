package dev.monthendbuddy.analytics;

import java.time.Instant;
import java.time.YearMonth;

import dev.monthendbuddy.common.Money;

/**
 * Last month's review (architecture §9.6). {@code kept} is the month's net cash flow from the API's own summary, so
 * the browser never computes it; {@code transactionCount} says whether the month had data at all. {@code
 * currentSavings} is Savings today, or {@code null} when unknown, in which case nothing can be added to it.
 * {@code savingsUpdatedSinceMonthEnd} is true when the user changed Savings today after the month ended (in their
 * timezone), so it may already include what the month kept and adding it again would count it twice.
 * {@code addedToSavings} and {@code setAsideForGoals} are what a finished review did.
 */
public record MonthReviewView(YearMonth month, Money kept, long transactionCount, MonthReviewStatus status,
		Money currentSavings, Instant currentSavingsUpdatedAt, boolean savingsUpdatedSinceMonthEnd,
		Money addedToSavings, Money setAsideForGoals) {
}
