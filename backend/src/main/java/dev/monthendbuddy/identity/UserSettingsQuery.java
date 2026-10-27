package dev.monthendbuddy.identity;

import dev.monthendbuddy.common.UserId;

/** How other modules read a user's settings. */
public interface UserSettingsQuery {

	UserSettingsView get(UserId userId);

}
