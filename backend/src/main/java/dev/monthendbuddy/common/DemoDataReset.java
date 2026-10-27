package dev.monthendbuddy.common;

/**
 * Implemented by modules that own per-user data, so the nightly demo reset can clear the demo account in one
 * transaction without the demo module depending on each of them. Called before transactions and categories are
 * replaced, so data that references them (budgets, alerts) goes first.
 */
public interface DemoDataReset {

	void deleteDemoData(UserId demoUser);

}
