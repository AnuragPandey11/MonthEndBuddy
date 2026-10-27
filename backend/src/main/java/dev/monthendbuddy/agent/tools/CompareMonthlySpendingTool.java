package dev.monthendbuddy.agent.tools;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import dev.monthendbuddy.analytics.ComparisonService;
import dev.monthendbuddy.analytics.PeriodComparison;
import dev.monthendbuddy.common.Assumption;
import dev.monthendbuddy.common.AssumptionLabel;

/** Same service call as {@code GET /api/v1/analytics/compare}. */
@Component
class CompareMonthlySpendingTool implements FinanceTool<CompareMonthlySpendingTool.CompareInput> {

	private final ComparisonService comparisons;

	private final Clock clock;

	CompareMonthlySpendingTool(ComparisonService comparisons, Clock clock) {
		this.comparisons = comparisons;
		this.clock = clock;
	}

	record CompareInput(
			@JsonProperty(required = false) @JsonPropertyDescription("Month as YYYY-MM. Omit for the current month.")
			@Pattern(regexp = MONTH, message = "must be a month in YYYY-MM format") String currentMonth,
			@JsonProperty(required = false) @JsonPropertyDescription("Month to compare against, as YYYY-MM. Omit for the month before currentMonth.")
			@Pattern(regexp = MONTH, message = "must be a month in YYYY-MM format") String previousMonth) {
	}

	static final String MONTH = "\\d{4}-(0[1-9]|1[0-2])";

	@Override
	public String name() {
		return "compareMonthlySpending";
	}

	@Override
	public String description() {
		return "Compares spending between two months: totals and, per category, both amounts, the change and the "
				+ "change in percent, and whether the category is new. Use for 'more or less than last month' questions.";
	}

	@Override
	public Class<CompareInput> inputType() {
		return CompareInput.class;
	}

	@Override
	public String statusLabel() {
		return "Comparing months";
	}

	@Override
	public ToolResult execute(CompareInput input, ToolContext context) {
		List<Assumption> assumptions = new ArrayList<>();
		YearMonth current = input.currentMonth() == null ? context.currentMonth() : YearMonth.parse(input.currentMonth());
		if (input.currentMonth() == null) {
			assumptions.add(new Assumption("currentMonth", current + " (current month)", AssumptionLabel.DEFAULT_ASSUMPTION));
		}
		YearMonth previous = input.previousMonth() == null ? current.minusMonths(1) : YearMonth.parse(input.previousMonth());
		PeriodComparison comparison = comparisons.compare(context.userId(), current, previous);
		return ToolResult.ok(comparison, new Provenance(Tools.TRANSACTIONS, previous + " vs " + current,
				context.currency(), clock.instant(), null), assumptions);
	}

}
