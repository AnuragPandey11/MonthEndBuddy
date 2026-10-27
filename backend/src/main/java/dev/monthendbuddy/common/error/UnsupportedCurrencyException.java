package dev.monthendbuddy.common.error;

import java.util.List;

import dev.monthendbuddy.common.CurrencyCode;

/** A currency other than the user's base currency; v1 has no FX conversion (architecture §6.1). */
public class UnsupportedCurrencyException extends ValidationException {

	public UnsupportedCurrencyException(String field, CurrencyCode baseCurrency) {
		super(List.of(new FieldViolation(field, "only " + baseCurrency + " is supported")));
	}

	@Override
	public String code() {
		return "unsupported_currency";
	}

}
