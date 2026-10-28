package dev.monthendbuddy.budgets;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import dev.monthendbuddy.budgets.events.BudgetThresholdReached;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.support.KafkaTestEvents;
import dev.monthendbuddy.support.KafkaTestcontainersConfiguration;
import dev.monthendbuddy.transactions.events.TransactionCreated;

/**
 * {@link BudgetThresholdScenario} with {@code meb.events.mode=kafka}: events are read back from the outbox, and the
 * re-delivery is a second copy of the record on Kafka. Same annotations as {@code KafkaIntegrationTest}.
 */
@Import(KafkaTestcontainersConfiguration.class)
@TestPropertySource(properties = "meb.events.mode=kafka")
class BudgetThresholdKafkaIT extends BudgetThresholdScenario {

	@Autowired
	KafkaTestEvents kafkaEvents;

	@Override
	List<BudgetThresholdReached> thresholdsReached(UserId user) {
		return kafkaEvents.published(BudgetThresholdReached.class, user);
	}

	@Override
	void redeliver(TransactionCreated event) {
		kafkaEvents.send(event);
		awaitEventsHandled();
	}

}
