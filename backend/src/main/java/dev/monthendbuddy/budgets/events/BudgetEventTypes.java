package dev.monthendbuddy.budgets.events;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.monthendbuddy.common.events.EventType;

/** The budgets module's events on Kafka (architecture §12.3). */
@Configuration(proxyBeanMethods = false)
class BudgetEventTypes {

	@Bean
	EventType budgetThresholdReachedType() {
		return new EventType("BudgetThresholdReached", BudgetThresholdReached.class, "meb.budgets.v1", 1);
	}

}
