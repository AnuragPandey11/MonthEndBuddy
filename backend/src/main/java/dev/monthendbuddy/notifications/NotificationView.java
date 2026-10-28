package dev.monthendbuddy.notifications;

import java.time.Instant;
import java.util.UUID;

/** A notification as shown in the top bar. {@code link} is an app path, e.g. {@code /budgets}; unread while {@code readAt} is null. */
public record NotificationView(UUID id, NotificationType type, String title, String body, String link,
		Instant createdAt, Instant readAt) {

}
