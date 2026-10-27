package dev.monthendbuddy.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.analytics.domain.CashFlowMath;
import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.Period;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.cache.UserScopedCache;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.identity.UserSettingsQuery;
import dev.monthendbuddy.identity.UserSettingsView;
import dev.monthendbuddy.transactions.TransactionAggregations;
import dev.monthendbuddy.transactions.TransactionAggregations.DataCoverage;
import dev.monthendbuddy.transactions.TransactionAggregations.MonthTotal;
import dev.monthendbuddy.transactions.TransactionAggregations.TypeTotals;
import dev.monthendbuddy.transactions.TransactionType;

/**
 * Monthly summary, category breakdown, trends and averages (architecture §9.1). The same methods back the REST
 * endpoints and, from Phase 3, the agent's tools, so the dashboard and the copilot cannot disagree. Month-aligned
 * results go through the user-scoped cache.
 */
@Service
@Transactional(readOnly = true)
public class SummaryService {

	public static final String UNCATEGORISED = "Uncategorised";

	private final TransactionAggregations aggregations;

	private final UserSettingsQuery settings;

	private final UserScopedCache cache;

	private final Clock clock;

	public SummaryService(TransactionAggregations aggregations, UserSettingsQuery settings, UserScopedCache cache,
			Clock clock) {
		this.aggregations = aggregations;
		this.settings = settings;
		this.cache = cache;
		this.clock = clock;
	}

	public MonthlySummary monthlySummary(UserId user, YearMonth month) {
		UserSettingsView userSettings = settings.get(user);
		Period period = Period.ofMonth(month).requireNotInFuture(userSettings.today(clock));
		return cache.getOrLoad(user, "summary", month.toString(), MonthlySummary.class, () -> {
			CurrencyCode currency = userSettings.baseCurrency();
			TypeTotals totals = aggregations.totalsByType(user, period);
			Money income = new Money(totals.income(), currency);
			Money expenses = new Money(totals.expenses(), currency);
			BigDecimal rate = CashFlowMath.savingsRate(income, expenses).orElse(null);
			return new MonthlySummary(month, income, expenses, CashFlowMath.net(income, expenses), rate,
					rate == null ? "NO_INCOME" : null, aggregations.coverage(user, period));
		});
	}

	public CategoryBreakdown categoryBreakdown(UserId user, Period period) {
		UserSettingsView userSettings = settings.get(user);
		period.requireNotInFuture(userSettings.today(clock));
		YearMonth month = YearMonth.from(period.startInclusive());
		if (period.equals(Period.ofMonth(month))) {
			return cache.getOrLoad(user, "categories", month.toString(), CategoryBreakdown.class,
					() -> loadBreakdown(user, period, userSettings.baseCurrency()));
		}
		return loadBreakdown(user, period, userSettings.baseCurrency());
	}

	private CategoryBreakdown loadBreakdown(UserId user, Period period, CurrencyCode currency) {
		List<TransactionAggregations.CategoryTotal> rows = aggregations.totalsByCategory(user, period,
				TransactionType.EXPENSE);
		Money total = rows.stream()
			.map(r -> new Money(r.amount(), currency))
			.reduce(Money.zero(currency), Money::add);
		List<CategoryBreakdown.CategoryShare> shares = rows.stream().map(r -> {
			Money amount = new Money(r.amount(), currency);
			return new CategoryBreakdown.CategoryShare(r.categoryId(),
					r.categoryName() == null ? UNCATEGORISED : r.categoryName(), amount,
					CashFlowMath.share(amount, total), r.count());
		}).toList();
		return new CategoryBreakdown(period.startInclusive(), period.endInclusive(), total, shares,
				aggregations.coverage(user, period));
	}

	/** {@code months} calendar months ending with the current one (which may be partial). */
	public TrendSeries trends(UserId user, int months) {
		if (months < 1 || months > Period.MAX_MONTHS) {
			throw ValidationException.of("months", "must be between 1 and " + Period.MAX_MONTHS);
		}
		UserSettingsView userSettings = settings.get(user);
		YearMonth current = userSettings.currentMonth(clock);
		YearMonth first = current.minusMonths(months - 1L);
		return cache.getOrLoad(user, "trend", first + ":" + current, TrendSeries.class, () -> {
			CurrencyCode currency = userSettings.baseCurrency();
			Map<YearMonth, MonthTotal> byMonth = aggregations
				.monthlyTotals(user, new Period(first.atDay(1), current.atEndOfMonth()))
				.stream()
				.collect(Collectors.toMap(MonthTotal::month, Function.identity()));
			List<TrendSeries.TrendPoint> points = new ArrayList<>();
			for (YearMonth m = first; !m.isAfter(current); m = m.plusMonths(1)) {
				MonthTotal total = byMonth.get(m);
				Money income = new Money(total == null ? BigDecimal.ZERO : total.income(), currency);
				Money expenses = new Money(total == null ? BigDecimal.ZERO : total.expenses(), currency);
				points.add(new TrendSeries.TrendPoint(m, income, expenses, CashFlowMath.net(income, expenses),
						total != null));
			}
			return new TrendSeries(points);
		});
	}

	/**
	 * Averages over the last {@code completeMonths} complete months, counting only months that have transactions
	 * ("missing data is not zero spending"). Used for affordability (architecture §9.4).
	 */
	public MonthlyAverages averages(UserId user, int completeMonths) {
		UserSettingsView userSettings = settings.get(user);
		CurrencyCode currency = userSettings.baseCurrency();
		LocalDate today = userSettings.today(clock);
		List<MonthTotal> months = aggregations.monthlyTotals(user, Period.lastCompleteMonths(completeMonths, today));
		if (months.isEmpty()) {
			return new MonthlyAverages(null, null, 0, completeMonths, true);
		}
		BigDecimal count = BigDecimal.valueOf(months.size());
		BigDecimal income = months.stream().map(MonthTotal::income).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal expenses = months.stream().map(MonthTotal::expenses).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new MonthlyAverages(new Money(income.divide(count, Money.STORAGE_SCALE, RoundingMode.HALF_EVEN), currency),
				new Money(expenses.divide(count, Money.STORAGE_SCALE, RoundingMode.HALF_EVEN), currency), months.size(),
				completeMonths, months.size() < completeMonths);
	}

	/** Coverage passthrough for callers that need to tell "no data" from "zero". */
	public DataCoverage coverage(UserId user, Period period) {
		return aggregations.coverage(user, period);
	}

}
