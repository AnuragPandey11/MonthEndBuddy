package dev.monthendbuddy.common.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import dev.monthendbuddy.common.Money;

/**
 * Writes {@link Money} in the API wire format, {@code {"amount": "12450.00", "currency": "INR"}}, so a domain value
 * that reaches a response by accident still never appears as a JSON number (architecture §6.1). Request bodies use
 * {@code MoneyDto} instead, so there is deliberately no deserializer.
 */
public class MoneyJacksonModule extends SimpleModule {

	public MoneyJacksonModule() {
		super("MoneyJacksonModule");
		addSerializer(Money.class, new MoneySerializer());
	}

	static final class MoneySerializer extends ValueSerializer<Money> {

		@Override
		public void serialize(Money value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
			gen.writeStartObject();
			gen.writeStringProperty("amount", value.toApiString());
			gen.writeStringProperty("currency", value.currency().name());
			gen.writeEndObject();
		}

	}

}
