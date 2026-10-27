package dev.monthendbuddy.identity;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;

/** How other modules change a user's stated savings: the month-end review adds what the user kept (architecture §9.6). */
public interface UserSavingsCommands {

	/**
	 * Adds {@code amount} to Savings today. Savings must already be known: the caller checks first, because adding to
	 * an unknown balance would invent one.
	 */
	UserSettingsView addToCurrentSavings(UserId userId, Money amount);

}
