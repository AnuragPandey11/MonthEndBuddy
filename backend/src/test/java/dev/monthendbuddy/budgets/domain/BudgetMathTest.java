package dev.monthendbuddy.budgets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;

class BudgetMathTest {

	private static Money inr(String amount) {
		return Money.of(amount, CurrencyCode.INR);
	}

	@Test
	void remainingAndPercentage() {
		assertThat(BudgetMath.remaining(inr("6000"), inr("4500"))).isEqualTo(inr("1500"));
		assertThat(BudgetMath.pctUsed(inr("6000"), inr("4500"))).isEqualByComparingTo("75.0");
		assertThat(BudgetMath.pctUsed(inr("3"), inr("1"))).isEqualByComparingTo("33.3");
	}

	@Test
	void overspendGivesNegativeRemainingAndMoreThanHundredPercent() {
		assertThat(BudgetMath.remaining(inr("6000"), inr("8400")).isNegative()).isTrue();
		assertThat(BudgetMath.pctUsed(inr("6000"), inr("8400"))).isEqualByComparingTo("140.0");
	}

	@Test
	void thresholdsAreCrossedAtOrAbove() {
		List<Integer> thresholds = List.of(100, 80);
		assertThat(BudgetMath.crossedThresholds(thresholds, new BigDecimal("79.9"))).isEmpty();
		assertThat(BudgetMath.crossedThresholds(thresholds, new BigDecimal("80.0"))).containsExactly(80);
		assertThat(BudgetMath.crossedThresholds(thresholds, new BigDecimal("140.0"))).containsExactly(80, 100);
	}

}
