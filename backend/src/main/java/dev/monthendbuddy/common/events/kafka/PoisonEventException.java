package dev.monthendbuddy.common.events.kafka;

/**
 * A message that can never be handled: unreadable JSON, an unknown event type, or a schema version other than N and
 * N−1. Not retried; it goes straight to the topic's {@code .DLT} (architecture §12.3).
 */
public class PoisonEventException extends RuntimeException {

	public PoisonEventException(String message, Throwable cause) {
		super(message, cause);
	}

}
