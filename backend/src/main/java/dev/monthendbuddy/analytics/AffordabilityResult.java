package dev.monthendbuddy.analytics;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import dev.monthendbuddy.common.Assumption;
import dev.monthendbuddy.common.Money;

/**
 * A purchase simulation with every input and its source (architecture §9.4), so the copilot cannot present an
 * estimate as a fact. Figures that cannot be computed are {@code null}; {@code dataQuality} says why.
 * {@code currentSavings} is the total, including {@code savingsSetAside} (what active goals already hold);
 * {@code availableSavings} is the rest, and {@code savingsAfterPurchase} is what remains of it after the purchase.
 * {@code recoveryMonths} is how long the monthly surplus left after goals takes to earn the purchase back.
 */
public record AffordabilityResult(AffordabilityVerdict verdict, Money purchaseAmount, YearMonth purchaseMonth,
		Money currentSavings, Money savingsSetAside, Money availableSavings, Money savingsAfterPurchase, Money averageMonthlyIncome, Money averageMonthlyExpenses,
		Money monthlySurplus, Money emergencyBuffer, Money goalNeed, BigDecimal recoveryMonths,
		List<GoalImpact> goalImpacts, List<Assumption> assumptions, List<DataQualityFlag> dataQuality,
		int monthsOfData) {

	/** {@code delayMonths} is {@code null} when the surplus can never recover the purchase. */
	public record GoalImpact(UUID goalId, String name, Money requiredMonthly, Integer delayMonths) {
	}

	/**
	 * {@code MISSING_INPUT}, {@code LOW_DATA}, {@code DEADLINE_PASSED} or {@code GOALS_EXCEED_SAVINGS}, with a sentence
	 * for the UI.
	 */
	public record DataQualityFlag(String code, String message) {
	}

}
