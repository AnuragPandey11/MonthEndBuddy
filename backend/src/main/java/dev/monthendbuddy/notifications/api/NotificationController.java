package dev.monthendbuddy.notifications.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.monthendbuddy.identity.CurrentUser;
import dev.monthendbuddy.notifications.NotificationService;
import dev.monthendbuddy.notifications.NotificationView;

/** In-app notifications (architecture §13.2). */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

	private final NotificationService notifications;

	NotificationController(NotificationService notifications) {
		this.notifications = notifications;
	}

	@GetMapping
	List<NotificationView> list(CurrentUser user, @RequestParam(defaultValue = "false") boolean unreadOnly) {
		return notifications.list(user.id(), unreadOnly);
	}

	@PostMapping("/{id}/read")
	NotificationView markRead(CurrentUser user, @PathVariable UUID id) {
		return notifications.markRead(user.id(), id);
	}

}
