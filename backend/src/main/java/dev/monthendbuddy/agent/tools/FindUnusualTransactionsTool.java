package dev.monthendbuddy.agent.tools;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.monthendbuddy.analytics.AlertStatus;
import dev.monthendbuddy.analytics.AlertView;
import dev.monthendbuddy.analytics.AnomalyService;
import dev.monthendbuddy.analytics.RuleId;
import dev.monthendbuddy.analytics.Severity;

/**
 * Same service call as {@code GET /api/v1/alerts?status=OPEN&month=…}. Each alert keeps the numbers that triggered
 * its rule, so the model explains the real reason; the subject label is cut like any transaction text.
 */
@Component
class FindUnusualTransactionsTool implements FinanceTool<MonthInput> {

	static final int MAX_ALERTS = 50;

	private final AnomalyService anomalies;

	private final Clock clock;

	FindUnusualTransactionsTool(AnomalyService anomalies, Clock clock) {
		this.anomalies = anomalies;
		this.clock = clock;
	}

	record AlertRow(UUID id, RuleId ruleId, Severity severity, String subject, LocalDate periodStart,
			Map<String, Object> explanation, String message) {
	}

	@Override
	public String name() {
		return "findUnusualTransactions";
	}

	@Override
	public String description() {
		return "Open anomaly alerts for a month: unusually large transactions and categories spiking above their "
				+ "usual level, each with the rule and the numbers that triggered it.";
	}

	@Override
	public Class<MonthInput> inputType() {
		return MonthInput.class;
	}

	@Override
	public String statusLabel() {
		return "Looking for unusual spending";
	}

	@Override
	public ToolResult execute(MonthInput input, ToolContext context) {
		YearMonth month = input.resolve(context);
		List<AlertView> alerts = anomalies.listAlerts(context.userId(), AlertStatus.OPEN, month);
		List<AlertRow> rows = alerts.stream()
			.limit(MAX_ALERTS)
			.map(a -> new AlertRow(a.id(), a.ruleId(), a.severity(), Tools.text(a.subjectLabel()), a.periodStart(),
					a.explanation(), Tools.text(a.message())))
			.toList();
		return ToolResult.ok(rows, new Provenance("anomaly alerts", month.toString(), context.currency(),
				clock.instant(), null), Tools.defaultedMonth(input, month));
	}

}
