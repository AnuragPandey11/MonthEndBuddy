package dev.monthendbuddy.transactions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.monthendbuddy.common.Period;
import dev.monthendbuddy.common.UserId;

/** Filtered reads for other modules (architecture §4.1), newest first. Every query is scoped to {@code user}. */
public interface TransactionQueries {

	List<TransactionView> find(UserId user, TransactionFilter filter, int limit);

	Optional<TransactionView> findById(UserId user, UUID id);

	/** The {@code n} largest expenses in a period, optionally in one category (budget evidence). */
	List<TransactionView> topExpenses(UserId user, Period period, UUID categoryId, int n);

}
