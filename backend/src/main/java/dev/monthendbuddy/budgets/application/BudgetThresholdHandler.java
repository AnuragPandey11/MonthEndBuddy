package dev.monthendbuddy.budgets.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.monthendbuddy.budgets.BudgetStatus;
import dev.monthendbuddy.budgets.BudgetStatusService;
import dev.monthendbuddy.budgets.events.BudgetThresholdReached;
import dev.monthendbuddy.budgets.infrastructure.ThresholdEventRepository;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.Uuids;
import dev.monthendbuddy.common.events.DomainEvent;
import dev.monthendbuddy.common.events.EventPublisher;
import dev.monthendbuddy.common.events.IdempotentHandler;
import dev.monthendbuddy.identity.UserSettingsQuery;
import dev.monthendbuddy.transactions.events.TransactionCreated;
import dev.monthendbuddy.transactions.events.TransactionDeleted;
import dev.monthendbuddy.transactions.events.TransactionUpdated;
import dev.monthendbuddy.transactions.events.TransactionsImported;

/**
 * After any transaction write, re-checks the budgets of the affected months and publishes
 * {@link BudgetThresholdReached} for each threshold crossed for the first time that month (architecture §9.2). The
 * insert into {@code budget_threshold_events} decides "first time", so repeated writes and re-delivered events never
 * fire twice.
 */
@Component
class BudgetThresholdHandler extends IdempotentHandler {

	private final BudgetStatusService status;

	private final ThresholdEventRepository fired;

	private final EventPublisher events;

	private final UserSettingsQuery settings;

	private final Clock clock;

	BudgetThresholdHandler(JdbcClient jdbc, BudgetStatusService status, ThresholdEventRepository fired,
			EventPublisher events, UserSettingsQuery settings, Clock clock) {
		super(jdbc);
		this.status = status;
		this.fired = fired;
		this.events = events;
		this.settings = settings;
		this.clock = clock;
	}

	@Override
	public Set<Class<? extends DomainEvent>> eventTypes() {
		return Set.of(TransactionCreated.class, TransactionUpdated.class, TransactionDeleted.class, TransactionsImported.class);
	}

	@Override
	protected String consumerName() {
		return "meb-analytics.budget";
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
		Set<YearMonth> months = new TreeSet<>();
		switch (event) {
			case TransactionCreated e when "EXPENSE".equals(e.type()) -> months.add(YearMonth.from(e.transactionDate()));
			case TransactionUpdated e -> {
				months.add(YearMonth.from(e.transactionDate()));
				months.add(YearMonth.from(e.previousTransactionDate()));
			}
			case TransactionsImported e -> {
				for (YearMonth m = YearMonth.from(e.firstDate()); !m.isAfter(YearMonth.from(e.lastDate())); m = m
					.plusMonths(1)) {
					months.add(m);
				}
			}
			default -> {
				// Income and deletions cannot raise spending, so no threshold can newly be crossed.
			}
		}
		if (months.isEmpty()) {
			return;
		}
		LocalDate today = settings.get(event.userId()).today(clock);
		for (YearMonth month : months) {
			if (!month.atDay(1).isAfter(today)) {
				fireNewlyCrossed(event.userId(), month);
			}
		}
	}

	private void fireNewlyCrossed(UserId user, YearMonth month) {
		for (BudgetStatus budget : status.statusWithoutEvidence(user, month).budgets()) {
			for (int threshold : budget.thresholdsCrossed()) {
				if (fired.recordFirstFiring(budget.budgetId(), month.atDay(1), threshold)) {
					events.publish(new BudgetThresholdReached(Uuids.v7(), user, budget.budgetId(), clock.instant(),
							month.atDay(1), threshold, budget.pctUsed(), budget.limit(), budget.spent(),
							budget.categoryId()));
				}
			}
		}
	}

}
