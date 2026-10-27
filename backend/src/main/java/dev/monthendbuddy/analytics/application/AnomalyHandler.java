package dev.monthendbuddy.analytics.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.monthendbuddy.analytics.AnomalyService;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.events.DomainEvent;
import dev.monthendbuddy.common.events.IdempotentHandler;
import dev.monthendbuddy.transactions.TransactionFilter;
import dev.monthendbuddy.transactions.TransactionQueries;
import dev.monthendbuddy.transactions.TransactionType;
import dev.monthendbuddy.transactions.TransactionView;
import dev.monthendbuddy.transactions.events.TransactionCreated;
import dev.monthendbuddy.transactions.events.TransactionDeleted;
import dev.monthendbuddy.transactions.events.TransactionUpdated;
import dev.monthendbuddy.transactions.events.TransactionsImported;

/**
 * Runs the anomaly rules after transaction writes (architecture §9.5). In-process until Phase 5, when the same
 * handler sits behind a Kafka consumer.
 */
@Component
class AnomalyHandler extends IdempotentHandler {

	private static final int MAX_IMPORT_EXPENSES = 10_000;

	private final AnomalyService anomalies;

	private final AnomalyContextFactory contexts;

	private final TransactionQueries transactions;

	AnomalyHandler(JdbcClient jdbc, AnomalyService anomalies, AnomalyContextFactory contexts,
			TransactionQueries transactions) {
		super(jdbc);
		this.anomalies = anomalies;
		this.contexts = contexts;
		this.transactions = transactions;
	}

	@Override
	protected String consumerName() {
		return "meb-analytics.anomaly";
	}

	@TransactionalEventListener
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void on(TransactionCreated event) {
		handle(event);
	}

	@TransactionalEventListener
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void on(TransactionUpdated event) {
		handle(event);
	}

	@TransactionalEventListener
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void on(TransactionDeleted event) {
		handle(event);
	}

	@TransactionalEventListener
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void on(TransactionsImported event) {
		handle(event);
	}

	@Override
	protected void doHandle(DomainEvent event) {
		UserId user = event.userId();
		switch (event) {
			case TransactionCreated e -> evaluateOne(user, e.aggregateId());
			case TransactionUpdated e -> {
				anomalies.forgetTransaction(user, e.aggregateId());
				evaluateOne(user, e.aggregateId());
			}
			case TransactionDeleted e -> anomalies.forgetTransaction(user, e.aggregateId());
			case TransactionsImported e -> {
				LocalDate start = contexts.evaluationStart(user);
				LocalDate from = e.firstDate().isBefore(start) ? start : e.firstDate();
				if (!from.isAfter(e.lastDate())) {
					anomalies.evaluate(user, transactions.find(user, new TransactionFilter(from, e.lastDate(), null,
							TransactionType.EXPENSE, null, null, null), MAX_IMPORT_EXPENSES));
				}
			}
			default -> {
			}
		}
	}

	private void evaluateOne(UserId user, UUID transactionId) {
		transactions.findById(user, transactionId)
			.filter(t -> t.type() == TransactionType.EXPENSE)
			.filter(t -> !t.transactionDate().isBefore(contexts.evaluationStart(user)))
			.ifPresent(t -> anomalies.evaluate(user, List.<TransactionView>of(t)));
	}

}
