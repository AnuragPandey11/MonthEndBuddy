package dev.monthendbuddy.common.events.kafka;

import static dev.monthendbuddy.transactions.TransactionFixtures.DINING;
import static dev.monthendbuddy.transactions.TransactionFixtures.today;
import static dev.monthendbuddy.transactions.TransactionFixtures.transaction;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.KafkaHeaders;

import dev.monthendbuddy.analytics.events.AnomalyDetected;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.KafkaIntegrationTest;
import dev.monthendbuddy.transactions.events.TransactionCreated;

/**
 * The delivery guarantees of the outbox and Kafka path (implementation plan P5.4, P5.5): duplicates are harmless, a poison
 * message goes to the dead-letter topic without retries, a relay that crashes before stamping a row re-sends it
 * harmlessly, and one user's events are handled in the order they were written.
 */
class KafkaEventsIT extends KafkaIntegrationTest {

	private static final String TRANSACTIONS = "meb.transactions.v1";

	@Autowired
	OutboxRelay relay;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ConsumerFactory<?, ?> consumers;

	private ApiClient as;

	private UserId user;

	@BeforeEach
	void newUser() {
		as = api().bearer(api().register().accessToken());
		user = UserId.of(as.get("/api/v1/me").json().get("id").asString());
	}

	@Test
	void aDuplicateDeliveryRaisesNoSecondAlertOrNotification() {
		spend("30000");
		awaitEventsHandled();
		assertThat(as.get("/api/v1/alerts").json()).hasSize(1);

		TransactionCreated event = kafkaEvents.published(TransactionCreated.class, user).getFirst();
		kafkaEvents.send(event);
		kafkaEvents.send(event);
		awaitEventsHandled();

		assertThat(as.get("/api/v1/alerts").json()).hasSize(1);
		assertThat(timesProcessed(event.eventId(), "meb-analytics.anomaly")).isEqualTo(1);
		assertThat(timesProcessed(event.eventId(), "meb-analytics.budget")).isEqualTo(1);

		// The alert's own event, delivered twice more to the notifications group.
		assertThat(as.get("/api/v1/notifications").json()).hasSize(1);
		AnomalyDetected detected = kafkaEvents.published(AnomalyDetected.class, user).getFirst();
		kafkaEvents.send(detected);
		kafkaEvents.send(detected);
		awaitEventsHandled();
		assertThat(as.get("/api/v1/notifications").json()).hasSize(1);
		assertThat(timesProcessed(detected.eventId(), "meb-notifications.inbox")).isEqualTo(1);
	}

	@Test
	void aPoisonMessageGoesStraightToTheDeadLetterTopic() {
		String junk = "not an event " + UUID.randomUUID();
		try (Consumer<String, String> dlt = deadLetterReader()) {
			long sentAt = System.nanoTime();
			kafkaEvents.sendRaw(TRANSACTIONS, UUID.randomUUID().toString(), junk);
			ConsumerRecord<String, String> dead = awaitRecord(dlt, junk);
			// The first retry would wait 1 s, so arriving sooner proves the poison was not retried.
			assertThat(Duration.ofNanos(System.nanoTime() - sentAt)).isLessThan(Duration.ofMillis(1000));
			assertThat(header(dead, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN) + header(dead, KafkaHeaders.DLT_EXCEPTION_FQCN))
				.contains(PoisonEventException.class.getName());
		}
		awaitEventsHandled(); // the consumer committed past it and carries on
	}

	@Test
	void aRelayCrashBeforeStampingRedeliversHarmlessly() {
		awaitEventsHandled();
		long before = kafkaEvents.recordCount(TRANSACTIONS);
		// Holding the relay's monitor keeps the scheduled pass out while this "relay" sends and crashes before
		// stamping published_at. Once released, the scheduled pass sends the same row again.
		synchronized (relay) {
			spend("30000");
			assertThat(relay.relayBatch(false)).isEqualTo(1);
		}
		awaitEventsHandled();

		TransactionCreated event = kafkaEvents.published(TransactionCreated.class, user).getFirst();
		assertThat(kafkaEvents.recordCount(TRANSACTIONS)).isEqualTo(before + 2);
		assertThat(jdbc.queryForObject("select published_at is not null from outbox_events where id = ?",
				Boolean.class, event.eventId())).isTrue();
		assertThat(timesProcessed(event.eventId(), "meb-analytics.anomaly")).isEqualTo(1);
		assertThat(as.get("/api/v1/alerts").json()).hasSize(1);
	}

	@Test
	void oneUsersEventsAreHandledInTheOrderTheyWereWritten() {
		for (int i = 1; i <= 8; i++) {
			spend(String.valueOf(100 * i));
		}
		awaitEventsHandled();
		String byWrite = """
				select o.id from outbox_events o where o.message_key = ? and o.event_type = 'TransactionCreated'
				order by o.seq
				""";
		String byHandling = """
				select p.event_id from processed_events p join outbox_events o on o.id = p.event_id
				where o.message_key = ? and p.consumer = 'meb-analytics.budget' order by p.processed_at
				""";
		List<UUID> written = jdbc.queryForList(byWrite, UUID.class, user.value().toString());
		assertThat(written).hasSize(8);
		assertThat(jdbc.queryForList(byHandling, UUID.class, user.value().toString())).containsExactlyElementsOf(written);
	}

	private void spend(String amount) {
		assertThat(as.post("/api/v1/transactions", transaction("EXPENSE", amount, today().minusDays(1), "Meal", "Shop",
				DINING)).status()).isEqualTo(201);
	}

	private int timesProcessed(UUID eventId, String consumer) {
		return jdbc.queryForObject("select count(*) from processed_events where consumer = ? and event_id = ?",
				Integer.class, consumer, eventId);
	}

	/** Reads the dead-letter topic from its current end, without joining a group, so it is ready before the send. */
	@SuppressWarnings("unchecked")
	private Consumer<String, String> deadLetterReader() {
		Consumer<String, String> consumer = (Consumer<String, String>) consumers
			.createConsumer("dlt-reader-" + UUID.randomUUID(), null, null);
		TopicPartition dlt = new TopicPartition(TRANSACTIONS + KafkaEventsConfiguration.DLT_SUFFIX, 0);
		consumer.assign(List.of(dlt));
		consumer.seekToEnd(List.of(dlt));
		consumer.position(dlt);
		return consumer;
	}

	private static ConsumerRecord<String, String> awaitRecord(Consumer<String, String> consumer, String value) {
		long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
		while (System.nanoTime() < deadline) {
			for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(50))) {
				if (value.equals(record.value())) {
					return record;
				}
			}
		}
		throw new AssertionError("No dead-letter record: " + value);
	}

	private static String header(ConsumerRecord<?, ?> record, String name) {
		Header header = record.headers().lastHeader(name);
		return header == null ? "" : new String(header.value(), StandardCharsets.UTF_8);
	}

}
