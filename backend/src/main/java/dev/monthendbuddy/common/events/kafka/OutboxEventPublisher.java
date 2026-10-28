package dev.monthendbuddy.common.events.kafka;

import org.slf4j.MDC;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.common.events.DomainEvent;
import dev.monthendbuddy.common.events.EventPublisher;
import dev.monthendbuddy.common.events.EventType;

/**
 * {@code meb.events.mode=kafka} (architecture §12.2): writes the event to {@code outbox_events} in the caller's
 * transaction, so it is published if and only if the change commits. {@code MANDATORY}: a publish outside a
 * transaction is a bug, and fails instead of being written on its own.
 */
@Component
@ConditionalOnProperty(name = "meb.events.mode", havingValue = "kafka")
class OutboxEventPublisher implements EventPublisher {

	private final JdbcClient jdbc;

	private final EventCodec codec;

	private final EventTypes types;

	OutboxEventPublisher(JdbcClient jdbc, EventCodec codec, EventTypes types) {
		this.jdbc = jdbc;
		this.codec = codec;
		this.types = types;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void publish(DomainEvent event) {
		EventType type = types.of(event.getClass());
		jdbc.sql("""
				insert into outbox_events (id, topic, message_key, event_type, envelope, occurred_at)
				values (:id, :topic, :key, :type, :envelope, :occurredAt)
				""")
			.param("id", event.eventId())
			.param("topic", type.topic())
			.param("key", event.userId().value().toString())
			.param("type", type.name())
			.param("envelope", codec.encode(event, MDC.get("traceId")))
			.param("occurredAt", java.sql.Timestamp.from(event.occurredAt()))
			.update();
	}

}
