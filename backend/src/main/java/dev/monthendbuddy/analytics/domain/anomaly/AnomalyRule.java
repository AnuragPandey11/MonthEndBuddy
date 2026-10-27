package dev.monthendbuddy.analytics.domain.anomaly;

import java.util.Optional;

import dev.monthendbuddy.analytics.RuleId;

/**
 * One explainable anomaly rule (Strategy, LLD §7.2): a pure function of the context. Adding a rule means adding a
 * {@code @Component} that implements this; {@code AnomalyService} receives every implementation.
 */
public interface AnomalyRule {

	RuleId id();

	Optional<AnomalyFinding> evaluate(AnomalyContext context);

}
