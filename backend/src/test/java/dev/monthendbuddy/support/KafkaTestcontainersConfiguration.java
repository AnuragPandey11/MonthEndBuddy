package dev.monthendbuddy.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Kafka for the event-mode integration tests (P5.4). The image is pinned in pom.xml ({@code kafka.image}). Like
 * compose.yaml, the broker does not auto-create topics, so a topic the app forgets to declare fails here too.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(KafkaTestEvents.class)
public class KafkaTestcontainersConfiguration {

	@Bean
	@ServiceConnection
	KafkaContainer kafkaContainer() {
		return new KafkaContainer(DockerImageName.parse(System.getProperty("kafka.image", "apache/kafka:4.2.2")))
			.withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false")
			.withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0");
	}

}
