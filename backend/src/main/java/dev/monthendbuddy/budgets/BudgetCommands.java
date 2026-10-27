package dev.monthendbuddy.budgets;

import java.util.List;
import java.util.UUID;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;

/** Creating budgets from outside the module (the demo seed). Users create budgets through the REST API. */
public interface BudgetCommands {

	/** @param categoryId null for the overall budget */
	UUID createBudget(UserId user, UUID categoryId, Money limit, List<Integer> alertThresholds);

}
