package dev.monthendbuddy.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyTest {

	private static Money inr(String amount) {
		return Money.of(amount, CurrencyCode.INR);
	}

	@Test
	void keepsStorageScaleAndComparesByValue() {
		assertThat(inr("12450").amount()).isEqualByComparingTo("12450.0000");
		assertThat(inr("12450").amount().scale()).isEqualTo(4);
		assertThat(inr("10.5")).isEqualTo(inr("10.50"));
	}

	@Test
	void roundsHalfEvenAtStorageScale() {
		assertThat(inr("1.00005").amount()).isEqualByComparingTo("1.0000");
		assertThat(inr("1.00015").amount()).isEqualByComparingTo("1.0002");
	}

	@Test
	void apiStringHasExactlyTwoDecimalsRoundedHalfEven() {
		assertThat(inr("12450").toApiString()).isEqualTo("12450.00");
		assertThat(inr("0.125").toApiString()).isEqualTo("0.12");
		assertThat(inr("0.135").toApiString()).isEqualTo("0.14");
		assertThat(inr("-75.5").toApiString()).isEqualTo("-75.50");
		assertThat(inr("100000000").toApiString()).isEqualTo("100000000.00");
	}

	@Test
	void arithmetic() {
		assertThat(inr("100.10").add(inr("0.90"))).isEqualTo(inr("101"));
		assertThat(inr("100").subtract(inr("250.25"))).isEqualTo(inr("-150.25"));
		assertThat(inr("100").subtract(inr("250")).isNegative()).isTrue();
		assertThat(inr("19.99").multiply(new BigDecimal("3"))).isEqualTo(inr("59.97"));
		assertThat(inr("100").divide(new BigDecimal("3"))).isEqualTo(inr("33.3333"));
		assertThat(inr("100").divide(new BigDecimal("3")).toApiString()).isEqualTo("33.33");
		assertThat(Money.zero(CurrencyCode.INR).isZero()).isTrue();
		assertThat(inr("5").isGreaterThanOrEqual(inr("5.00"))).isTrue();
		assertThat(inr("4.99").isGreaterThanOrEqual(inr("5"))).isFalse();
	}

	@Test
	void divideByZeroThrows() {
		assertThatThrownBy(() -> inr("1").divide(BigDecimal.ZERO)).isInstanceOf(ArithmeticException.class);
	}

	@Test
	void toScale2RoundsTheValue() {
		assertThat(inr("10.005").toScale2()).isEqualTo(inr("10.00"));
		assertThat(inr("10.015").toScale2()).isEqualTo(inr("10.02"));
	}

	@Test
	void rejectsNulls() {
		assertThatThrownBy(() -> new Money(null, CurrencyCode.INR)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new Money(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> inr("1").add(null)).isInstanceOf(NullPointerException.class);
	}

}
