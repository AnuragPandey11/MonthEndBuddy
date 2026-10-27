package dev.monthendbuddy.transactions;

import java.time.LocalDate;
import java.util.UUID;

import dev.monthendbuddy.common.Money;

/**
 * A transaction as returned by the REST API and to other modules (LLD §11.3). {@code amount} serializes as
 * {@code {"amount": "…", "currency": "…"}}; {@code category} is {@code null} when uncategorised.
 */
public record TransactionView(UUID id, TransactionType type, Money amount, LocalDate transactionDate,
		String description, String merchant, CategoryRef category, CategorySource categorySource, String notes,
		TransactionSource source, long version) {

}
