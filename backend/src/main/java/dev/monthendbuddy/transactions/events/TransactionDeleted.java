package dev.monthendbuddy.transactions.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.events.DomainEvent;

/** A transaction was deleted; carries what it was so consumers can recompute the affected month and category. */
public record TransactionDeleted(UUID eventId, UserId userId, UUID aggregateId, Instant occurredAt, String type,
		Money amount, UUID categoryId, LocalDate transactionDate) implements DomainEvent {

	@Override
	public String eventType() {
		return "TransactionDeleted";
	}

	@Override
	public int schemaVersion() {
		return 1;
	}

}
