package dev.monthendbuddy.common.events.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import dev.monthendbuddy.analytics.events.AnomalyDetected;
import dev.monthendbuddy.budgets.events.BudgetThresholdReached;
import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.Uuids;
import dev.monthendbuddy.common.events.DomainEvent;
import dev.monthendbuddy.common.events.EventType;
import dev.monthendbuddy.transactions.events.TransactionCreated;
import dev.monthendbuddy.transactions.events.TransactionDeleted;
import dev.monthendbuddy.transactions.events.TransactionUpdated;
import dev.monthendbuddy.transactions.events.TransactionsImported;

/** The Kafka envelope round-trips every event exactly, accepts schema versions N and N−1, and rejects the rest. */
class EventCodecTest {

	private static final UserId USER = UserId.of("0190f1a2-0000-7000-8000-000000000001");

	private static final Instant AT = Instant.parse("2026-10-28T06:30:00.123456Z");

	private static final LocalDate DAY = LocalDate.of(2026, 10, 28);

	private static final Money AMOUNT = Money.of("8400.1234", CurrencyCode.INR);

	/** Version 2 for one type, so N−1 = 1 can be tested against a real record. */
	private static final EventTypes TYPES = new EventTypes(List.of(
			new EventType("TransactionCreated", TransactionCreated.class, "meb.transactions.v1", 2),
			new EventType("TransactionUpdated", TransactionUpdated.class, "meb.transactions.v1", 1),
			new EventType("TransactionDeleted", TransactionDeleted.class, "meb.transactions.v1", 1),
			new EventType("TransactionsImported", TransactionsImported.class, "meb.transactions.v1", 1),
			new EventType("BudgetThresholdReached", BudgetThresholdReached.class, "meb.budgets.v1", 1),
			new EventType("AnomalyDetected", AnomalyDetected.class, "meb.alerts.v1", 1)));

	private final EventCodec codec = new EventCodec(TYPES);

	private final JsonMapper json = JsonMapper.builder().build();

	static List<DomainEvent> events() {
		UUID id = UUID.randomUUID();
		UUID category = UUID.randomUUID();
		return List.of(new TransactionCreated(Uuids.v7(), USER, id, AT, "EXPENSE", AMOUNT, category, DAY),
				new TransactionUpdated(Uuids.v7(), USER, id, AT, "EXPENSE", AMOUNT, null, DAY, category,
						DAY.minusMonths(1)),
				new TransactionDeleted(Uuids.v7(), USER, id, AT, "INCOME", AMOUNT, null, DAY),
				new TransactionsImported(Uuids.v7(), USER, id, AT, 412, DAY.minusMonths(6), DAY),
				new BudgetThresholdReached(Uuids.v7(), USER, id, AT, DAY.withDayOfMonth(1), 80,
						new BigDecimal("84.50"), Money.of("10000", CurrencyCode.INR), AMOUNT, category),
				new AnomalyDetected(Uuids.v7(), USER, id, AT, "LARGE_VS_CATEGORY", "TRANSACTION", id,
						DAY.withDayOfMonth(1), "WARNING"));
	}

	@ParameterizedTest
	@MethodSource("events")
	void everyEventRoundTripsExactly(DomainEvent event) {
		assertThat(codec.decode(codec.encode(event, "trace-1"))).isEqualTo(event);
	}

	@Test
	void theEnvelopeCarriesIdsAndMoneyAsStrings() {
		DomainEvent event = events().getFirst();
		var envelope = json.readTree(codec.encode(event, "trace-1"));
		assertThat(envelope.get("eventId").asString()).isEqualTo(event.eventId().toString());
		assertThat(envelope.get("eventType").asString()).isEqualTo("TransactionCreated");
		assertThat(envelope.get("userId").asString()).isEqualTo(USER.value().toString());
		assertThat(envelope.get("traceId").asString()).isEqualTo("trace-1");
		assertThat(envelope.get("payload").get("userId").isString()).isTrue();
		assertThat(envelope.get("payload").get("amount").get("amount").isString()).isTrue();
		assertThat(envelope.get("payload").get("amount").get("amount").asString()).isEqualTo("8400.1234");
	}

	@ParameterizedTest
	@ValueSource(ints = { 2, 1 })
	void versionsNAndTheOneBeforeAreAccepted(int version) {
		DomainEvent event = events().getFirst();
		assertThat(codec.decode(withVersion(event, version))).isEqualTo(event);
	}

	@ParameterizedTest
	@ValueSource(ints = { 3, 0, -1 })
	void otherVersionsArePoison(int version) {
		assertThatThrownBy(() -> codec.decode(withVersion(events().getFirst(), version))).isInstanceOf(PoisonEventException.class)
			.hasMessageContaining("schema version " + version);
	}

	@ParameterizedTest
	@ValueSource(strings = { "not json", "{\"eventType\":\"Nope\",\"schemaVersion\":1,\"payload\":{}}",
			"{\"eventType\":\"TransactionCreated\",\"schemaVersion\":2,\"payload\":{\"userId\":\"x\"}}" })
	void unreadableMessagesArePoison(String message) {
		assertThatThrownBy(() -> codec.decode(message)).isInstanceOf(PoisonEventException.class);
	}

	private String withVersion(DomainEvent event, int version) {
		ObjectNode envelope = (ObjectNode) json.readTree(codec.encode(event, null));
		envelope.put("schemaVersion", version);
		return json.writeValueAsString(envelope);
	}

}
