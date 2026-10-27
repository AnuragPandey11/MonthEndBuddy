package dev.monthendbuddy.agent.tools;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import dev.monthendbuddy.agent.tools.ProposedAction.ActionType;
import dev.monthendbuddy.analytics.CategoryBreakdown;
import dev.monthendbuddy.analytics.SummaryService;
import dev.monthendbuddy.budgets.BudgetStatus;
import dev.monthendbuddy.budgets.BudgetStatusService;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.Period;

/**
 * Same service call as {@code GET /api/v1/analytics/categories}. When the largest discretionary category has no
 * budget, the result proposes one ({@code CREATE_BUDGET}) at its monthly average, which the user can confirm or
 * change.
 */
@Component
class GetExpensesByCategoryTool implements FinanceTool<GetExpensesByCategoryTool.DateRangeInput> {

	/** System categories that are fixed commitments, where a budget does not help: Rent, Utilities, Insurance, EMI. */
	static final Set<UUID> FIXED_COSTS = Set.of(UUID.fromString("0192f000-0000-7000-8000-000000000001"),
			UUID.fromString("0192f000-0000-7000-8000-000000000005"),
			UUID.fromString("0192f000-0000-7000-8000-00000000000d"),
			UUID.fromString("0192f000-0000-7000-8000-00000000000e"));

	private final SummaryService summaries;

	private final BudgetStatusService budgets;

	private final Clock clock;

	GetExpensesByCategoryTool(SummaryService summaries, BudgetStatusService budgets, Clock clock) {
		this.summaries = summaries;
		this.budgets = budgets;
		this.clock = clock;
	}

	/** An inclusive date range of at most 24 months. */
	record DateRangeInput(
			@JsonPropertyDescription("First day, inclusive, as YYYY-MM-DD") @NotNull LocalDate startDate,
			@JsonPropertyDescription("Last day, inclusive, as YYYY-MM-DD; at most 24 months after startDate") @NotNull LocalDate endDate) {
	}

	@Override
	public String name() {
		return "getExpensesByCategory";
	}

	@Override
	public String description() {
		return "Expenses per category in a date range, largest first, with each category's amount, share of the "
				+ "total and transaction count. Use for 'where did my money go' questions.";
	}

	@Override
	public Class<DateRangeInput> inputType() {
		return DateRangeInput.class;
	}

	@Override
	public String statusLabel() {
		return "Breaking down your spending by category";
	}

	@Override
	public ToolResult execute(DateRangeInput input, ToolContext context) {
		Period period = new Period(input.startDate(), input.endDate());
		CategoryBreakdown breakdown = summaries.categoryBreakdown(context.userId(), period);
		return ToolResult
			.ok(breakdown, Tools.fromTransactions(period.startInclusive() + ".." + period.endInclusive(),
					breakdown.coverage(), context, clock), null)
			.withProposedActions(budgetForTopUnbudgetedCategory(breakdown, period, context));
	}

	private List<ProposedAction> budgetForTopUnbudgetedCategory(CategoryBreakdown breakdown, Period period,
			ToolContext context) {
		Set<UUID> budgeted = budgets.statusWithoutEvidence(context.userId(), context.currentMonth())
			.budgets()
			.stream()
			.map(BudgetStatus::categoryId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		return breakdown.categories()
			.stream()
			.filter(c -> c.categoryId() != null && c.amount().isPositive() && !budgeted.contains(c.categoryId())
					&& !FIXED_COSTS.contains(c.categoryId()))
			.findFirst()
			.map(c -> {
				Money monthly = Tools.roundUpTo500(c.amount().divide(BigDecimal.valueOf(period.lengthInMonths())));
				String name = Tools.text(c.name());
				return List.of(new ProposedAction(ActionType.CREATE_BUDGET, Map.of("categoryId",
						c.categoryId().toString(), "categoryName", name, "limit", Tools.wire(monthly)),
						"Set a monthly budget for " + name));
			})
			.orElse(List.of());
	}

}
