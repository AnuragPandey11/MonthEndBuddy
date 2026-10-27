package dev.monthendbuddy.analytics;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.analytics.domain.CashFlowMath;
import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.Period;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.cache.UserScopedCache;
import dev.monthendbuddy.identity.UserSettingsQuery;
import dev.monthendbuddy.identity.UserSettingsView;
import dev.monthendbuddy.transactions.TransactionAggregations;
import dev.monthendbuddy.transactions.TransactionAggregations.CategoryTotal;
import dev.monthendbuddy.transactions.TransactionType;

/** Month-over-month expense comparison, in total and per category (architecture §6.3). */
@Service
@Transactional(readOnly = true)
public class ComparisonService {

	private final TransactionAggregations aggregations;

	private final UserSettingsQuery settings;

	private final UserScopedCache cache;

	private final Clock clock;

	public ComparisonService(TransactionAggregations aggregations, UserSettingsQuery settings, UserScopedCache cache,
			Clock clock) {
		this.aggregations = aggregations;
		this.settings = settings;
		this.cache = cache;
		this.clock = clock;
	}

	public PeriodComparison compare(UserId user, YearMonth current, YearMonth previous) {
		UserSettingsView userSettings = settings.get(user);
		Period currentPeriod = Period.ofMonth(current).requireNotInFuture(userSettings.today(clock));
		Period previousPeriod = Period.ofMonth(previous).requireNotInFuture(userSettings.today(clock));
		return cache.getOrLoad(user, "compare", current + ":" + previous, PeriodComparison.class,
				() -> load(user, userSettings.baseCurrency(), current, previous, currentPeriod, previousPeriod));
	}

	private PeriodComparison load(UserId user, CurrencyCode currency, YearMonth current, YearMonth previous,
			Period currentPeriod, Period previousPeriod) {
		Map<String, Row> rows = new LinkedHashMap<>();
		for (CategoryTotal t : aggregations.totalsByCategory(user, currentPeriod, TransactionType.EXPENSE)) {
			rows.computeIfAbsent(key(t), k -> new Row(t.categoryId(), name(t), currency)).current = money(t, currency);
		}
		for (CategoryTotal t : aggregations.totalsByCategory(user, previousPeriod, TransactionType.EXPENSE)) {
			rows.computeIfAbsent(key(t), k -> new Row(t.categoryId(), name(t), currency)).previous = money(t, currency);
		}
		List<PeriodComparison.CategoryChange> categories = new ArrayList<>();
		Money currentTotal = Money.zero(currency);
		Money previousTotal = Money.zero(currency);
		for (Row row : rows.values()) {
			CashFlowMath.Delta delta = CashFlowMath.delta(row.current, row.previous);
			categories.add(new PeriodComparison.CategoryChange(row.categoryId, row.name, row.current, row.previous,
					delta.delta(), delta.deltaPct(), delta.isNew()));
			currentTotal = currentTotal.add(row.current);
			previousTotal = previousTotal.add(row.previous);
		}
		categories.sort(Comparator.comparing(PeriodComparison.CategoryChange::delta).reversed()
			.thenComparing(PeriodComparison.CategoryChange::name));
		CashFlowMath.Delta total = CashFlowMath.delta(currentTotal, previousTotal);
		return new PeriodComparison(current, previous,
				new PeriodComparison.Change(currentTotal, previousTotal, total.delta(), total.deltaPct(),
						total.isNew()),
				categories, aggregations.coverage(user, currentPeriod), aggregations.coverage(user, previousPeriod));
	}

	private static String key(CategoryTotal t) {
		return Objects.toString(t.categoryId(), "uncategorised");
	}

	private static String name(CategoryTotal t) {
		return t.categoryName() == null ? SummaryService.UNCATEGORISED : t.categoryName();
	}

	private static Money money(CategoryTotal t, CurrencyCode currency) {
		return new Money(t.amount(), currency);
	}

	private static final class Row {

		final UUID categoryId;

		final String name;

		Money current;

		Money previous;

		Row(UUID categoryId, String name, CurrencyCode currency) {
			this.categoryId = categoryId;
			this.name = name;
			this.current = Money.zero(currency);
			this.previous = Money.zero(currency);
		}

	}

}
