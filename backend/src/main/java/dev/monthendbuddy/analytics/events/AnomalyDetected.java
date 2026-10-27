package dev.monthendbuddy.analytics.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.events.DomainEvent;

/** A new anomaly alert was raised ({@code aggregateId} = the alert). Names are strings so consumers need only this package. */
public record AnomalyDetected(UUID eventId, UserId userId, UUID aggregateId, Instant occurredAt, String ruleId,
		String subjectType, UUID subjectId, LocalDate periodStart, String severity) implements DomainEvent {

	@Override
	public String eventType() {
		return "AnomalyDetected";
	}

	@Override
	public int schemaVersion() {
		return 1;
	}

}
