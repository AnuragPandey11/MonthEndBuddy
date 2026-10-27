package dev.monthendbuddy.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;

/** Architecture §6.3 formulas and their edge cases. */
class CashFlowMathTest {

	private static Money inr(String amount) {
		return Money.of(amount, CurrencyCode.INR);
	}

	@Test
	void netMayBeNegative() {
		assertThat(CashFlowMath.net(inr("95000"), inr("71250"))).isEqualTo(inr("23750"));
		assertThat(CashFlowMath.net(inr("1000"), inr("1500"))).isEqualTo(inr("-500"));
	}

	@Test
	void savingsRateIsNetOverIncomeAtScaleOne() {
		assertThat(CashFlowMath.savingsRate(inr("95000"), inr("71250"))).hasValueSatisfying(
				rate -> assertThat(rate).isEqualByComparingTo("25.0").hasScaleOf(1));
		assertThat(CashFlowMath.savingsRate(inr("3"), inr("2"))).hasValueSatisfying(
				rate -> assertThat(rate).isEqualByComparingTo("33.3"));
		assertThat(CashFlowMath.savingsRate(inr("1000"), inr("1500"))).hasValueSatisfying(
				rate -> assertThat(rate).isEqualByComparingTo("-50.0"));
	}

	@Test
	void savingsRateIsAbsentWithoutIncome() {
		assertThat(CashFlowMath.savingsRate(Money.zero(CurrencyCode.INR), inr("500"))).isEmpty();
	}

	@Test
	void deltaAgainstAPreviousValue() {
		CashFlowMath.Delta delta = CashFlowMath.delta(inr("71250"), inr("64300"));
		assertThat(delta.delta()).isEqualTo(inr("6950"));
		assertThat(delta.deltaPct()).isEqualByComparingTo("10.8");
		assertThat(delta.isNew()).isFalse();
		assertThat(CashFlowMath.delta(inr("500"), inr("1000")).deltaPct()).isEqualByComparingTo("-50.0");
	}

	@Test
	void deltaFromZeroHasNoPercentageAndIsNew() {
		CashFlowMath.Delta delta = CashFlowMath.delta(inr("18000"), Money.zero(CurrencyCode.INR));
		assertThat(delta.deltaPct()).isNull();
		assertThat(delta.isNew()).isTrue();
		assertThat(CashFlowMath.delta(Money.zero(CurrencyCode.INR), Money.zero(CurrencyCode.INR)).isNew()).isFalse();
	}

	@Test
	void shareOfTotal() {
		assertThat(CashFlowMath.share(inr("2000"), inr("5299.99"))).isEqualByComparingTo("37.7");
		assertThat(CashFlowMath.share(inr("0"), Money.zero(CurrencyCode.INR))).isEqualByComparingTo("0.0");
	}

}
