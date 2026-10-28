package dev.monthendbuddy.common.events.kafka;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import dev.monthendbuddy.common.events.EventType;

/**
 * {@code meb.events.mode=kafka} (architecture §12): the event registry and codec, the topics, the retry and dead-letter
 * policy, and the {@code kafka} health check that the full stack's readiness group includes.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "meb.events.mode", havingValue = "kafka")
class KafkaEventsConfiguration {

	static final String DLT_SUFFIX = ".DLT";

	static final int PARTITIONS = 3;

	private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(2);

	@Bean
	EventTypes eventTypes(List<EventType> types) {
		return new EventTypes(types);
	}

	@Bean
	EventCodec eventCodec(EventTypes types) {
		return new EventCodec(types);
	}

	/**
	 * The broker does not auto-create topics (compose.yaml), so the app declares them: each event topic with 3
	 * partitions, keyed by user, and its dead-letter topic with one.
	 */
	@Bean
	KafkaAdmin.NewTopics eventTopics(EventTypes types) {
		NewTopic[] topics = types.topics()
			.stream()
			.flatMap(topic -> Stream.of(TopicBuilder.name(topic).partitions(PARTITIONS).build(),
					TopicBuilder.name(topic + DLT_SUFFIX).partitions(1).build()))
			.toArray(NewTopic[]::new);
		return new KafkaAdmin.NewTopics(topics);
	}

	/**
	 * Three retries after 1 s, 2 s and 4 s, then the record goes to {@code <topic>.DLT} (architecture §12.3). A
	 * {@link PoisonEventException} can never succeed, so it skips the retries. Boot applies this to the listener
	 * container factory that {@link KafkaEventConsumer} builds its containers from.
	 */
	@Bean
	DefaultErrorHandler eventErrorHandler(KafkaTemplate<?, ?> kafka) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafka,
				(record, e) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));
		ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
		backOff.setInitialInterval(1000);
		backOff.setMultiplier(2);
		DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
		handler.addNotRetryableExceptions(PoisonEventException.class);
		return handler;
	}

	@Bean
	HealthIndicator kafkaHealthIndicator(KafkaAdmin admin) {
		return () -> {
			try (AdminClient client = AdminClient.create(admin.getConfigurationProperties())) {
				int nodes = client
					.describeCluster(new DescribeClusterOptions().timeoutMs((int) HEALTH_TIMEOUT.toMillis()))
					.nodes()
					.get(HEALTH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
					.size();
				return Health.up().withDetail("nodes", nodes).build();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return Health.down(e).build();
			}
			catch (Exception e) {
				return Health.down().withDetail("error", e.getClass().getSimpleName()).build();
			}
		};
	}

}
