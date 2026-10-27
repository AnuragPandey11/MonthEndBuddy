package dev.monthendbuddy.identity;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;

/** The shared demo account (architecture §5.2), for the demo module's seeding and nightly reset. */
public interface DemoAccounts {

	/** Reserved: nobody can register it, and it never has a usable password. */
	String DEMO_EMAIL = "demo@monthendbuddy.dev";

	boolean demoAccountExists();

	/**
	 * Creates the demo user if it is missing, resets its settings to the defaults with the given current savings, and
	 * returns its ID.
	 * @throws IllegalStateException if the demo email belongs to a non-demo user (never wipe a real account)
	 */
	UserId resetDemoAccount(Money currentSavings);

}
