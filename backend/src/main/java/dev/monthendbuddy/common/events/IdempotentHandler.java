package dev.monthendbuddy.common.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Template for every event handler (LLD §2, Template Method): {@link #handle} records the event in
 * {@code processed_events} and calls {@link #doHandle} only the first time, so at-least-once delivery never
 * duplicates a side effect. Works the same for in-process events now and Kafka consumers in Phase 5.
 *
 * <p>
 * Subclasses listen with {@code @TransactionalEventListener} plus {@code @Transactional(propagation = REQUIRES_NEW)}:
 * an after-commit listener runs after the publisher's transaction has committed, so it needs its own transaction for
 * the bookkeeping row and the side effects to commit together (implementation plan P2.5).
 */
public abstract class IdempotentHandler {

	private static final Logger log = LoggerFactory.getLogger(IdempotentHandler.class);

	private final JdbcClient jdbc;

	protected IdempotentHandler(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Stable name of this consumer, e.g. {@code meb-analytics.budget}. */
	protected abstract String consumerName();

	protected abstract void doHandle(DomainEvent event);

	protected final void handle(DomainEvent event) {
		int inserted = jdbc.sql("""
				insert into processed_events (consumer, event_id, processed_at) values (:consumer, :eventId, now())
				on conflict do nothing
				""").param("consumer", consumerName()).param("eventId", event.eventId()).update();
		if (inserted == 0) {
			log.debug("{} already processed event {}", consumerName(), event.eventId());
			return;
		}
		doHandle(event);
	}

}
