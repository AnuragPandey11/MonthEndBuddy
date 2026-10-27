package dev.monthendbuddy.budgets;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.transactions.TransactionView;

/**
 * One budget in one month (architecture §9.2). {@code topTransactions} are the largest contributing expenses, the
 * evidence for "why is this budget used up"; {@code remaining} is negative when {@code overspent}.
 */
public record BudgetStatus(UUID budgetId, UUID categoryId, String categoryName, Money limit, Money spent,
		Money remaining, BigDecimal pctUsed, boolean overspent, List<Integer> alertThresholds,
		List<Integer> thresholdsCrossed, List<TransactionView> topTransactions) {

}
