package dev.monthendbuddy.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount in one currency (architecture §6.1). Arithmetic happens only here, never on {@code double}. The amount
 * is kept at scale 4 (the storage scale) with {@link RoundingMode#HALF_EVEN}; API values use scale 2 through
 * {@link #toApiString()}. Mixing currencies throws.
 */
public record Money(BigDecimal amount, CurrencyCode currency) implements Comparable<Money> {

	public static final int STORAGE_SCALE = 4;

	public static final int API_SCALE = 2;

	public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

	public Money {
		Objects.requireNonNull(amount, "amount");
		Objects.requireNonNull(currency, "currency");
		amount = amount.setScale(STORAGE_SCALE, ROUNDING);
	}

	public static Money of(String amount, CurrencyCode currency) {
		return new Money(new BigDecimal(amount), currency);
	}

	public static Money zero(CurrencyCode currency) {
		return new Money(BigDecimal.ZERO, currency);
	}

	public Money add(Money other) {
		requireSameCurrency(other);
		return new Money(amount.add(other.amount), currency);
	}

	public Money subtract(Money other) {
		requireSameCurrency(other);
		return new Money(amount.subtract(other.amount), currency);
	}

	public Money multiply(BigDecimal factor) {
		return new Money(amount.multiply(factor), currency);
	}

	/** Divides at storage scale; dividing by zero throws {@link ArithmeticException}. */
	public Money divide(BigDecimal divisor) {
		return new Money(amount.divide(divisor, STORAGE_SCALE, ROUNDING), currency);
	}

	public Money negate() {
		return new Money(amount.negate(), currency);
	}

	public boolean isNegative() {
		return amount.signum() < 0;
	}

	public boolean isZero() {
		return amount.signum() == 0;
	}

	public boolean isPositive() {
		return amount.signum() > 0;
	}

	public boolean isGreaterThanOrEqual(Money other) {
		return compareTo(other) >= 0;
	}

	/** Rounds the value to 2 decimal places (it is still held at storage scale). */
	public Money toScale2() {
		return new Money(amount.setScale(API_SCALE, ROUNDING), currency);
	}

	/** The wire format: a plain decimal string with exactly 2 decimal places, e.g. {@code "12450.00"}. */
	public String toApiString() {
		return amount.setScale(API_SCALE, ROUNDING).toPlainString();
	}

	@Override
	public int compareTo(Money other) {
		requireSameCurrency(other);
		return amount.compareTo(other.amount);
	}

	private void requireSameCurrency(Money other) {
		Objects.requireNonNull(other, "other");
		if (other.currency != currency) {
			throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
		}
	}

}
