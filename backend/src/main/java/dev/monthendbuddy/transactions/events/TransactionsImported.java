package dev.monthendbuddy.transactions.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.events.DomainEvent;

/**
 * A batch of transactions arrived at once: a committed CSV import ({@code aggregateId} = the import job) or a demo
 * reseed. Consumers re-read the affected date range ({@code firstDate}..{@code lastDate}) from the database instead of
 * receiving every row.
 */
public record TransactionsImported(UUID eventId, UserId userId, UUID aggregateId, Instant occurredAt, int count,
		LocalDate firstDate, LocalDate lastDate) implements DomainEvent {

	@Override
	public String eventType() {
		return "TransactionsImported";
	}

	@Override
	public int schemaVersion() {
		return 1;
	}

}
