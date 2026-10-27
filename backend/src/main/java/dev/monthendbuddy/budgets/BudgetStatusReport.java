package dev.monthendbuddy.budgets;

import java.time.YearMonth;
import java.util.List;

import dev.monthendbuddy.transactions.TransactionAggregations.DataCoverage;

public record BudgetStatusReport(YearMonth month, List<BudgetStatus> budgets, DataCoverage coverage) {

}
