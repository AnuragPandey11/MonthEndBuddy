package dev.monthendbuddy.transactions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.transactions.CategorySource;
import dev.monthendbuddy.transactions.TransactionSource;
import dev.monthendbuddy.transactions.TransactionType;
import dev.monthendbuddy.transactions.TransactionView;
import dev.monthendbuddy.transactions.infrastructure.TransactionQueryRepository.Keyset;

class TransactionCursorTest {

	@Test
	void roundTripsTheSortPositionOfTheLastRow() {
		UUID id = UUID.randomUUID();
		TransactionView last = new TransactionView(id, TransactionType.EXPENSE, Money.of("1", CurrencyCode.INR),
				LocalDate.of(2026, 9, 30), "x", null, null, CategorySource.NONE, null, TransactionSource.MANUAL, 0);
		String cursor = TransactionCursor.encode(last);
		assertThat(cursor).doesNotContain("|", "+", "/", "=");
		assertThat(TransactionCursor.decode(cursor)).isEqualTo(new Keyset(LocalDate.of(2026, 9, 30), id));
	}

	@Test
	void absentCursorMeansFirstPage() {
		assertThat(TransactionCursor.decode(null)).isNull();
		assertThat(TransactionCursor.decode("")).isNull();
	}

	@Test
	void malformedCursorIsAValidationError() {
		for (String bad : new String[] { "!!!", "bm90LWEtY3Vyc29y", "MjAyNi0wOS0zMHxub3QtYS11dWlk" }) {
			assertThatThrownBy(() -> TransactionCursor.decode(bad)).isInstanceOf(ValidationException.class);
		}
	}

}
