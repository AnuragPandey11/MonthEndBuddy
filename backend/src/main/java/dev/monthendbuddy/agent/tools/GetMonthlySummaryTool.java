package dev.monthendbuddy.agent.tools;

import java.time.Clock;
import java.time.YearMonth;

import org.springframework.stereotype.Component;

import dev.monthendbuddy.analytics.MonthlySummary;
import dev.monthendbuddy.analytics.SummaryService;

/** Same service call as {@code GET /api/v1/analytics/summary}. */
@Component
class GetMonthlySummaryTool implements FinanceTool<MonthInput> {

	private final SummaryService summaries;

	private final Clock clock;

	GetMonthlySummaryTool(SummaryService summaries, Clock clock) {
		this.summaries = summaries;
		this.clock = clock;
	}

	@Override
	public String name() {
		return "getMonthlySummary";
	}

	@Override
	public String description() {
		return "Income, expenses, net cash flow and savings rate for one month, with the number of transactions "
				+ "recorded. Use for questions about how a month went overall.";
	}

	@Override
	public Class<MonthInput> inputType() {
		return MonthInput.class;
	}

	@Override
	public String statusLabel() {
		return "Reading your monthly summary";
	}

	@Override
	public ToolResult execute(MonthInput input, ToolContext context) {
		YearMonth month = input.resolve(context);
		MonthlySummary summary = summaries.monthlySummary(context.userId(), month);
		return ToolResult.ok(summary, Tools.fromTransactions(month.toString(), summary.coverage(), context, clock),
				Tools.defaultedMonth(input, month));
	}

}
