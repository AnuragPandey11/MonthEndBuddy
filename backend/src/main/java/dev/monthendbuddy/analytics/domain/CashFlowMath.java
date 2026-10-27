package dev.monthendbuddy.analytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import dev.monthendbuddy.common.Money;

/**
 * Cash-flow formulas (architecture §6.3), framework-free so they are unit-tested directly. Percentages are scale 1,
 * HALF_EVEN, like all money rounding.
 */
public final class CashFlowMath {

	public static final int PERCENT_SCALE = 1;

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private CashFlowMath() {
	}

	/** income − expenses; may be negative. */
	public static Money net(Money income, Money expenses) {
		return income.subtract(expenses);
	}

	/** (income − expenses) ÷ income × 100; empty when there is no income (reason {@code NO_INCOME}). */
	public static Optional<BigDecimal> savingsRate(Money income, Money expenses) {
		if (!income.isPositive()) {
			return Optional.empty();
		}
		return Optional.of(percent(net(income, expenses).amount(), income.amount()));
	}

	/** current − previous and its percentage of previous; a previous value of zero gives no percentage. */
	public static Delta delta(Money current, Money previous) {
		Money delta = current.subtract(previous);
		if (previous.isZero()) {
			return new Delta(delta, null, current.isPositive());
		}
		return new Delta(delta, percent(delta.amount(), previous.amount()), false);
	}

	/** part ÷ total × 100; zero when the total is zero. */
	public static BigDecimal share(Money part, Money total) {
		if (total.isZero()) {
			return BigDecimal.ZERO.setScale(PERCENT_SCALE);
		}
		return percent(part.amount(), total.amount());
	}

	private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
		return numerator.multiply(HUNDRED).divide(denominator, PERCENT_SCALE, RoundingMode.HALF_EVEN);
	}

	/** @param isNew the previous value was zero and the current one is positive */
	public record Delta(Money delta, BigDecimal deltaPct, boolean isNew) {
	}

}
