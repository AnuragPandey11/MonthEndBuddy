package dev.monthendbuddy.budgets;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.monthendbuddy.budgets.events.BudgetThresholdReached;
import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.Uuids;
import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.IntegrationTest;
import dev.monthendbuddy.transactions.TransactionFixtures;
import dev.monthendbuddy.transactions.TransactionType;
import dev.monthendbuddy.transactions.application.TransactionService;
import dev.monthendbuddy.transactions.events.TransactionCreated;

/**
 * Each threshold fires once per budget per month, however many writes cross it and however often an event is
 * delivered (architecture §9.2). Runs in both event modes (P5.4): {@link BudgetThresholdIT} in-process and
 * {@link BudgetThresholdKafkaIT} through the outbox and Kafka; only how events are observed and re-delivered differs.
 */
abstract class BudgetThresholdScenario extends IntegrationTest {

	@Autowired
	TransactionService transactions;

	@Autowired
	JdbcTemplate jdbc;

	/** The {@link BudgetThresholdReached} events published for this user, in order. */
	abstract List<BudgetThresholdReached> thresholdsReached(UserId user);

	/** Delivers the same event to the handlers again, as at-least-once delivery may, and waits for it. */
	abstract void redeliver(TransactionCreated event);

	@Test
	void thresholdsFireOncePerMonthAndRedeliveryIsHarmless() {
		ApiClient as = api().bearer(api().register().accessToken());
		UserId user = UserId.of(as.get("/api/v1/me").json().get("id").asString());
		String budgetId = as.post("/api/v1/budgets", Map.of("categoryId", TransactionFixtures.DINING, "limit",
				Map.of("amount", "1000", "currency", "INR")))
			.json()
			.get("id")
			.asString();
		LocalDate day = TransactionFixtures.today();

		spend(user, "850", day);
		assertThat(thresholdsReached(user)).singleElement().satisfies(e -> {
			assertThat(e.threshold()).isEqualTo(80);
			assertThat(e.aggregateId()).isEqualTo(UUID.fromString(budgetId));
			assertThat(e.spent()).isEqualTo(Money.of("850", CurrencyCode.INR));
		});

		spend(user, "10", day); // still between 80 and 100: nothing new
		assertThat(thresholdsReached(user)).hasSize(1);

		spend(user, "200", day); // crosses 100
		assertThat(thresholdsReached(user)).extracting(BudgetThresholdReached::threshold).containsExactly(80, 100);

		// The same event delivered again (at-least-once) changes nothing.
		TransactionCreated redelivered = new TransactionCreated(Uuids.v7(), user, UUID.randomUUID(),
				clock.instant(), "EXPENSE", Money.of("1", CurrencyCode.INR), UUID.fromString(TransactionFixtures.DINING),
				day);
		redeliver(redelivered);
		redeliver(redelivered);
		assertThat(thresholdsReached(user)).hasSize(2);
		assertThat(jdbc.queryForObject("select count(*) from budget_threshold_events where budget_id = ?::uuid",
				Integer.class, budgetId)).isEqualTo(2);
		assertThat(jdbc.queryForObject(
				"select count(*) from processed_events where consumer = 'meb-analytics.budget' and event_id = ?",
				Integer.class, redelivered.eventId())).isEqualTo(1);
	}

	private void spend(UserId user, String amount, LocalDate day) {
		transactions.create(user, new TransactionService.Input(TransactionType.EXPENSE, Money.of(amount, CurrencyCode.INR),
				day, "Dinner", null, UUID.fromString(TransactionFixtures.DINING), null));
		awaitEventsHandled();
	}

}
