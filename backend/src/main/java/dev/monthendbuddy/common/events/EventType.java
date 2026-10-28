package dev.monthendbuddy.common.events;

/**
 * One kind of domain event as it travels through Kafka (architecture §12.3): its {@code eventType} name, record class,
 * topic and current {@code schemaVersion}. Each publishing module declares its own as beans in its {@code events}
 * package, so {@code common} never imports another module's classes.
 */
public record EventType(String name, Class<? extends DomainEvent> type, String topic, int schemaVersion) {

}
