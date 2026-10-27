package dev.monthendbuddy.budgets;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.budgets.domain.Budget;
import dev.monthendbuddy.budgets.domain.BudgetMath;
import dev.monthendbuddy.budgets.infrastructure.BudgetRepository;
import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.Period;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.identity.UserSettingsQuery;
import dev.monthendbuddy.identity.UserSettingsView;
import dev.monthendbuddy.transactions.CategoryQueries;
import dev.monthendbuddy.transactions.TransactionAggregations;
import dev.monthendbuddy.transactions.TransactionAggregations.CategoryTotal;
import dev.monthendbuddy.transactions.TransactionQueries;
import dev.monthendbuddy.transactions.TransactionType;

/**
 * Spent, remaining and percentage used for each active budget in a month (architecture §9.2). Backs
 * {@code GET /budgets/status}, the threshold handler and, from Phase 3, the {@code getBudgetStatus} tool.
 */
@Service
@Transactional(readOnly = true)
public class BudgetStatusService {

	public static final String OVERALL = "Overall";

	private static final int TOP_TRANSACTIONS = 5;

	private final BudgetRepository budgets;

	private final TransactionAggregations aggregations;

	private final TransactionQueries queries;

	private final CategoryQueries categories;

	private final UserSettingsQuery settings;

	private final Clock clock;

	public BudgetStatusService(BudgetRepository budgets, TransactionAggregations aggregations,
			TransactionQueries queries, CategoryQueries categories, UserSettingsQuery settings, Clock clock) {
		this.budgets = budgets;
		this.aggregations = aggregations;
		this.queries = queries;
		this.categories = categories;
		this.settings = settings;
		this.clock = clock;
	}

	public BudgetStatusReport getStatus(UserId user, YearMonth month) {
		return evaluate(user, month, true);
	}

	/** The same figures without the top transactions; used by the threshold handler after every write. */
	public BudgetStatusReport statusWithoutEvidence(UserId user, YearMonth month) {
		return evaluate(user, month, false);
	}

	private BudgetStatusReport evaluate(UserId user, YearMonth month, boolean withEvidence) {
		UserSettingsView userSettings = settings.get(user);
		Period period = Period.ofMonth(month).requireNotInFuture(userSettings.today(clock));
		CurrencyCode currency = userSettings.baseCurrency();
		List<Budget> active = budgets.findActive(user);
		Map<UUID, BigDecimal> byCategory = aggregations.totalsByCategory(user, period, TransactionType.EXPENSE)
			.stream()
			.filter(t -> t.categoryId() != null)
			.collect(Collectors.toMap(CategoryTotal::categoryId, CategoryTotal::amount));
		Money totalExpenses = new Money(aggregations.totalsByType(user, period).expenses(), currency);
		Map<UUID, String> names = categories.names(user,
				active.stream().map(Budget::getCategoryId).filter(Objects::nonNull).toList());

		List<BudgetStatus> statuses = active.stream().map(budget -> {
			Money spent = budget.isOverall() ? totalExpenses
					: new Money(byCategory.getOrDefault(budget.getCategoryId(), BigDecimal.ZERO), currency);
			Money remaining = BudgetMath.remaining(budget.getLimit(), spent);
			BigDecimal pct = BudgetMath.pctUsed(budget.getLimit(), spent);
			return new BudgetStatus(budget.getId(), budget.getCategoryId(),
					budget.isOverall() ? OVERALL : names.get(budget.getCategoryId()), budget.getLimit(), spent,
					remaining, pct, remaining.isNegative(), budget.getAlertThresholds(),
					BudgetMath.crossedThresholds(budget.getAlertThresholds(), pct),
					withEvidence ? queries.topExpenses(user, period, budget.getCategoryId(), TOP_TRANSACTIONS)
							: List.of());
		}).toList();
		return new BudgetStatusReport(month, statuses, aggregations.coverage(user, period));
	}

}
