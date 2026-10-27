package dev.monthendbuddy.agent.tools;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.monthendbuddy.agent.tools.ProposedAction.ActionType;
import dev.monthendbuddy.budgets.BudgetStatus;
import dev.monthendbuddy.budgets.BudgetStatusReport;
import dev.monthendbuddy.budgets.BudgetStatusService;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.transactions.TransactionAggregations.DataCoverage;

/**
 * Same service call as {@code GET /api/v1/budgets/status}, with each budget's top transactions in the bounded tool
 * row shape. An overspent budget comes with an {@code UPDATE_BUDGET} proposal the user can confirm.
 */
@Component
class GetBudgetStatusTool implements FinanceTool<MonthInput> {

	private final BudgetStatusService budgets;

	private final Clock clock;

	GetBudgetStatusTool(BudgetStatusService budgets, Clock clock) {
		this.budgets = budgets;
		this.clock = clock;
	}

	record BudgetRow(UUID budgetId, UUID categoryId, String categoryName, Money limit, Money spent, Money remaining,
			BigDecimal pctUsed, boolean overspent, List<Integer> thresholdsCrossed,
			List<ToolTransactionRow> topTransactions) {
	}

	record BudgetRows(YearMonth month, List<BudgetRow> budgets, DataCoverage coverage) {
	}

	@Override
	public String name() {
		return "getBudgetStatus";
	}

	@Override
	public String description() {
		return "Each budget for a month: limit, spent, remaining, percent used, thresholds crossed and the largest "
				+ "transactions behind it. A null categoryName is the overall budget.";
	}

	@Override
	public Class<MonthInput> inputType() {
		return MonthInput.class;
	}

	@Override
	public String statusLabel() {
		return "Checking your budgets";
	}

	@Override
	public ToolResult execute(MonthInput input, ToolContext context) {
		YearMonth month = input.resolve(context);
		BudgetStatusReport report = budgets.getStatus(context.userId(), month);
		List<BudgetRow> rows = report.budgets()
			.stream()
			.map(b -> new BudgetRow(b.budgetId(), b.categoryId(), b.categoryName(), b.limit(), b.spent(), b.remaining(),
					b.pctUsed(), b.overspent(), b.thresholdsCrossed(),
					b.topTransactions().stream().map(ToolTransactionRow::of).toList()))
			.toList();
		List<ProposedAction> actions = report.budgets().stream().filter(BudgetStatus::overspent).map(b -> {
			Money suggested = Tools.roundUpTo500(b.spent());
			String name = b.categoryName() == null ? "overall" : Tools.text(b.categoryName());
			return new ProposedAction(ActionType.UPDATE_BUDGET,
					Map.of("budgetId", b.budgetId().toString(), "categoryName", name, "limit", Tools.wire(suggested)),
					"Review the " + name + " budget");
		}).toList();
		return ToolResult
			.ok(new BudgetRows(month, rows, report.coverage()),
					Tools.fromTransactions(month.toString(), report.coverage(), context, clock),
					Tools.defaultedMonth(input, month))
			.withProposedActions(actions);
	}

}
