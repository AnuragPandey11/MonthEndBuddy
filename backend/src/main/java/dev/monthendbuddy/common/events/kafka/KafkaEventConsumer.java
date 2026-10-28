package dev.monthendbuddy.common.events.kafka;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import dev.monthendbuddy.common.events.DomainEvent;
import dev.monthendbuddy.common.events.IdempotentHandler;

/**
 * Feeds Kafka to the <b>existing</b> {@link IdempotentHandler}s (implementation plan P5.4). One listener container per
 * consumer group ({@code meb-analytics}, ...), subscribed to the topics of the events its handlers take. Each record
 * is decoded once and given to every handler in the group that takes its event type, each in a transaction of its
 * own: a handler that already succeeded skips a retried record through {@code processed_events}, and one that throws
 * hands the record to the retry and dead-letter policy in {@link KafkaEventsConfiguration}. In this mode no Spring
 * events are published, so the handlers' {@code @TransactionalEventListener} methods never fire.
 */
@Component
@ConditionalOnProperty(name = "meb.events.mode", havingValue = "kafka")
class KafkaEventConsumer implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(KafkaEventConsumer.class);

	private final ConcurrentKafkaListenerContainerFactory<String, String> factory;

	private final EventCodec codec;

	private final EventTypes types;

	private final TransactionTemplate handlerTransaction;

	private final Map<String, List<IdempotentHandler>> groups = new LinkedHashMap<>();

	private final List<ConcurrentMessageListenerContainer<String, String>> containers = new ArrayList<>();

	private volatile boolean running;

	@SuppressWarnings("unchecked")
	KafkaEventConsumer(ConcurrentKafkaListenerContainerFactory<?, ?> factory, EventCodec codec, EventTypes types,
			PlatformTransactionManager transactionManager, List<IdempotentHandler> handlers) {
		this.factory = (ConcurrentKafkaListenerContainerFactory<String, String>) factory;
		this.codec = codec;
		this.types = types;
		this.handlerTransaction = new TransactionTemplate(transactionManager);
		this.handlerTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		for (IdempotentHandler handler : handlers) {
			groups.computeIfAbsent(handler.consumerGroup(), g -> new ArrayList<>()).add(handler);
		}
	}

	@Override
	public synchronized void start() {
		if (containers.isEmpty()) {
			groups.forEach((group, handlers) -> containers.add(container(group, handlers)));
		}
		containers.forEach(ConcurrentMessageListenerContainer::start);
		running = true;
	}

	@Override
	public synchronized void stop() {
		containers.forEach(ConcurrentMessageListenerContainer::stop);
		running = false;
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	private ConcurrentMessageListenerContainer<String, String> container(String group,
			List<IdempotentHandler> handlers) {
		TreeSet<String> topics = new TreeSet<>();
		handlers.forEach(h -> h.eventTypes().forEach(type -> topics.add(types.of(type).topic())));
		ConcurrentMessageListenerContainer<String, String> container = factory
			.createContainer(topics.toArray(String[]::new));
		container.getContainerProperties().setGroupId(group);
		container.getContainerProperties()
			.setMessageListener((MessageListener<String, String>) record -> dispatch(record, handlers));
		container.setBeanName(group);
		log.info("Kafka consumer group {} subscribed to {}", group, topics);
		return container;
	}

	private void dispatch(ConsumerRecord<String, String> record, List<IdempotentHandler> handlers) {
		DomainEvent event = codec.decode(record.value());
		for (IdempotentHandler handler : handlers) {
			if (handler.eventTypes().contains(event.getClass())) {
				handlerTransaction.executeWithoutResult(status -> handler.deliver(event));
			}
		}
	}

}
