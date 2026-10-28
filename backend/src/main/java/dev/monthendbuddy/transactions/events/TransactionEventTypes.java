package dev.monthendbuddy.transactions.events;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.monthendbuddy.common.events.EventType;

/** The transactions module's events on Kafka (architecture §12.3): all on {@code meb.transactions.v1}. */
@Configuration(proxyBeanMethods = false)
class TransactionEventTypes {

	static final String TOPIC = "meb.transactions.v1";

	@Bean
	EventType transactionCreatedType() {
		return new EventType("TransactionCreated", TransactionCreated.class, TOPIC, 1);
	}

	@Bean
	EventType transactionUpdatedType() {
		return new EventType("TransactionUpdated", TransactionUpdated.class, TOPIC, 1);
	}

	@Bean
	EventType transactionDeletedType() {
		return new EventType("TransactionDeleted", TransactionDeleted.class, TOPIC, 1);
	}

	@Bean
	EventType transactionsImportedType() {
		return new EventType("TransactionsImported", TransactionsImported.class, TOPIC, 1);
	}

}
