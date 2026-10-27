package dev.monthendbuddy.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.support.IntegrationTest;
import dev.monthendbuddy.transactions.application.TransactionService;
import dev.monthendbuddy.transactions.events.TransactionCreated;
import dev.monthendbuddy.transactions.events.TransactionDeleted;
import dev.monthendbuddy.transactions.events.TransactionUpdated;

/** Writes publish domain events with IDs and amounts, and nothing else (architecture §12.3). */
@RecordApplicationEvents
class TransactionEventsIT extends IntegrationTest {

	@Autowired
	TransactionService transactions;

	@Autowired
	ApplicationEvents events;

	@Test
	void createUpdateAndDeleteEachPublishOneEvent() {
		String token = api().register().accessToken();
		UserId user = UserId.of(api().bearer(token).get("/api/v1/me").json().get("id").asString());
		LocalDate firstDate = TransactionFixtures.today().minusDays(5);
		UUID dining = UUID.fromString(TransactionFixtures.DINING);
		UUID groceries = UUID.fromString(TransactionFixtures.GROCERIES);

		TransactionView created = transactions.create(user, new TransactionService.Input(TransactionType.EXPENSE,
				Money.of("420", CurrencyCode.INR), firstDate, "Lunch", "Cafe", dining, "secret note"));
		assertThat(events.stream(TransactionCreated.class)).singleElement().satisfies(e -> {
			assertThat(e.userId()).isEqualTo(user);
			assertThat(e.aggregateId()).isEqualTo(created.id());
			assertThat(e.eventId().version()).isEqualTo(7);
			assertThat(e.type()).isEqualTo("EXPENSE");
			assertThat(e.amount()).isEqualTo(Money.of("420", CurrencyCode.INR));
			assertThat(e.categoryId()).isEqualTo(dining);
			assertThat(e.transactionDate()).isEqualTo(firstDate);
			assertThat(e.toString()).doesNotContain("Lunch", "Cafe", "secret note");
		});

		transactions.update(user, created.id(), new TransactionService.Input(TransactionType.EXPENSE,
				Money.of("500", CurrencyCode.INR), firstDate.plusDays(1), "Lunch", null, groceries, null), 0);
		assertThat(events.stream(TransactionUpdated.class)).singleElement().satisfies(e -> {
			assertThat(e.previousCategoryId()).isEqualTo(dining);
			assertThat(e.previousTransactionDate()).isEqualTo(firstDate);
			assertThat(e.categoryId()).isEqualTo(groceries);
			assertThat(e.transactionDate()).isEqualTo(firstDate.plusDays(1));
		});

		transactions.delete(user, created.id());
		assertThat(events.stream(TransactionDeleted.class)).singleElement()
			.satisfies(e -> assertThat(e.amount()).isEqualTo(Money.of("500", CurrencyCode.INR)));
	}

}
