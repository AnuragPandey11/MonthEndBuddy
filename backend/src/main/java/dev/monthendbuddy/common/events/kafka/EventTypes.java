package dev.monthendbuddy.common.events.kafka;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import dev.monthendbuddy.common.events.DomainEvent;
import dev.monthendbuddy.common.events.EventType;

/** Every {@link EventType} the modules declared, by name and by record class. */
public final class EventTypes {

	private final Map<String, EventType> byName = new LinkedHashMap<>();

	private final Map<Class<? extends DomainEvent>, EventType> byClass = new LinkedHashMap<>();

	public EventTypes(List<EventType> types) {
		for (EventType type : types) {
			if (byName.put(type.name(), type) != null || byClass.put(type.type(), type) != null) {
				throw new IllegalStateException("Event type declared twice: " + type.name());
			}
		}
	}

	public EventType of(Class<? extends DomainEvent> type) {
		EventType found = byClass.get(type);
		if (found == null) {
			throw new IllegalStateException("No EventType declared for " + type.getName());
		}
		return found;
	}

	/** Null for a name no module declares. */
	public EventType named(String name) {
		return byName.get(name);
	}

	public Set<String> topics() {
		Set<String> topics = new TreeSet<>();
		byName.values().forEach(t -> topics.add(t.topic()));
		return topics;
	}

}
