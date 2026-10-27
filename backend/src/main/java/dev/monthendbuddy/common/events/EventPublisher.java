package dev.monthendbuddy.common.events;

/**
 * Publishes domain events (architecture D10). The implementation is chosen by {@code meb.events.mode}: in-process
 * until Phase 5, then outbox + Kafka. Handlers are the same in both modes.
 */
public interface EventPublisher {

	void publish(DomainEvent event);

}
