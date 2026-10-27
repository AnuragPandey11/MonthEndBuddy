package dev.monthendbuddy.common.events;

import java.time.Instant;
import java.util.UUID;

import dev.monthendbuddy.common.UserId;

/**
 * An event published after a state change (LLD §2). Events are records in the publishing module's {@code events}
 * package and carry IDs and amounts only, never descriptions or notes (architecture §12.3).
 */
public interface DomainEvent {

	UUID eventId();

	String eventType();

	int schemaVersion();

	UserId userId();

	UUID aggregateId();

	Instant occurredAt();

}
