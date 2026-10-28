package dev.monthendbuddy.common.events.kafka;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends the outbox to Kafka (architecture §12.2). Every 500 ms it claims up to 100 unpublished rows in order with
 * {@code FOR UPDATE SKIP LOCKED}, sends each synchronously (idempotent producer, {@code acks=all}, key = user id), and
 * stamps {@code published_at} in the same transaction. A crash between the send and the stamp re-sends the row on the
 * next pass; consumers drop the duplicate through {@code processed_events}. Metrics: {@code meb.outbox.pending} and
 * {@code meb.outbox.publish.lag}.
 */
@Component
@ConditionalOnProperty(name = "meb.events.mode", havingValue = "kafka")
class OutboxRelay {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

	static final int BATCH = 100;

	private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

	private final JdbcClient jdbc;

	private final KafkaTemplate<String, String> kafka;

	private final TransactionTemplate transactions;

	private final Timer lag;

	OutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate transactions,
			MeterRegistry meters) {
		this.jdbc = jdbc;
		this.kafka = kafka;
		this.transactions = transactions;
		this.lag = Timer.builder("meb.outbox.publish.lag")
			.description("Time from an event's commit to its send to Kafka")
			.register(meters);
		Gauge.builder("meb.outbox.pending", this, OutboxRelay::pending)
			.description("Outbox rows not yet sent to Kafka")
			.register(meters);
	}

	/** Synchronized so that a test holding this relay's monitor keeps the scheduled pass out (the crash test). */
	@Scheduled(fixedDelayString = "${meb.events.relay-interval:500ms}")
	synchronized void relay() {
		try {
			while (relayBatch(true) == BATCH) {
				// A full batch: more may be waiting.
			}
		}
		catch (RuntimeException e) {
			log.warn("Outbox relay pass failed; the rows stay unpublished and are retried: {}", e.toString());
		}
	}

	/**
	 * Claims, sends and (with {@code markPublished}) stamps one batch; returns how many rows it sent. Without the stamp
	 * it behaves like a relay that crashed after sending, which the tests use.
	 */
	int relayBatch(boolean markPublished) {
		Integer sent = transactions.execute(status -> {
			List<OutboxRow> rows = jdbc.sql("""
					select id, topic, message_key, envelope, occurred_at from outbox_events
					where published_at is null order by seq limit :batch for update skip locked
					""").param("batch", BATCH).query(OutboxRow.class).list();
			for (OutboxRow row : rows) {
				send(row);
			}
			if (markPublished && !rows.isEmpty()) {
				jdbc.sql("update outbox_events set published_at = now() where id in (:ids)")
					.param("ids", rows.stream().map(OutboxRow::id).toList())
					.update();
			}
			return rows.size();
		});
		return sent == null ? 0 : sent;
	}

	private void send(OutboxRow row) {
		try {
			kafka.send(row.topic(), row.messageKey(), row.envelope())
				.get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
			lag.record(Duration.between(row.occurredAt(), Instant.now()));
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while sending event " + row.id(), e);
		}
		catch (ExecutionException | TimeoutException e) {
			throw new IllegalStateException("Kafka did not accept event " + row.id(), e);
		}
	}

	/** Published rows are kept for 7 days for diagnosis, then deleted (architecture §12.2). */
	@Scheduled(cron = "0 17 * * * *")
	void purge() {
		int deleted = jdbc.sql("delete from outbox_events where published_at < now() - interval '7 days'").update();
		if (deleted > 0) {
			log.info("Purged {} published outbox rows", deleted);
		}
	}

	private double pending() {
		Long count = jdbc.sql("select count(*) from outbox_events where published_at is null")
			.query(Long.class)
			.single();
		return count == null ? 0 : count;
	}

	record OutboxRow(UUID id, String topic, String messageKey, String envelope, Instant occurredAt) {
	}

}
