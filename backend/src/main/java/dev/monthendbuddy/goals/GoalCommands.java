package dev.monthendbuddy.goals;

import java.time.LocalDate;
import java.util.UUID;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;

/**
 * Goal writes from outside the module: the demo seed creates goals, and the month-end review adds to them. Users
 * otherwise change goals through the REST API.
 */
public interface GoalCommands {

	UUID createGoal(UserId user, String name, Money target, Money saved, LocalDate targetDate);

	/**
	 * Adds {@code amount} to an active goal's saved amount; the goal becomes achieved once it reaches its target.
	 * Returns {@code false}, changing nothing, when the user has no active goal with that ID (another user's goal looks
	 * the same as a missing one).
	 */
	boolean contribute(UserId user, UUID goalId, Money amount);

}
