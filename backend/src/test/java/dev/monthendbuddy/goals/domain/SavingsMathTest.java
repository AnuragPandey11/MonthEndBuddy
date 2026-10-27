package dev.monthendbuddy.goals.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;

/** Architecture §6.3 savings formulas at month boundaries. */
class SavingsMathTest {

	private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

	private static Money inr(String amount) {
		return Money.of(amount, CurrencyCode.INR);
	}

	@Test
	void monthsRemainingCountsWholeMonthsFromNextMonthToTheTargetMonth() {
		assertThat(SavingsMath.monthsRemaining(OCTOBER, LocalDate.of(2026, 12, 1))).isEqualTo(2);
		assertThat(SavingsMath.monthsRemaining(OCTOBER, LocalDate.of(2026, 11, 30))).isEqualTo(1);
		assertThat(SavingsMath.monthsRemaining(OCTOBER, LocalDate.of(2026, 10, 31))).isZero();
		assertThat(SavingsMath.monthsRemaining(OCTOBER, LocalDate.of(2027, 2, 28))).isEqualTo(4);
		assertThat(SavingsMath.monthsRemaining(OCTOBER, LocalDate.of(2026, 9, 1))).isEqualTo(-1);
	}

	@Test
	void requiredMonthlyRoundsUpToThePaisa() {
		// Spec §3: "save ₹1,00,000 in eight months".
		assertThat(SavingsMath.requiredMonthly(inr("100000"), 8)).isEqualTo(inr("12500.00"));
		assertThat(SavingsMath.requiredMonthly(inr("100"), 3)).isEqualTo(inr("33.34"));
		assertThatThrownBy(() -> SavingsMath.requiredMonthly(inr("100"), 0)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void completionMonthIsTheFirstMonthTheBalanceReachesTheTarget() {
		assertThat(SavingsMath.completionMonth(inr("0"), inr("100000"), inr("12500"), OCTOBER))
			.contains(YearMonth.of(2027, 6));
		assertThat(SavingsMath.completionMonth(inr("0"), inr("100000"), inr("12499.99"), OCTOBER))
			.contains(YearMonth.of(2027, 7));
		assertThat(SavingsMath.completionMonth(inr("100000"), inr("100000"), inr("0"), OCTOBER)).contains(OCTOBER);
		assertThat(SavingsMath.completionMonth(inr("10"), inr("100"), inr("0"), OCTOBER)).isEmpty();
	}

	@Test
	void seriesStartsWithTodaysBalance() {
		var series = SavingsMath.series(inr("5000"), inr("1000"), OCTOBER, 3);
		assertThat(series).hasSize(4);
		assertThat(series.get(0).balance()).isEqualTo(inr("5000"));
		assertThat(series.get(3).month()).isEqualTo(YearMonth.of(2027, 1));
		assertThat(series.get(3).balance()).isEqualTo(inr("8000"));
	}

}
