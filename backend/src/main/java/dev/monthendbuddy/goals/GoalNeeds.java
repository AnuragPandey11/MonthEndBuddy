package dev.monthendbuddy.goals;

import java.util.List;
import java.util.UUID;

import dev.monthendbuddy.common.Money;

/**
 * What a set of active goals needs each month, for the purchase simulation (architecture §9.4). Goals whose deadline
 * has passed are listed with a {@code null} requirement and are not part of {@code total}.
 */
public record GoalNeeds(Money total, List<GoalNeed> goals) {

	public record GoalNeed(UUID goalId, String name, Money requiredMonthly, ProjectionStatus status) {
	}

}
