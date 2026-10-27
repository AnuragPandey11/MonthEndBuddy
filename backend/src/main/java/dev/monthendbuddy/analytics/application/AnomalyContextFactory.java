package dev.monthendbuddy.analytics.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.analytics.domain.anomaly.AnomalyContext;
import dev.monthendbuddy.analytics.domain.anomaly.LargeVsCategoryRule;
import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.Period;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.identity.UserSettingsQuery;
import dev.monthendbuddy.identity.UserSettingsView;
import dev.monthendbuddy.transactions.TransactionAggregations;
import dev.monthendbuddy.transactions.TransactionAggregations.DatedAmount;
import dev.monthendbuddy.transactions.TransactionView;

/**
 * Builds rule contexts for many expenses at once (LLD §7.2): one history query per category, not per expense, so an
 * import of thousands of rows stays cheap. Baselines count only months that have any transactions, because missing
 * data is not zero spending (architecture §6.3).
 */
@Component
public class AnomalyContextFactory {

	/** Minimum increase for a category spike (architecture §9.5). */
	public static final Money MIN_SPIKE_INCREASE = Money.of("2000", CurrencyCode.INR);

	public static final int BASELINE_MONTHS = 3;

	private final TransactionAggregations aggregations;

	private final UserSettingsQuery settings;

	private final Clock clock;

	public AnomalyContextFactory(TransactionAggregations aggregations, UserSettingsQuery settings, Clock clock) {
		this.aggregations = aggregations;
		this.settings = settings;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<AnomalyContext> forExpenses(UserId user, List<TransactionView> expenses) {
		if (expenses.isEmpty()) {
			return List.of();
		}
		UserSettingsView s = settings.get(user);
		CurrencyCode currency = s.baseCurrency();
		var ruleSettings = new AnomalyContext.AnomalySettings(s.anomalyMultiplier(), s.categorySpikePct(),
				s.largeTxnThreshold(), MIN_SPIKE_INCREASE);

		LocalDate earliest = expenses.stream()
			.map(t -> earlierOf(t.transactionDate().minusDays(LargeVsCategoryRule.WINDOW_DAYS),
					YearMonth.from(t.transactionDate()).minusMonths(BASELINE_MONTHS).atDay(1)))
			.min(Comparator.naturalOrder())
			.orElseThrow();
		LocalDate latest = expenses.stream()
			.map(t -> YearMonth.from(t.transactionDate()).atEndOfMonth())
			.max(Comparator.naturalOrder())
			.orElseThrow();
		Period span = new Period(earliest, latest);
		Set<YearMonth> monthsWithData = new HashSet<>(aggregations.coverage(user, span).monthsWithData());

		Map<UUID, List<DatedAmount>> history = expenses.stream()
			.map(t -> t.category() == null ? null : t.category().id())
			.filter(Objects::nonNull)
			.distinct()
			.collect(Collectors.toMap(id -> id, id -> aggregations.categoryExpenses(user, id, span)));

		List<AnomalyContext> contexts = new ArrayList<>(expenses.size());
		for (TransactionView t : expenses) {
			UUID categoryId = t.category() == null ? null : t.category().id();
			String label = t.merchant() != null ? t.merchant() : t.description();
			if (categoryId == null) {
				contexts.add(new AnomalyContext(t.id(), t.transactionDate(), t.amount(), null, null, label, List.of(),
						Money.zero(currency), null, 0, ruleSettings));
				continue;
			}
			List<DatedAmount> rows = history.get(categoryId);
			LocalDate windowStart = t.transactionDate().minusDays(LargeVsCategoryRule.WINDOW_DAYS);
			List<AnomalyContext.WindowExpense> window = rows.stream()
				.filter(r -> !r.transactionId().equals(t.id()) && !r.date().isBefore(windowStart)
						&& !r.date().isAfter(t.transactionDate()))
				.map(r -> new AnomalyContext.WindowExpense(r.amount(), r.label()))
				.toList();
			YearMonth month = YearMonth.from(t.transactionDate());
			Money monthTotal = new Money(sumIn(rows, month), currency);
			List<YearMonth> baselineMonths = new ArrayList<>();
			for (int i = 1; i <= BASELINE_MONTHS; i++) {
				if (monthsWithData.contains(month.minusMonths(i))) {
					baselineMonths.add(month.minusMonths(i));
				}
			}
			Money baseline = null;
			if (!baselineMonths.isEmpty()) {
				BigDecimal total = baselineMonths.stream().map(m -> sumIn(rows, m)).reduce(BigDecimal.ZERO, BigDecimal::add);
				baseline = new Money(total.divide(BigDecimal.valueOf(baselineMonths.size()), Money.STORAGE_SCALE,
						RoundingMode.HALF_EVEN), currency);
			}
			contexts.add(new AnomalyContext(t.id(), t.transactionDate(), t.amount(), categoryId, t.category().name(),
					label, window, monthTotal, baseline, baselineMonths.size(), ruleSettings));
		}
		return contexts;
	}

	/** Expenses older than this are not evaluated: an alert about last year's spending is not useful. */
	public LocalDate evaluationStart(UserId user) {
		return settings.get(user).today(clock).minusMonths(12);
	}

	private static BigDecimal sumIn(List<DatedAmount> rows, YearMonth month) {
		return rows.stream()
			.filter(r -> YearMonth.from(r.date()).equals(month))
			.map(DatedAmount::amount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static LocalDate earlierOf(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}

}
