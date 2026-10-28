package dev.monthendbuddy.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.analytics.events.AnomalyDetected;
import dev.monthendbuddy.budgets.events.BudgetThresholdReached;
import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.Uuids;
import dev.monthendbuddy.common.events.DomainEvent;
import dev.monthendbuddy.notifications.NotificationType;
import dev.monthendbuddy.notifications.domain.NotificationTemplates.Content;
import dev.monthendbuddy.transactions.events.TransactionsImported;

class NotificationTemplatesTest {

	private static final UserId USER = UserId.of("0190f1a2-0000-7000-8000-000000000001");

	private static final Instant AT = Instant.parse("2026-10-28T06:30:00Z");

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

	@Test
	void aBudgetThresholdNamesTheAmountsAndTheMonth() {
		Content c = render(new BudgetThresholdReached(Uuids.v7(), USER, UUID.randomUUID(), AT,
				LocalDate.of(2026, 10, 1), 80, new BigDecimal("84.50"), Money.of("150000", CurrencyCode.INR),
				Money.of("126750", CurrencyCode.INR), null))
			.orElseThrow();
		assertThat(c.type()).isEqualTo(NotificationType.BUDGET_THRESHOLD);
		assertThat(c.title()).isEqualTo("Budget 80% used");
		assertThat(c.body()).isEqualTo("You've spent ₹1,26,750.00 of a ₹1,50,000.00 budget in October 2026 (84.5%).");
		assertThat(c.link()).isEqualTo("/budgets");
	}

	@Test
	void rupeesUseLakhAndCroreGroups() {
		assertThat(NotificationTemplates.money(Money.of("5", CurrencyCode.INR))).isEqualTo("₹5.00");
		assertThat(NotificationTemplates.money(Money.of("999.995", CurrencyCode.INR))).isEqualTo("₹1,000.00");
		assertThat(NotificationTemplates.money(Money.of("40000", CurrencyCode.INR))).isEqualTo("₹40,000.00");
		assertThat(NotificationTemplates.money(Money.of("126750", CurrencyCode.INR))).isEqualTo("₹1,26,750.00");
		assertThat(NotificationTemplates.money(Money.of("123456789.5", CurrencyCode.INR))).isEqualTo("₹12,34,56,789.50");
		assertThat(NotificationTemplates.money(Money.of("-2500", CurrencyCode.INR))).isEqualTo("-₹2,500.00");
	}

	@Test
	void aThresholdFromAPastMonthNotifiesNothing() {
		// An imported or reseeded history crosses thresholds in every month it covers; only this month's are news.
		assertThat(render(new BudgetThresholdReached(Uuids.v7(), USER, UUID.randomUUID(), AT, LocalDate.of(2026, 9, 1),
				100, new BigDecimal("112.00"), Money.of("1000", CurrencyCode.INR), Money.of("1120", CurrencyCode.INR),
				null)))
			.isEmpty();
	}

	@Test
	void aCrossedLimitSaysTheBudgetIsUsedUp() {
		Content c = render(new BudgetThresholdReached(Uuids.v7(), USER, UUID.randomUUID(), AT,
				LocalDate.of(2026, 10, 1), 100, new BigDecimal("106.00"), Money.of("1000", CurrencyCode.INR),
				Money.of("1060", CurrencyCode.INR), null))
			.orElseThrow();
		assertThat(c.title()).isEqualTo("Budget used up");
		assertThat(c.body()).endsWith("(106%).");
	}

	@Test
	void anAnomalyPointsToTheDashboard() {
		Content c = render(new AnomalyDetected(Uuids.v7(), USER, UUID.randomUUID(), AT,
				"ABSOLUTE_THRESHOLD", "TRANSACTION", UUID.randomUUID(), LocalDate.of(2026, 10, 1), "WARNING"))
			.orElseThrow();
		assertThat(c.type()).isEqualTo(NotificationType.ANOMALY);
		assertThat(c.body()).startsWith("An expense is above your large-transaction threshold.");
		assertThat(c.link()).isEqualTo("/");
	}

	@Test
	void anImportCountsItsTransactionsButADemoReseedIsSilent() {
		Content c = render(new TransactionsImported(Uuids.v7(), USER, UUID.randomUUID(), AT, 412,
				LocalDate.of(2026, 5, 1), LocalDate.of(2026, 10, 27)))
			.orElseThrow();
		assertThat(c.type()).isEqualTo(NotificationType.IMPORT_COMPLETED);
		assertThat(c.body()).isEqualTo("412 transactions imported, dated 1 May 2026 to 27 Oct 2026.");
		assertThat(render(new TransactionsImported(Uuids.v7(), USER, USER.value(), AT, 412,
				LocalDate.of(2026, 5, 1), LocalDate.of(2026, 10, 27))))
			.isEmpty();
	}

	private static Optional<Content> render(DomainEvent event) {
		return NotificationTemplates.render(event, TODAY);
	}

}
