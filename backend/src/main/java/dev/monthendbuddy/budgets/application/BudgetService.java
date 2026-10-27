package dev.monthendbuddy.budgets.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.budgets.BudgetCommands;
import dev.monthendbuddy.budgets.BudgetStatusService;
import dev.monthendbuddy.budgets.domain.Budget;
import dev.monthendbuddy.budgets.infrastructure.BudgetRepository;
import dev.monthendbuddy.common.DemoDataReset;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.cache.CacheGenerationService;
import dev.monthendbuddy.common.error.ConflictException;
import dev.monthendbuddy.common.error.NotFoundException;
import dev.monthendbuddy.common.error.UnsupportedCurrencyException;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.identity.UserSettingsQuery;
import dev.monthendbuddy.transactions.CategoryKind;
import dev.monthendbuddy.transactions.CategoryQueries;

/** Budget definitions (CRUD). Status lives in {@link BudgetStatusService}. */
@Service
public class BudgetService implements BudgetCommands, DemoDataReset {

	private final BudgetRepository budgets;

	private final CategoryQueries categories;

	private final UserSettingsQuery settings;

	private final CacheGenerationService cacheGeneration;

	public BudgetService(BudgetRepository budgets, CategoryQueries categories, UserSettingsQuery settings,
			CacheGenerationService cacheGeneration) {
		this.budgets = budgets;
		this.categories = categories;
		this.settings = settings;
		this.cacheGeneration = cacheGeneration;
	}

	@Transactional(readOnly = true)
	public List<BudgetView> list(UserId user) {
		List<Budget> active = budgets.findActive(user);
		Map<UUID, String> names = categories.names(user,
				active.stream().map(Budget::getCategoryId).filter(Objects::nonNull).toList());
		return active.stream().map(b -> view(b, names)).toList();
	}

	/** @throws ConflictException {@code budget_exists} if the overall or that category's budget already exists */
	@Transactional
	public BudgetView create(UserId user, UUID categoryId, Money limit, List<Integer> thresholds) {
		requireBaseCurrency(user, limit);
		String name = null;
		if (categoryId != null) {
			name = categories.findUsable(user, categoryId, CategoryKind.EXPENSE)
				.orElseThrow(() -> ValidationException.of("categoryId", "must be one of your expense categories"))
				.name();
		}
		Budget budget;
		try {
			budget = budgets.saveAndFlush(Budget.create(user, categoryId, limit, thresholds));
		}
		catch (DataIntegrityViolationException e) {
			throw new ConflictException("budget_exists",
					categoryId == null ? "You already have an overall budget" : "This category already has a budget");
		}
		cacheGeneration.bumpAfterCommit(user);
		return view(budget, categoryId == null ? Map.of() : Map.of(categoryId, name));
	}

	@Transactional
	public BudgetView update(UserId user, UUID id, Money limit, List<Integer> thresholds, long expectedVersion) {
		Budget budget = budgets.findByIdAndUserId(id, user).orElseThrow(() -> new NotFoundException("Budget"));
		if (budget.getVersion() != expectedVersion) {
			throw ConflictException.versionConflict();
		}
		requireBaseCurrency(user, limit);
		budget.update(limit, thresholds);
		budgets.saveAndFlush(budget);
		cacheGeneration.bumpAfterCommit(user);
		return view(budget, categories.names(user,
				budget.getCategoryId() == null ? List.of() : List.of(budget.getCategoryId())));
	}

	@Transactional
	public void delete(UserId user, UUID id) {
		if (budgets.deleteByIdAndUserId(id, user) == 0) {
			throw new NotFoundException("Budget");
		}
		cacheGeneration.bumpAfterCommit(user);
	}

	@Override
	@Transactional
	public UUID createBudget(UserId user, UUID categoryId, Money limit, List<Integer> alertThresholds) {
		return create(user, categoryId, limit, alertThresholds).id();
	}

	@Override
	@Transactional
	public void deleteDemoData(UserId demoUser) {
		budgets.deleteAllForDemoReset(demoUser);
	}

	private void requireBaseCurrency(UserId user, Money limit) {
		var base = settings.get(user).baseCurrency();
		if (limit.currency() != base) {
			throw new UnsupportedCurrencyException("limit.currency", base);
		}
	}

	private static BudgetView view(Budget b, Map<UUID, String> names) {
		return new BudgetView(b.getId(), b.getCategoryId(),
				b.isOverall() ? BudgetStatusService.OVERALL : names.get(b.getCategoryId()), b.getLimit(),
				b.getAlertThresholds(), b.getVersion());
	}

	public record BudgetView(UUID id, UUID categoryId, String categoryName, Money limit, List<Integer> alertThresholds,
			long version) {
	}

}
