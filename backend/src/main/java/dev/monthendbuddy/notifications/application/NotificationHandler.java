package dev.monthendbuddy.notifications.application;

import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.monthendbuddy.analytics.events.AnomalyDetected;
import dev.monthendbuddy.budgets.events.BudgetThresholdReached;
import dev.monthendbuddy.common.events.DomainEvent;
import dev.monthendbuddy.common.events.IdempotentHandler;
import dev.monthendbuddy.notifications.NotificationService;
import dev.monthendbuddy.transactions.events.TransactionsImported;

/**
 * Turns budget thresholds, anomaly alerts and completed imports into notifications (LLD §9, P5.5). Consumer group
 * {@code meb-notifications} in Kafka mode; the same class listens after commit in-process.
 */
@Component
class NotificationHandler extends IdempotentHandler {

	private final NotificationService notifications;

	NotificationHandler(JdbcClient jdbc, NotificationService notifications) {
		super(jdbc);
		this.notifications = notifications;
	}

	@Override
	public Set<Class<? extends DomainEvent>> eventTypes() {
		return Set.of(BudgetThresholdReached.class, AnomalyDetected.class, TransactionsImported.class);
	}

	@Override
	protected String consumerName() {
		return "meb-notifications.inbox";
	}

	@TransactionalEventListener
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void on(BudgetThresholdReached event) {
		handle(event);
	}

	@TransactionalEventListener
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void on(AnomalyDetected event) {
		handle(event);
	}

	@TransactionalEventListener
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void on(TransactionsImported event) {
		handle(event);
	}

	@Override
	protected void doHandle(DomainEvent event) {
		notifications.createFromEvent(event);
	}

}
