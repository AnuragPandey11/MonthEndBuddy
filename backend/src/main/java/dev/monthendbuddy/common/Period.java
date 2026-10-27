package dev.monthendbuddy.common;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.monthendbuddy.common.error.ValidationException;

/**
 * An inclusive date range of at most {@value #MAX_MONTHS} calendar months (architecture §6.2). Dates have no time
 * of day; which month is "this month" is decided by the user's timezone before a period is built.
 */
public record Period(LocalDate startInclusive, LocalDate endInclusive) {

	public static final int MAX_MONTHS = 24;

	public Period {
		Objects.requireNonNull(startInclusive, "startInclusive");
		Objects.requireNonNull(endInclusive, "endInclusive");
		if (startInclusive.isAfter(endInclusive)) {
			throw ValidationException.of("period", "start must not be after end");
		}
		long months = ChronoUnit.MONTHS.between(YearMonth.from(startInclusive), YearMonth.from(endInclusive)) + 1;
		if (months > MAX_MONTHS) {
			throw ValidationException.of("period", "must not span more than " + MAX_MONTHS + " months");
		}
	}

	public static Period ofMonth(YearMonth month) {
		return new Period(month.atDay(1), month.atEndOfMonth());
	}

	/** The {@code n} calendar months before the month containing {@code today}; the current month is excluded. */
	public static Period lastCompleteMonths(int n, LocalDate today) {
		if (n < 1 || n > MAX_MONTHS) {
			throw ValidationException.of("months", "must be between 1 and " + MAX_MONTHS);
		}
		YearMonth current = YearMonth.from(today);
		return new Period(current.minusMonths(n).atDay(1), current.minusMonths(1).atEndOfMonth());
	}

	public boolean contains(LocalDate date) {
		return !date.isBefore(startInclusive) && !date.isAfter(endInclusive);
	}

	/** Every calendar month the period touches, in order. */
	public List<YearMonth> months() {
		List<YearMonth> months = new ArrayList<>();
		for (YearMonth m = YearMonth.from(startInclusive); !m.isAfter(YearMonth.from(endInclusive)); m = m.plusMonths(1)) {
			months.add(m);
		}
		return months;
	}

	public int lengthInMonths() {
		return months().size();
	}

	/** A period that starts after {@code today} is rejected with 422 (architecture §6.2). */
	public Period requireNotInFuture(LocalDate today) {
		if (startInclusive.isAfter(today)) {
			throw ValidationException.of("period", "must not start in the future");
		}
		return this;
	}

}
