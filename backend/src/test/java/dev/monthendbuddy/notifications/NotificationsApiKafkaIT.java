package dev.monthendbuddy.notifications;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import dev.monthendbuddy.support.KafkaTestcontainersConfiguration;

/**
 * {@link NotificationsApiScenario} with {@code meb.events.mode=kafka}: the notifications come from the
 * {@code meb-notifications} consumer group. Same annotations as {@code KafkaIntegrationTest}.
 */
@Import(KafkaTestcontainersConfiguration.class)
@TestPropertySource(properties = "meb.events.mode=kafka")
class NotificationsApiKafkaIT extends NotificationsApiScenario {

}
