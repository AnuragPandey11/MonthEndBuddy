package dev.monthendbuddy.transactions.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.events.DomainEvent;

/**
 * A transaction was created. Like every event it carries IDs and amounts only, never description or notes
 * (architecture §12.3); {@code type} is the {@code TransactionType} name, so consumers need only this package.
 */
public record TransactionCreated(UUID eventId, UserId userId, UUID aggregateId, Instant occurredAt, String type,
		Money amount, UUID categoryId, LocalDate transactionDate) implements DomainEvent {

	@Override
	public String eventType() {
		return "TransactionCreated";
	}

	@Override
	public int schemaVersion() {
		return 1;
	}

}
