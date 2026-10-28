package dev.monthendbuddy.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests with {@code meb.events.mode=kafka} (P5.4): the outbox, the relay and the Kafka consumers, on the
 * same context as {@link IntegrationTest} plus a Testcontainers Kafka. The Kafka variants of the event scenarios
 * (e.g. {@code BudgetThresholdKafkaIT}) cannot extend this class, so they repeat its two annotations exactly, which
 * keeps them on this one cached context.
 */
@Import(KafkaTestcontainersConfiguration.class)
@TestPropertySource(properties = "meb.events.mode=kafka")
public abstract class KafkaIntegrationTest extends IntegrationTest {

	@Autowired
	protected KafkaTestEvents kafkaEvents;

}
