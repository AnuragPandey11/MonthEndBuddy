package dev.monthendbuddy.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.common.error.ValidationException;

class PeriodTest {

	@Test
	void ofMonthCoversFebruaryInLeapAndCommonYears() {
		assertThat(Period.ofMonth(YearMonth.of(2028, 2)).endInclusive()).isEqualTo(LocalDate.of(2028, 2, 29));
		assertThat(Period.ofMonth(YearMonth.of(2026, 2)).endInclusive()).isEqualTo(LocalDate.of(2026, 2, 28));
		assertThat(Period.ofMonth(YearMonth.of(2026, 9)).startInclusive()).isEqualTo(LocalDate.of(2026, 9, 1));
	}

	@Test
	void lastCompleteMonthsExcludesTheCurrentMonth() {
		Period p = Period.lastCompleteMonths(3, LocalDate.of(2026, 10, 26));
		assertThat(p.startInclusive()).isEqualTo(LocalDate.of(2026, 7, 1));
		assertThat(p.endInclusive()).isEqualTo(LocalDate.of(2026, 9, 30));
		assertThat(p.months()).containsExactly(YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9));
	}

	@Test
	void lastCompleteMonthsCrossesYearBoundaryOnTheFirstOfJanuary() {
		Period p = Period.lastCompleteMonths(1, LocalDate.of(2027, 1, 1));
		assertThat(p).isEqualTo(Period.ofMonth(YearMonth.of(2026, 12)));
	}

	@Test
	void containsIsInclusiveAtBothEnds() {
		Period p = Period.ofMonth(YearMonth.of(2026, 9));
		assertThat(p.contains(LocalDate.of(2026, 9, 1))).isTrue();
		assertThat(p.contains(LocalDate.of(2026, 9, 30))).isTrue();
		assertThat(p.contains(LocalDate.of(2026, 8, 31))).isFalse();
		assertThat(p.contains(LocalDate.of(2026, 10, 1))).isFalse();
	}

	@Test
	void allowsExactlyTwentyFourMonthsAndRejectsTwentyFive() {
		Period max = new Period(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31));
		assertThat(max.lengthInMonths()).isEqualTo(24);
		assertThatThrownBy(() -> new Period(LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 1)))
			.isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> Period.lastCompleteMonths(25, LocalDate.of(2026, 10, 26)))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsStartAfterEnd() {
		assertThatThrownBy(() -> new Period(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)))
			.isInstanceOf(ValidationException.class)
			.satisfies(e -> assertThat(((ValidationException) e).errors().get(0).field()).isEqualTo("period"));
	}

	@Test
	void futurePeriodIsRejectedButTheCurrentMonthIsNot() {
		LocalDate today = LocalDate.of(2026, 10, 26);
		assertThat(Period.ofMonth(YearMonth.of(2026, 10)).requireNotInFuture(today)).isNotNull();
		assertThatThrownBy(() -> Period.ofMonth(YearMonth.of(2026, 11)).requireNotInFuture(today))
			.isInstanceOf(ValidationException.class);
	}

}
