package dev.monthendbuddy.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PositiveMoneyValidatorTest {

	record Input(@PositiveMoney MoneyDto amount) {
	}

	private static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();

	private static final Validator validator = factory.getValidator();

	@AfterAll
	static void close() {
		factory.close();
	}

	private static boolean valid(String amount, String currency) {
		return validator.validate(new Input(new MoneyDto(amount, currency))).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "0.01", "1", "12450.00", "12450.5", "999999999999999.99" })
	void acceptsPositivePlainDecimals(String amount) {
		assertThat(valid(amount, "INR")).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "0.00", "-1", "+1", "1.001", "1e5", "1,000", " 1", "", "abc", "1000000000000000" })
	void rejectsEverythingElse(String amount) {
		assertThat(valid(amount, "INR")).isFalse();
	}

	@Test
	void rejectsMissingOrMalformedCurrencyAndMissingAmount() {
		assertThat(valid("10", null)).isFalse();
		assertThat(valid("10", "inr")).isFalse();
		assertThat(valid("10", "RUPEE")).isFalse();
		assertThat(valid(null, "INR")).isFalse();
	}

	@Test
	void wellFormedButUnsupportedCurrencyPassesValidationAndFailsOnConversion() {
		assertThat(valid("10", "USD")).isTrue();
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> new MoneyDto("10", "USD").toMoney("amount"))
			.isInstanceOf(dev.monthendbuddy.common.error.UnsupportedCurrencyException.class)
			.hasFieldOrPropertyWithValue("errors",
					java.util.List.of(new dev.monthendbuddy.common.error.FieldViolation("amount.currency",
							"only INR is supported")));
	}

	@Test
	void nullIsLeftToNotNull() {
		assertThat(validator.validate(new Input(null))).isEmpty();
	}

}
