package dev.monthendbuddy.transactions;

import java.time.LocalDate;

import dev.monthendbuddy.common.Money;

/** One synthetic transaction; {@code categoryName} names a system category. */
public record SeedTransaction(TransactionType type, Money amount, LocalDate transactionDate, String description,
		String merchant, String categoryName) {

}
