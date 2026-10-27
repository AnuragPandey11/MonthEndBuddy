package dev.monthendbuddy.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * An anomaly alert as shown to the user (LLD §11.3). {@code message} is rendered only from {@code explanation}, the
 * numbers that triggered the rule, so the reason shown is always the real one (architecture §9.5).
 */
public record AlertView(UUID id, RuleId ruleId, SubjectType subjectType, UUID subjectId, String subjectLabel,
		LocalDate periodStart, Severity severity, AlertStatus status, Map<String, Object> explanation, String message,
		Instant createdAt) {

}
