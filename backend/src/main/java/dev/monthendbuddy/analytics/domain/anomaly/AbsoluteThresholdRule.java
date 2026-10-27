package dev.monthendbuddy.analytics.domain.anomaly;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.monthendbuddy.analytics.RuleId;
import dev.monthendbuddy.analytics.Severity;
import dev.monthendbuddy.analytics.SubjectType;

/**
 * Any single expense at or above the user's large-transaction threshold (₹25,000 by default), unless it is a routine
 * payment such as monthly rent of that size.
 */
@Component
public class AbsoluteThresholdRule implements AnomalyRule {

	@Override
	public RuleId id() {
		return RuleId.ABSOLUTE_THRESHOLD;
	}

	@Override
	public Optional<AnomalyFinding> evaluate(AnomalyContext c) {
		if (!c.amount().isGreaterThanOrEqual(c.settings().largeTxnThreshold()) || c.isRoutine()) {
			return Optional.empty();
		}
		Map<String, Object> explanation = new LinkedHashMap<>();
		explanation.put("amount", c.amount().toApiString());
		explanation.put("threshold", c.settings().largeTxnThreshold().toApiString());
		return Optional.of(new AnomalyFinding(id(), SubjectType.TRANSACTION, c.transactionId(),
				c.date().withDayOfMonth(1), Severity.WARNING, explanation));
	}

}
