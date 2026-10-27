package dev.monthendbuddy.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import dev.monthendbuddy.common.error.ValidationException;

/** Optional filters for listing transactions; every field may be {@code null}. {@code text} is a substring search. */
public record TransactionFilter(LocalDate from, LocalDate to, UUID categoryId, TransactionType type, String text,
		BigDecimal minAmount, BigDecimal maxAmount) {

	public static final TransactionFilter NONE = new TransactionFilter(null, null, null, null, null, null, null);

	public TransactionFilter {
		if (from != null && to != null && from.isAfter(to)) {
			throw ValidationException.of("from", "must not be after to");
		}
		if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
			throw ValidationException.of("minAmount", "must not be greater than maxAmount");
		}
		text = (text == null || text.isBlank()) ? null : text.strip();
	}

}
