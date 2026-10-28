package dev.monthendbuddy.common.events.kafka;

import java.math.BigDecimal;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ObjectNode;

import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.events.DomainEvent;
import dev.monthendbuddy.common.events.EventType;

/**
 * The Kafka envelope (architecture §12.3): {@code eventId}, {@code eventType}, {@code schemaVersion},
 * {@code occurredAt}, {@code userId}, {@code aggregateId}, {@code traceId} and the event record as {@code payload}.
 * Its own mapper writes {@code UserId} as a plain string and {@code Money} as {@code {"amount": "8400.0000",
 * "currency": "INR"}}: the amount is a string at storage scale, not the API's 2 places, so it reads back exactly.
 * Other decimals are strings as well. Decoding accepts schema versions N and N−1;
 * anything it cannot read is a {@link PoisonEventException}.
 */
public final class EventCodec {

	private final JsonMapper json = JsonMapper.builder().addModule(eventModule()).build();

	private final EventTypes types;

	public EventCodec(EventTypes types) {
		this.types = types;
	}

	public String encode(DomainEvent event, String traceId) {
		EventType type = types.of(event.getClass());
		ObjectNode envelope = json.createObjectNode();
		envelope.put("eventId", event.eventId().toString());
		envelope.put("eventType", type.name());
		envelope.put("schemaVersion", event.schemaVersion());
		envelope.put("occurredAt", event.occurredAt().toString());
		envelope.put("userId", event.userId().value().toString());
		envelope.put("aggregateId", event.aggregateId().toString());
		envelope.put("traceId", traceId);
		envelope.set("payload", json.valueToTree(event));
		return json.writeValueAsString(envelope);
	}

	public DomainEvent decode(String message) {
		JsonNode envelope;
		try {
			envelope = json.readTree(message);
		}
		catch (JacksonException e) {
			throw new PoisonEventException("Not a JSON event envelope", e);
		}
		EventType type = types.named(envelope.path("eventType").asString(""));
		if (type == null) {
			throw new PoisonEventException("Unknown event type: " + envelope.path("eventType"), null);
		}
		int version = envelope.path("schemaVersion").asInt(-1);
		if (version != type.schemaVersion() && version != type.schemaVersion() - 1) {
			throw new PoisonEventException(
					type.name() + " schema version " + version + " (accepts " + type.schemaVersion() + " and "
							+ (type.schemaVersion() - 1) + ")",
					null);
		}
		try {
			return json.treeToValue(envelope.path("payload"), type.type());
		}
		catch (JacksonException | IllegalArgumentException e) {
			throw new PoisonEventException("Unreadable " + type.name() + " payload", e);
		}
	}

	private static SimpleModule eventModule() {
		SimpleModule module = new SimpleModule("EventJson");
		module.addSerializer(UserId.class, new ValueSerializer<>() {
			@Override
			public void serialize(UserId value, JsonGenerator gen, SerializationContext ctxt) {
				gen.writeString(value.value().toString());
			}
		});
		module.addDeserializer(UserId.class, new ValueDeserializer<>() {
			@Override
			public UserId deserialize(JsonParser p, DeserializationContext ctxt) {
				return new UserId(UUID.fromString(p.getValueAsString()));
			}
		});
		// Decimals as strings too, so a value such as 84.50 keeps its scale rather than coming back as 84.5.
		module.addSerializer(BigDecimal.class, new ValueSerializer<>() {
			@Override
			public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctxt) {
				gen.writeString(value.toPlainString());
			}
		});
		module.addDeserializer(BigDecimal.class, new ValueDeserializer<>() {
			@Override
			public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) {
				return new BigDecimal(p.getValueAsString());
			}
		});
		module.addSerializer(Money.class, new ValueSerializer<>() {
			@Override
			public void serialize(Money value, JsonGenerator gen, SerializationContext ctxt) {
				gen.writeStartObject();
				gen.writeStringProperty("amount", value.amount().toPlainString());
				gen.writeStringProperty("currency", value.currency().name());
				gen.writeEndObject();
			}
		});
		module.addDeserializer(Money.class, new ValueDeserializer<>() {
			@Override
			public Money deserialize(JsonParser p, DeserializationContext ctxt) {
				JsonNode node = ctxt.readTree(p);
				return new Money(new BigDecimal(node.path("amount").asString()),
						CurrencyCode.valueOf(node.path("currency").asString()));
			}
		});
		return module;
	}

}
