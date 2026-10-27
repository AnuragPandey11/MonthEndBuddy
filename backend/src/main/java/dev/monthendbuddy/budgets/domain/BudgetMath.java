package dev.monthendbuddy.budgets.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import dev.monthendbuddy.common.Money;

/** Budget formulas (architecture §6.3). Percentages are scale 1, HALF_EVEN, and may exceed 100. */
public final class BudgetMath {

	private BudgetMath() {
	}

	/** limit − spent; negative means overspent. */
	public static Money remaining(Money limit, Money spent) {
		return limit.subtract(spent);
	}

	public static BigDecimal pctUsed(Money limit, Money spent) {
		return spent.amount().multiply(BigDecimal.valueOf(100)).divide(limit.amount(), 1, RoundingMode.HALF_EVEN);
	}

	/** The thresholds (percent) that {@code pctUsed} has reached, smallest first. */
	public static List<Integer> crossedThresholds(List<Integer> thresholds, BigDecimal pctUsed) {
		return thresholds.stream().filter(t -> pctUsed.compareTo(BigDecimal.valueOf(t)) >= 0).sorted().toList();
	}

}
