package dev.monthendbuddy.analytics;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import dev.monthendbuddy.analytics.application.AnomalyContextFactory;
import dev.monthendbuddy.analytics.domain.anomaly.AlertMessages;
import dev.monthendbuddy.analytics.domain.anomaly.AnomalyContext;
import dev.monthendbuddy.analytics.domain.anomaly.AnomalyFinding;
import dev.monthendbuddy.analytics.domain.anomaly.AnomalyRule;
import dev.monthendbuddy.analytics.events.AnomalyDetected;
import dev.monthendbuddy.analytics.infrastructure.AlertRepository;
import dev.monthendbuddy.analytics.infrastructure.AlertRepository.AlertRow;
import dev.monthendbuddy.common.DemoDataReset;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.Uuids;
import dev.monthendbuddy.common.error.NotFoundException;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.common.events.EventPublisher;
import dev.monthendbuddy.transactions.CategoryQueries;
import dev.monthendbuddy.transactions.TransactionQueries;
import dev.monthendbuddy.transactions.TransactionView;

/**
 * Runs the anomaly rules, stores alerts and lists or resolves them (architecture §9.5). Alerts are deduplicated by
 * (rule, subject, month), so re-running the rules is always safe. Backs {@code /alerts}, the anomaly handler and,
 * from Phase 3, the {@code findUnusualTransactions} tool.
 */
@Service
public class AnomalyService implements DemoDataReset {

	private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
	};

	private final List<AnomalyRule> rules;

	private final AnomalyContextFactory contexts;

	private final AlertRepository alerts;

	private final TransactionQueries transactions;

	private final CategoryQueries categories;

	private final EventPublisher events;

	private final JsonMapper json;

	private final Clock clock;

	public AnomalyService(List<AnomalyRule> rules, AnomalyContextFactory contexts, AlertRepository alerts,
			TransactionQueries transactions, CategoryQueries categories, EventPublisher events, JsonMapper json,
			Clock clock) {
		this.rules = rules;
		this.contexts = contexts;
		this.alerts = alerts;
		this.transactions = transactions;
		this.categories = categories;
		this.events = events;
		this.json = json;
		this.clock = clock;
	}

	/** Evaluates every rule for the given expenses and returns the alerts that are new. */
	@Transactional
	public List<UUID> evaluate(UserId user, List<TransactionView> expenses) {
		List<UUID> created = new ArrayList<>();
		for (AnomalyContext context : contexts.forExpenses(user, expenses)) {
			List<AnomalyFinding> findings = rules.stream()
				.map(rule -> rule.evaluate(context))
				.flatMap(Optional::stream)
				.toList();
			// One explanation per transaction: "4.2× your usual Dining" says more than "above ₹25,000".
			boolean large = findings.stream().anyMatch(f -> f.ruleId() == RuleId.LARGE_VS_CATEGORY);
			for (AnomalyFinding f : findings) {
				if (large && f.ruleId() == RuleId.ABSOLUTE_THRESHOLD) {
					continue;
				}
				alerts.insertIfNew(user, f, json.writeValueAsString(f.explanation())).ifPresent(id -> {
					created.add(id);
					events.publish(new AnomalyDetected(Uuids.v7(), user, id, clock.instant(), f.ruleId().name(),
							f.subjectType().name(), f.subjectId(), f.periodStart(), f.severity().name()));
				});
			}
		}
		return created;
	}

	@Transactional(readOnly = true)
	public List<AlertView> listAlerts(UserId user, AlertStatus status, YearMonth month) {
		return alerts.find(user, status, month == null ? null : month.atDay(1))
			.stream()
			.map(row -> view(user, row))
			.toList();
	}

	@Transactional
	public AlertView resolve(UserId user, UUID alertId, AlertStatus to) {
		if (to == AlertStatus.OPEN) {
			throw ValidationException.of("status", "must be DISMISSED or EXPECTED");
		}
		if (alerts.updateStatus(user, alertId, to) == 0) {
			throw new NotFoundException("Alert");
		}
		return view(user, alerts.findById(user, alertId).orElseThrow());
	}

	/** A transaction was deleted or changed: its open alerts no longer hold. */
	@Transactional
	public void forgetTransaction(UserId user, UUID transactionId) {
		alerts.deleteOpenForTransaction(user, transactionId);
	}

	@Override
	@Transactional
	public void deleteDemoData(UserId demoUser) {
		alerts.deleteAllForDemoReset(demoUser);
	}

	private AlertView view(UserId user, AlertRow row) {
		Map<String, Object> explanation = json.readValue(row.explanationJson(), MAP);
		String label = switch (row.subjectType()) {
			case TRANSACTION -> transactions.findById(user, row.subjectId())
				.map(t -> t.merchant() != null ? t.merchant() : t.description())
				.orElse("a deleted transaction");
			case CATEGORY -> categories.names(user, List.of(row.subjectId())).getOrDefault(row.subjectId(),
					String.valueOf(explanation.get("category")));
		};
		return new AlertView(row.id(), row.ruleId(), row.subjectType(), row.subjectId(), label, row.periodStart(),
				row.severity(), row.status(), explanation,
				AlertMessages.render(row.ruleId(), label, row.periodStart(), explanation), row.createdAt());
	}

}
