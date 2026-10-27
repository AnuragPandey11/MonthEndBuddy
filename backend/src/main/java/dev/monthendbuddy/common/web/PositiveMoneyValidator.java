package dev.monthendbuddy.common.web;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PositiveMoneyValidator implements ConstraintValidator<PositiveMoney, MoneyDto> {

	/** Plain decimal only: no sign, exponent or grouping separators. */
	private static final Pattern PLAIN_DECIMAL = Pattern.compile("\\d{1,15}(\\.\\d{1,2})?");

	/** ISO 4217 shape; whether the code is supported is decided when converting (422 unsupported_currency). */
	private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

	private boolean allowZero;

	@Override
	public void initialize(PositiveMoney annotation) {
		this.allowZero = annotation.allowZero();
	}

	@Override
	public boolean isValid(MoneyDto value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		if (value.currency() == null || !CURRENCY.matcher(value.currency()).matches() || value.amount() == null
				|| !PLAIN_DECIMAL.matcher(value.amount()).matches()) {
			return false;
		}
		int sign = new BigDecimal(value.amount()).signum();
		return allowZero ? sign >= 0 : sign > 0;
	}

}
