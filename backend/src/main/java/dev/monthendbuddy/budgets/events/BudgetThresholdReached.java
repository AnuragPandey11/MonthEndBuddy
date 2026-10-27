package dev.monthendbuddy.budgets.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.events.DomainEvent;

/** A budget reached one of its alert thresholds in a month, for the first time (architecture §9.2). */
public record BudgetThresholdReached(UUID eventId, UserId userId, UUID aggregateId, Instant occurredAt,
		LocalDate periodStart, int threshold, BigDecimal pctUsed, Money limit, Money spent, UUID categoryId)
		implements DomainEvent {

	@Override
	public String eventType() {
		return "BudgetThresholdReached";
	}

	@Override
	public int schemaVersion() {
		return 1;
	}

}
