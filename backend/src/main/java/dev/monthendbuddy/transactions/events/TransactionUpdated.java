package dev.monthendbuddy.transactions.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.events.DomainEvent;

/**
 * A transaction was changed. The previous date and category are included so consumers can recompute both affected
 * months and categories (budgets, anomaly rules).
 */
public record TransactionUpdated(UUID eventId, UserId userId, UUID aggregateId, Instant occurredAt, String type,
		Money amount, UUID categoryId, LocalDate transactionDate, UUID previousCategoryId,
		LocalDate previousTransactionDate) implements DomainEvent {

	@Override
	public String eventType() {
		return "TransactionUpdated";
	}

	@Override
	public int schemaVersion() {
		return 1;
	}

}
