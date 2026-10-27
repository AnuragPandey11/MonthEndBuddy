package dev.monthendbuddy.common.web;

import java.math.BigDecimal;
import java.util.Arrays;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.error.UnsupportedCurrencyException;

/**
 * Wire format for money (architecture §6.1): {@code {"amount": "12450.00", "currency": "INR"}}. Both fields are
 * strings: the amount so no client parses it as a float, the currency so an unsupported but well-formed code (e.g.
 * {@code USD}) is a 422 {@code unsupported_currency}, not a JSON parse error. Validate inputs with
 * {@link PositiveMoney}.
 */
public record MoneyDto(String amount, String currency) {

	public static MoneyDto from(Money money) {
		return new MoneyDto(money.toApiString(), money.currency().name());
	}

	/**
	 * Converts a validated input.
	 * @param field the request field, for the error's {@code errors[].field}
	 * @throws UnsupportedCurrencyException if the currency is not one the application supports
	 */
	public Money toMoney(String field) {
		CurrencyCode code = Arrays.stream(CurrencyCode.values())
			.filter(c -> c.name().equals(currency))
			.findFirst()
			.orElseThrow(() -> new UnsupportedCurrencyException(field + ".currency", CurrencyCode.INR));
		return new Money(new BigDecimal(amount), code);
	}

}
