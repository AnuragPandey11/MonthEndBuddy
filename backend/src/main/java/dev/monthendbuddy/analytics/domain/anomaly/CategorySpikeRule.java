package dev.monthendbuddy.analytics.domain.anomaly;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.monthendbuddy.analytics.RuleId;
import dev.monthendbuddy.analytics.Severity;
import dev.monthendbuddy.analytics.SubjectType;
import dev.monthendbuddy.common.Money;

/**
 * A category's spending this month exceeds (1 + x) × its baseline (mean of the previous 3 months with data) and by
 * at least ₹2,000, so small categories do not fire on tiny changes. A category with no spending in the baseline counts
 * as a spike once it passes the minimum increase.
 */
@Component
public class CategorySpikeRule implements AnomalyRule {

	@Override
	public RuleId id() {
		return RuleId.CATEGORY_SPIKE;
	}

	@Override
	public Optional<AnomalyFinding> evaluate(AnomalyContext c) {
		if (c.categoryId() == null || c.categoryBaseline() == null || c.baselineMonths() < 1) {
			return Optional.empty();
		}
		Money month = c.categoryMonthTotal();
		Money baseline = c.categoryBaseline();
		BigDecimal factor = BigDecimal.ONE.add(BigDecimal.valueOf(c.settings().categorySpikePct())
			.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_EVEN));
		Money increase = month.subtract(baseline);
		if (month.amount().compareTo(baseline.amount().multiply(factor)) <= 0
				|| !increase.isGreaterThanOrEqual(c.settings().minSpikeIncrease())) {
			return Optional.empty();
		}
		Map<String, Object> explanation = new LinkedHashMap<>();
		explanation.put("category", c.categoryName());
		explanation.put("monthTotal", month.toApiString());
		explanation.put("baseline", baseline.toApiString());
		explanation.put("increase", increase.toApiString());
		explanation.put("pctOver", baseline.isZero() ? null
				: increase.amount().multiply(BigDecimal.valueOf(100)).divide(baseline.amount(), 0, RoundingMode.HALF_EVEN));
		explanation.put("thresholdPct", c.settings().categorySpikePct());
		explanation.put("monthsInBaseline", c.baselineMonths());
		return Optional.of(new AnomalyFinding(id(), SubjectType.CATEGORY, c.categoryId(), c.date().withDayOfMonth(1),
				Severity.INFO, explanation));
	}

}
