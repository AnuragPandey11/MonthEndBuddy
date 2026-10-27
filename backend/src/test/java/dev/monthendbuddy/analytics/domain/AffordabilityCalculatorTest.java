package dev.monthendbuddy.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.analytics.AffordabilityVerdict;
import dev.monthendbuddy.analytics.domain.AffordabilityCalculator.Inputs;
import dev.monthendbuddy.analytics.domain.AffordabilityCalculator.Outcome;
import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;

/** Every verdict branch of architecture §9.4. Income 95,000 and expenses 65,000 a month give a 30,000 surplus. */
class AffordabilityCalculatorTest {

	private static Money inr(String amount) {
		return Money.of(amount, CurrencyCode.INR);
	}

	private static Outcome evaluate(String purchase, String savings, String goalNeed, int months) {
		return evaluate(purchase, savings, "0", goalNeed, months);
	}

	private static Outcome evaluate(String purchase, String savings, String setAside, String goalNeed, int months) {
		return AffordabilityCalculator.evaluate(new Inputs(inr(purchase), savings == null ? null : inr(savings),
				inr(setAside), inr("95000"), inr("65000"), months, 3, inr(goalNeed)));
	}

	@Test
	void moneyAlreadySavedTowardGoalsIsNotFreeToSpend() {
		// ₹80,000 in total, ₹20,000 of it saved toward a goal: ₹60,000 is free, and a ₹60,000 purchase empties it.
		Outcome outcome = evaluate("60000", "80000", "20000", "0", 3);
		assertThat(outcome.available()).isEqualTo(inr("60000"));
		assertThat(outcome.savingsAfter()).isEqualTo(inr("0"));
		assertThat(outcome.verdict()).isEqualTo(AffordabilityVerdict.AFFORDABLE_WITH_TRADEOFFS);

		assertThat(evaluate("60000", "300000", "250000", "0", 3).verdict())
			.as("would be affordable if the goal money were free")
			.isEqualTo(AffordabilityVerdict.NOT_AFFORDABLE);
	}

	@Test
	void goalsHoldingMoreThanTheSavingsLeaveNothingAvailable() {
		Outcome outcome = evaluate("1000", "50000", "80000", "0", 3);
		assertThat(outcome.available()).isEqualTo(inr("-30000"));
		assertThat(outcome.verdict()).isEqualTo(AffordabilityVerdict.NOT_AFFORDABLE);
	}

	@Test
	void affordableWhenSavingsStayAboveTheBufferAndGoalsAreCovered() {
		Outcome outcome = evaluate("60000", "300000", "10000", 3);
		assertThat(outcome.verdict()).isEqualTo(AffordabilityVerdict.AFFORDABLE);
		assertThat(outcome.savingsAfter()).isEqualTo(inr("240000"));
		assertThat(outcome.buffer()).isEqualTo(inr("195000"));
		assertThat(outcome.surplus()).isEqualTo(inr("30000"));
		assertThat(outcome.recoveryMonths()).isEqualByComparingTo("3.0");
	}

	@Test
	void tradeoffsWhenSavingsDropBelowTheBuffer() {
		assertThat(evaluate("60000", "200000", "0", 3).verdict())
			.isEqualTo(AffordabilityVerdict.AFFORDABLE_WITH_TRADEOFFS);
	}

	@Test
	void tradeoffsWhenTheSurplusCannotCoverGoals() {
		Outcome outcome = evaluate("60000", "500000", "40000", 3);
		assertThat(outcome.verdict()).isEqualTo(AffordabilityVerdict.AFFORDABLE_WITH_TRADEOFFS);
		assertThat(outcome.recoveryMonths()).isNull();
	}

	@Test
	void notAffordableWhenSavingsWouldGoNegative() {
		assertThat(evaluate("60000", "59999.99", "0", 3).verdict()).isEqualTo(AffordabilityVerdict.NOT_AFFORDABLE);
		assertThat(evaluate("60000", "60000", "0", 3).verdict())
			.isEqualTo(AffordabilityVerdict.AFFORDABLE_WITH_TRADEOFFS);
	}

	@Test
	void insufficientDataWithoutSavingsOrWithoutAnyCompleteMonth() {
		assertThat(evaluate("60000", null, "0", 3).verdict()).isEqualTo(AffordabilityVerdict.INSUFFICIENT_DATA);
		assertThat(evaluate("60000", "300000", "0", 0).verdict()).isEqualTo(AffordabilityVerdict.INSUFFICIENT_DATA);
	}

	@Test
	void goalDelayIsTheMonthsTheSurplusNeedsToEarnThePurchaseBack() {
		assertThat(AffordabilityCalculator.goalDelayMonths(inr("60000"), inr("30000"))).isEqualTo(2);
		assertThat(AffordabilityCalculator.goalDelayMonths(inr("60001"), inr("30000"))).isEqualTo(3);
		assertThat(AffordabilityCalculator.goalDelayMonths(inr("60000"), inr("-100"))).isNull();
	}

}
