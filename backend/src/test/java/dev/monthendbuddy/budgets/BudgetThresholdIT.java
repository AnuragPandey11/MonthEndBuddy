package dev.monthendbuddy.budgets;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import dev.monthendbuddy.budgets.events.BudgetThresholdReached;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.transactions.events.TransactionCreated;

/**
 * {@link BudgetThresholdScenario} with in-process events. Writes happen on the test thread so the after-commit
 * handler's events are recorded.
 */
@RecordApplicationEvents
class BudgetThresholdIT extends BudgetThresholdScenario {

	@Autowired
	ApplicationEvents events;

	@Autowired
	ApplicationEventPublisher springEvents;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Override
	List<BudgetThresholdReached> thresholdsReached(UserId user) {
		return events.stream(BudgetThresholdReached.class).filter(e -> e.userId().equals(user)).toList();
	}

	@Override
	void redeliver(TransactionCreated event) {
		transactionTemplate.executeWithoutResult(s -> springEvents.publishEvent(event));
	}

}
