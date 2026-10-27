package dev.monthendbuddy.goals.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.monthendbuddy.common.Money;

/**
 * Savings formulas (architecture §6.3), assuming a 0% return and one contribution at the start of each month from
 * next month on.
 */
public final class SavingsMath {

	private SavingsMath() {
	}

	/** Whole calendar months from the first day of next month to the target month, inclusive (October → December = 2). */
	public static int monthsRemaining(YearMonth current, LocalDate targetDate) {
		return (int) ChronoUnit.MONTHS.between(current, YearMonth.from(targetDate));
	}

	/** The remaining amount spread over {@code months}, rounded up to the paisa so the target is never missed. */
	public static Money requiredMonthly(Money remaining, int months) {
		if (months <= 0) {
			throw new IllegalArgumentException("months must be positive");
		}
		return new Money(remaining.amount().divide(BigDecimal.valueOf(months), 2, RoundingMode.CEILING),
				remaining.currency());
	}

	/** First month in which current + n × monthly ≥ target; empty when the contribution is zero. */
	public static Optional<YearMonth> completionMonth(Money current, Money target, Money monthly, YearMonth from) {
		if (current.isGreaterThanOrEqual(target)) {
			return Optional.of(from);
		}
		if (!monthly.isPositive()) {
			return Optional.empty();
		}
		long n = target.subtract(current).amount().divide(monthly.amount(), 0, RoundingMode.CEILING).longValueExact();
		return Optional.of(from.plusMonths(n));
	}

	/** Balance at the start of each month for {@code months} months, starting with this month (no contribution yet). */
	public static List<ProjectionPoint> series(Money current, Money monthly, YearMonth from, int months) {
		List<ProjectionPoint> points = new ArrayList<>(months + 1);
		for (int i = 0; i <= months; i++) {
			points.add(new ProjectionPoint(from.plusMonths(i), current.add(monthly.multiply(BigDecimal.valueOf(i)))));
		}
		return points;
	}

	public record ProjectionPoint(YearMonth month, Money balance) {
	}

}
