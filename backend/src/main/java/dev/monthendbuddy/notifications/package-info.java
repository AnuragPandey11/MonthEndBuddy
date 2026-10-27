/**
 * In-app notifications created from domain events (LLD §9). Depends only on other modules' {@code events}
 * named interfaces.
 */
@ApplicationModule(displayName = "Notifications",
		allowedDependencies = { "identity", "transactions::events", "budgets::events", "analytics::events" })
package dev.monthendbuddy.notifications;

import org.springframework.modulith.ApplicationModule;
