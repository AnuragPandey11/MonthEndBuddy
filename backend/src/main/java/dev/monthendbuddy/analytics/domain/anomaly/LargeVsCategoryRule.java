package dev.monthendbuddy.analytics.domain.anomaly;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.monthendbuddy.analytics.RuleId;
import dev.monthendbuddy.analytics.Severity;
import dev.monthendbuddy.analytics.SubjectType;

/**
 * An expense more than k × the median of its category over the trailing 90 days, with at least 5 samples. Routine
 * payments (the same merchant paid a similar amount in the window) are exempt, so mixed categories such as Transport
 * (cab rides and fuel) or Subscriptions do not alert every month.
 */
@Component
public class LargeVsCategoryRule implements AnomalyRule {

	public static final int MIN_SAMPLES = 5;

	public static final int WINDOW_DAYS = 90;

	@Override
	public RuleId id() {
		return RuleId.LARGE_VS_CATEGORY;
	}

	@Override
	public Optional<AnomalyFinding> evaluate(AnomalyContext c) {
		List<BigDecimal> window = c.categoryWindowAmounts();
		if (c.categoryId() == null || window.size() < MIN_SAMPLES || c.isRoutine()) {
			return Optional.empty();
		}
		BigDecimal median = median(window);
		BigDecimal limit = median.multiply(c.settings().multiplier());
		if (median.signum() <= 0 || c.amount().amount().compareTo(limit) <= 0) {
			return Optional.empty();
		}
		Map<String, Object> explanation = new LinkedHashMap<>();
		explanation.put("amount", c.amount().toApiString());
		explanation.put("median", median.setScale(2, RoundingMode.HALF_EVEN).toPlainString());
		explanation.put("ratio", c.amount().amount().divide(median, 1, RoundingMode.HALF_EVEN).toPlainString());
		explanation.put("multiplier", c.settings().multiplier().stripTrailingZeros().toPlainString());
		explanation.put("sampleSize", window.size());
		explanation.put("windowDays", WINDOW_DAYS);
		explanation.put("category", c.categoryName());
		return Optional.of(new AnomalyFinding(id(), SubjectType.TRANSACTION, c.transactionId(),
				c.date().withDayOfMonth(1), Severity.WARNING, explanation));
	}

	static BigDecimal median(List<BigDecimal> values) {
		List<BigDecimal> sorted = values.stream().sorted().toList();
		int n = sorted.size();
		return n % 2 == 1 ? sorted.get(n / 2)
				: sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.TWO, 4, RoundingMode.HALF_EVEN);
	}

}
