package dev.monthendbuddy.notifications;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.common.DemoDataReset;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.NotFoundException;
import dev.monthendbuddy.common.events.DomainEvent;
import dev.monthendbuddy.identity.UserSettingsQuery;
import dev.monthendbuddy.notifications.domain.NotificationTemplates;
import dev.monthendbuddy.notifications.infrastructure.NotificationRepository;

/** In-app notifications (LLD §9): the latest 50, newest first; created only from domain events. */
@Service
public class NotificationService implements DemoDataReset {

	static final int LIMIT = 50;

	private final NotificationRepository notifications;

	private final UserSettingsQuery settings;

	private final Clock clock;

	NotificationService(NotificationRepository notifications, UserSettingsQuery settings, Clock clock) {
		this.notifications = notifications;
		this.settings = settings;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<NotificationView> list(UserId user, boolean unreadOnly) {
		return notifications.find(user, unreadOnly, LIMIT);
	}

	@Transactional
	public NotificationView markRead(UserId user, UUID id) {
		if (notifications.markRead(user, id, clock.instant()) == 0) {
			throw new NotFoundException("Notification");
		}
		return notifications.findById(user, id).orElseThrow();
	}

	/** At most one notification per event: {@code source_event_id} is unique. "Today" is in the user's timezone. */
	@Transactional
	public void createFromEvent(DomainEvent event) {
		NotificationTemplates.render(event, settings.get(event.userId()).today(clock))
			.ifPresent(c -> notifications.insertIfNew(event.userId(), event.eventId(), c.type(), c.title(), c.body(),
					c.link(), clock.instant()));
	}

	@Override
	@Transactional
	public void deleteDemoData(UserId demoUser) {
		notifications.deleteAllForDemoReset(demoUser);
	}

}
