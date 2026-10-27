package dev.monthendbuddy.identity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;

/**
 * Read-only user settings for other modules (architecture §4.1). {@code currentSavings} is user-stated and may be
 * {@code null} or stale; show {@code currentSavingsUpdatedAt} alongside it.
 */
public record UserSettingsView(UserId userId, CurrencyCode baseCurrency, ZoneId timezone, Money currentSavings,
		Instant currentSavingsUpdatedAt, int emergencyBufferMonths, Money largeTxnThreshold,
		BigDecimal anomalyMultiplier, int categorySpikePct) {

	/** "Today" in the user's timezone (architecture §6.2). */
	public LocalDate today(Clock clock) {
		return LocalDate.now(clock.withZone(timezone));
	}

	public YearMonth currentMonth(Clock clock) {
		return YearMonth.from(today(clock));
	}

}
