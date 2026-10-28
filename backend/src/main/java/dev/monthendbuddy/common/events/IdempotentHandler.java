package dev.monthendbuddy.common.events;

import java.util.Set;

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
 * <p>
 * With {@code meb.events.mode=kafka} the same handler sits behind the Kafka consumer instead (P5.4): it declares the
 * events it takes in {@link #eventTypes()}, its consumer group is the part of {@link #consumerName()} before the first
 * dot ({@code meb-analytics.budget} is in {@code meb-analytics}), and the consumer calls {@link #deliver} inside a
 * transaction of its own, so a failure reaches Kafka's retry and dead-letter handling.
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

	/** The event records this handler takes; the Kafka consumer subscribes to their topics. */
	public abstract Set<Class<? extends DomainEvent>> eventTypes();

	// Not final: the handlers are CGLIB proxies (@Transactional), and a final method would run on the proxy instance,
	// whose fields are null, instead of the target.
	public String consumer() {
		return consumerName();
	}

	public String consumerGroup() {
		String name = consumerName();
		int dot = name.indexOf('.');
		return dot < 0 ? name : name.substring(0, dot);
	}

	/** Kafka delivery (P5.4): the caller supplies the transaction, which a thrown exception rolls back. */
	public void deliver(DomainEvent event) {
		handle(event);
	}

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
