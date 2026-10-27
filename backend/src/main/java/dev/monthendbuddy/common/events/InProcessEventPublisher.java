package dev.monthendbuddy.common.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Hands events to Spring's event bus. Handlers listen with {@code @TransactionalEventListener(AFTER_COMMIT)}, so they
 * see an event only once the publishing transaction has committed, and never after a rollback. Such a handler must
 * open its own {@code REQUIRES_NEW} transaction to write (implementation plan P2.5).
 */
@Component
@ConditionalOnProperty(name = "meb.events.mode", havingValue = "in-process", matchIfMissing = true)
class InProcessEventPublisher implements EventPublisher {

	private final ApplicationEventPublisher springPublisher;

	InProcessEventPublisher(ApplicationEventPublisher springPublisher) {
		this.springPublisher = springPublisher;
	}

	@Override
	public void publish(DomainEvent event) {
		springPublisher.publishEvent(event);
	}

}
