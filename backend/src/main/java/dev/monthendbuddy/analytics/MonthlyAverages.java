package dev.monthendbuddy.analytics;

import dev.monthendbuddy.common.Money;

/**
 * Average monthly income and expenses over the last complete months that have data (months without any transactions
 * are not counted as zero). {@code lowData} is true when fewer months than requested were available; the averages
 * are {@code null} when none were.
 */
public record MonthlyAverages(Money income, Money expenses, int monthsUsed, int monthsRequested, boolean lowData) {

}
