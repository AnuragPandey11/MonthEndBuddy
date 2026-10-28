package dev.monthendbuddy.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.events.DomainEvent;
import dev.monthendbuddy.common.events.IdempotentHandler;
import dev.monthendbuddy.common.events.kafka.EventCodec;
import dev.monthendbuddy.common.events.kafka.EventTypes;

/**
 * Test helper for {@code meb.events.mode=kafka} (P5.4): waits until every event has been relayed and consumed, sends
 * records straight to Kafka (re-deliveries, poison messages), and reads published events back from the outbox.
 */
public class KafkaTestEvents implements DisposableBean {

	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	private final JdbcTemplate jdbc;

	private final KafkaTemplate<String, String> kafka;

	private final EventCodec codec;

	private final EventTypes types;

	private final AdminClient admin;

	/** Consumer group to the topics it reads, derived from the handlers exactly as the consumer derives them. */
	private final Map<String, Set<String>> groups = new HashMap<>();

	KafkaTestEvents(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, EventCodec codec, EventTypes types,
			KafkaAdmin kafkaAdmin, List<IdempotentHandler> handlers) {
		this.jdbc = jdbc;
		this.kafka = kafka;
		this.codec = codec;
		this.types = types;
		this.admin = AdminClient.create(kafkaAdmin.getConfigurationProperties());
		for (IdempotentHandler handler : handlers) {
			Set<String> topics = groups.computeIfAbsent(handler.consumerGroup(), g -> new TreeSet<>());
			handler.eventTypes().forEach(type -> topics.add(types.of(type).topic()));
		}
	}

	/**
	 * Returns once the outbox has nothing unsent and every consumer group has committed up to the end of its topics,
	 * including records sent here directly and records that went to a dead-letter topic. Events that handlers publish
	 * on the way are waited for too: the outbox must look the same before and after the offset check.
	 */
	public void awaitHandled() {
		await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(50)).until(() -> {
			Map<String, Object> before = outboxState();
			if (((Number) before.get("pending")).longValue() > 0 || !consumersCaughtUp()) {
				return false;
			}
			return before.equals(outboxState());
		});
	}

	/** Sends an event as the relay would, outside the outbox: a re-delivery. */
	public RecordMetadata send(DomainEvent event) {
		return sendRaw(types.of(event.getClass()).topic(), event.userId().value().toString(),
				codec.encode(event, null));
	}

	public RecordMetadata sendRaw(String topic, String key, String value) {
		try {
			return kafka.send(topic, key, value).get(10, TimeUnit.SECONDS).getRecordMetadata();
		}
		catch (Exception e) {
			throw new IllegalStateException("Kafka send failed", e);
		}
	}

	/** The events of one type this user published, decoded from the outbox in publish order. */
	public <T extends DomainEvent> List<T> published(Class<T> type, UserId user) {
		return jdbc
			.queryForList("select envelope from outbox_events where event_type = ? and message_key = ? order by seq",
					String.class, types.of(type).name(), user.value().toString())
			.stream()
			.map(codec::decode)
			.map(type::cast)
			.toList();
	}

	/** The sum of a topic's end offsets: how many records it has ever received. */
	public long recordCount(String topic) {
		return endOffsets(Set.of(topic)).values().stream().mapToLong(Long::longValue).sum();
	}

	private Map<String, Object> outboxState() {
		return jdbc.queryForMap("""
				select count(*) filter (where published_at is null) as pending, coalesce(max(seq), 0) as last
				from outbox_events
				""");
	}

	private boolean consumersCaughtUp() throws ExecutionException, InterruptedException {
		for (Map.Entry<String, Set<String>> group : groups.entrySet()) {
			Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets(group.getKey())
				.partitionsToOffsetAndMetadata()
				.get();
			for (Map.Entry<TopicPartition, Long> end : endOffsets(group.getValue()).entrySet()) {
				OffsetAndMetadata offset = committed.get(end.getKey());
				if ((offset == null ? 0 : offset.offset()) < end.getValue()) {
					return false;
				}
			}
		}
		return true;
	}

	private Map<TopicPartition, Long> endOffsets(Set<String> topics) {
		try {
			Map<TopicPartition, OffsetSpec> latest = new HashMap<>();
			admin.describeTopics(topics)
				.allTopicNames()
				.get()
				.forEach((name, description) -> description.partitions()
					.forEach(p -> latest.put(new TopicPartition(name, p.partition()), OffsetSpec.latest())));
			Map<TopicPartition, Long> ends = new HashMap<>();
			for (Map.Entry<TopicPartition, ListOffsetsResultInfo> e : admin.listOffsets(latest).all().get().entrySet()) {
				ends.put(e.getKey(), e.getValue().offset());
			}
			return ends;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
		catch (ExecutionException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void destroy() {
		admin.close();
	}

}
