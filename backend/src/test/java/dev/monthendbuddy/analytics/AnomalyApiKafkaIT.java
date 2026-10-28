package dev.monthendbuddy.analytics;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import dev.monthendbuddy.support.KafkaTestcontainersConfiguration;

/**
 * {@link AnomalyApiScenario} with {@code meb.events.mode=kafka}. Same annotations as {@code KafkaIntegrationTest}, so it
 * shares that context.
 */
@Import(KafkaTestcontainersConfiguration.class)
@TestPropertySource(properties = "meb.events.mode=kafka")
class AnomalyApiKafkaIT extends AnomalyApiScenario {

}
