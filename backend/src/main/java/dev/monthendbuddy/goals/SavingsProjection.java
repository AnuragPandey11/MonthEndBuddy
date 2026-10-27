package dev.monthendbuddy.goals;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import dev.monthendbuddy.common.Assumption;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.goals.domain.SavingsMath.ProjectionPoint;

/**
 * A what-if savings projection (architecture §9.3). Nothing is persisted. {@code requiredMonthly} is {@code null}
 * when the deadline has passed; {@code projectedCompletion} is {@code null} when the contribution never reaches the
 * target. {@code assumptions} always says what was assumed, so the result is never presented as a guarantee.
 */
public record SavingsProjection(Money target, Money current, Money remaining, LocalDate targetDate,
		int monthsRemaining, Money requiredMonthly, Money monthlyContribution, ProjectionStatus status,
		YearMonth projectedCompletion, List<ProjectionPoint> series, List<Assumption> assumptions) {

}
