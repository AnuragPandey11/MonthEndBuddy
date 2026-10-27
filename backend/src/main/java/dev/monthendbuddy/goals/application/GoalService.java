package dev.monthendbuddy.goals.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.common.DemoDataReset;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.cache.CacheGenerationService;
import dev.monthendbuddy.common.error.ConflictException;
import dev.monthendbuddy.common.error.NotFoundException;
import dev.monthendbuddy.common.error.UnsupportedCurrencyException;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.goals.GoalCommands;
import dev.monthendbuddy.goals.GoalStatus;
import dev.monthendbuddy.goals.domain.SavingsGoal;
import dev.monthendbuddy.goals.infrastructure.GoalRepository;
import dev.monthendbuddy.identity.UserSettingsQuery;
import dev.monthendbuddy.identity.UserSettingsView;

/** Savings goal CRUD. Projections live in {@code SavingsProjectionService}. */
@Service
public class GoalService implements GoalCommands, DemoDataReset {

	private final GoalRepository goals;

	private final UserSettingsQuery settings;

	private final CacheGenerationService cacheGeneration;

	private final Clock clock;

	public GoalService(GoalRepository goals, UserSettingsQuery settings, CacheGenerationService cacheGeneration,
			Clock clock) {
		this.goals = goals;
		this.settings = settings;
		this.cacheGeneration = cacheGeneration;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<GoalView> list(UserId user) {
		return goals.findCurrent(user).stream().map(GoalService::view).toList();
	}

	@Transactional
	public GoalView create(UserId user, Input input) {
		validate(user, input);
		SavingsGoal goal = goals.saveAndFlush(SavingsGoal.create(user, input.name(), input.target(), input.current(),
				input.targetDate(), input.priority()));
		cacheGeneration.bumpAfterCommit(user);
		return view(goal);
	}

	@Transactional
	public GoalView update(UserId user, UUID id, Input input, long expectedVersion) {
		SavingsGoal goal = goals.findByIdAndUserId(id, user).orElseThrow(() -> new NotFoundException("Goal"));
		if (goal.getVersion() != expectedVersion) {
			throw ConflictException.versionConflict();
		}
		validate(user, input);
		goal.update(input.name(), input.target(), input.current(), input.targetDate(), input.priority());
		goals.saveAndFlush(goal);
		cacheGeneration.bumpAfterCommit(user);
		return view(goal);
	}

	@Transactional
	public void delete(UserId user, UUID id) {
		if (goals.deleteByIdAndUserId(id, user) == 0) {
			throw new NotFoundException("Goal");
		}
		cacheGeneration.bumpAfterCommit(user);
	}

	@Override
	@Transactional
	public UUID createGoal(UserId user, String name, Money target, Money saved, LocalDate targetDate) {
		return create(user, new Input(name, target, saved, targetDate, 100)).id();
	}

	@Override
	@Transactional
	public boolean contribute(UserId user, UUID goalId, Money amount) {
		SavingsGoal goal = goals.findByIdAndUserId(goalId, user).orElse(null);
		if (goal == null || goal.getStatus() != GoalStatus.ACTIVE) {
			return false;
		}
		requireCurrency(amount, "amount", settings.get(user));
		goal.contribute(amount);
		goals.saveAndFlush(goal);
		cacheGeneration.bumpAfterCommit(user);
		return true;
	}

	@Override
	@Transactional
	public void deleteDemoData(UserId demoUser) {
		goals.deleteAllForDemoReset(demoUser);
	}

	private void validate(UserId user, Input input) {
		UserSettingsView userSettings = settings.get(user);
		requireCurrency(input.target(), "targetAmount", userSettings);
		requireCurrency(input.current(), "currentAmount", userSettings);
		LocalDate today = userSettings.today(clock);
		if (!input.targetDate().isAfter(today)) {
			throw ValidationException.of("targetDate", "must be in the future");
		}
	}

	private static void requireCurrency(Money money, String field, UserSettingsView userSettings) {
		if (money.currency() != userSettings.baseCurrency()) {
			throw new UnsupportedCurrencyException(field + ".currency", userSettings.baseCurrency());
		}
	}

	private static GoalView view(SavingsGoal g) {
		BigDecimal progress = g.getCurrent()
			.amount()
			.multiply(BigDecimal.valueOf(100))
			.divide(g.getTarget().amount(), 1, RoundingMode.HALF_EVEN)
			.min(BigDecimal.valueOf(100).setScale(1));
		return new GoalView(g.getId(), g.getName(), g.getTarget(), g.getCurrent(), g.remaining(), progress,
				g.getTargetDate(), g.getStatus(), g.getPriority(), g.getVersion());
	}

	public record Input(String name, Money target, Money current, LocalDate targetDate, int priority) {
	}

	/** {@code progressPct} is capped at 100 once the goal is achieved. */
	public record GoalView(UUID id, String name, Money target, Money current, Money remaining, BigDecimal progressPct,
			LocalDate targetDate, GoalStatus status, int priority, long version) {
	}

}
