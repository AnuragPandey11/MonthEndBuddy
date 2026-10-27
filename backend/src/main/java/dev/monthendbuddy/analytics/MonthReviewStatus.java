package dev.monthendbuddy.analytics;

/**
 * Where last month's review stands. {@code DUE}: the month kept money and has not been reviewed. {@code NOTHING_KEPT}:
 * it had no transactions or did not end in surplus, so there is nothing to offer. {@code DONE} and {@code SKIPPED}
 * are recorded once and never change.
 */
public enum MonthReviewStatus {

	DUE, NOTHING_KEPT, DONE, SKIPPED

}
