package dev.monthendbuddy.analytics.domain.anomaly;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import dev.monthendbuddy.analytics.RuleId;
import dev.monthendbuddy.analytics.Severity;
import dev.monthendbuddy.analytics.SubjectType;

/** A rule fired; {@code explanation} holds the numbers behind it, as strings and integers. */
public record AnomalyFinding(RuleId ruleId, SubjectType subjectType, UUID subjectId, LocalDate periodStart,
		Severity severity, Map<String, Object> explanation) {

}
