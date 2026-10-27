package dev.monthendbuddy.analytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import dev.monthendbuddy.analytics.AffordabilityVerdict;
import dev.monthendbuddy.common.Money;

/**
 * The verdict logic of architecture §9.4, framework-free:
 *
 * <pre>
 * available      = currentSavings − setAside            (money already saved toward active goals is not free)
 * savingsAfter   = available − purchase
 * surplus        = avgIncome − avgExpenses
 * buffer         = emergencyBufferMonths × avgExpenses
 * recoveryMonths = purchase ÷ (surplus − goalNeed)     when that is positive
 *
 * INSUFFICIENT_DATA          savings unknown, or no complete month of data
 * NOT_AFFORDABLE             savingsAfter &lt; 0
 * AFFORDABLE                 savingsAfter ≥ buffer and surplus ≥ goalNeed
 * AFFORDABLE_WITH_TRADEOFFS  otherwise
 * </pre>
 */
public final class AffordabilityCalculator {

	private AffordabilityCalculator() {
	}

	/** {@code currentSavings} is the user's total, goal money included; {@code setAside} is that goal money. */
	public record Inputs(Money purchase, Money currentSavings, Money setAside, Money avgIncome, Money avgExpenses,
			int monthsOfData, int emergencyBufferMonths, Money goalNeed) {
	}

	/** {@code available} may be negative when goals hold more than the stated savings. */
	public record Outcome(AffordabilityVerdict verdict, Money available, Money savingsAfter, Money surplus,
			Money buffer, BigDecimal recoveryMonths) {
	}

	public static Outcome evaluate(Inputs in) {
		Money surplus = in.avgIncome() == null ? null : in.avgIncome().subtract(in.avgExpenses());
		Money buffer = in.avgExpenses() == null ? null
				: in.avgExpenses().multiply(BigDecimal.valueOf(in.emergencyBufferMonths()));
		Money available = in.currentSavings() == null ? null : in.currentSavings().subtract(in.setAside());
		Money savingsAfter = available == null ? null : available.subtract(in.purchase());
		BigDecimal recovery = null;
		if (surplus != null) {
			Money free = surplus.subtract(in.goalNeed());
			if (free.isPositive()) {
				recovery = in.purchase().amount().divide(free.amount(), 1, RoundingMode.CEILING);
			}
		}

		AffordabilityVerdict verdict;
		if (savingsAfter == null || in.monthsOfData() < 1 || surplus == null) {
			verdict = AffordabilityVerdict.INSUFFICIENT_DATA;
		}
		else if (savingsAfter.isNegative()) {
			verdict = AffordabilityVerdict.NOT_AFFORDABLE;
		}
		else if (savingsAfter.isGreaterThanOrEqual(buffer) && surplus.isGreaterThanOrEqual(in.goalNeed())) {
			verdict = AffordabilityVerdict.AFFORDABLE;
		}
		else {
			verdict = AffordabilityVerdict.AFFORDABLE_WITH_TRADEOFFS;
		}
		return new Outcome(verdict, available, savingsAfter, surplus, buffer, recovery);
	}

	/** Months until the surplus has earned back the purchase, if goal contributions pause meanwhile. */
	public static Integer goalDelayMonths(Money purchase, Money surplus) {
		if (surplus == null || !surplus.isPositive()) {
			return null;
		}
		return purchase.amount().divide(surplus.amount(), 0, RoundingMode.CEILING).intValueExact();
	}

}
