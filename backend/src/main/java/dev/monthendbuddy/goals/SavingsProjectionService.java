package dev.monthendbuddy.goals;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.common.Assumption;
import dev.monthendbuddy.common.AssumptionLabel;
import dev.monthendbuddy.common.CurrencyCode;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.NotFoundException;
import dev.monthendbuddy.common.error.UnsupportedCurrencyException;
import dev.monthendbuddy.goals.domain.SavingsGoal;
import dev.monthendbuddy.goals.domain.SavingsMath;
import dev.monthendbuddy.goals.infrastructure.GoalRepository;
import dev.monthendbuddy.identity.UserSettingsQuery;
import dev.monthendbuddy.identity.UserSettingsView;

/**
 * Required monthly contribution, projected completion and a month-by-month series (architecture §9.3). Backs the
 * projection endpoints, the purchase simulation and, from Phase 3, the {@code calculateSavingsProjection} tool.
 */
@Service
@Transactional(readOnly = true)
public class SavingsProjectionService {

	/** Longest series returned, so a tiny contribution cannot produce thousands of points. */
	public static final int MAX_SERIES_MONTHS = 120;

	private final GoalRepository goals;

	private final UserSettingsQuery settings;

	private final Clock clock;

	public SavingsProjectionService(GoalRepository goals, UserSettingsQuery settings, Clock clock) {
		this.goals = goals;
		this.settings = settings;
		this.clock = clock;
	}

	/**
	 * An unsaved goal. {@code currentSavings} defaults to zero and {@code monthlyContribution} to the required amount;
	 * both are labelled accordingly in the assumptions.
	 */
	public SavingsProjection project(UserId user, Money target, LocalDate targetDate, Money currentSavings,
			Money monthlyContribution) {
		UserSettingsView userSettings = settings.get(user);
		requireCurrency(userSettings.baseCurrency(), target, "targetAmount");
		requireCurrency(userSettings.baseCurrency(), currentSavings, "currentSavings");
		requireCurrency(userSettings.baseCurrency(), monthlyContribution, "monthlyContribution");
		List<Assumption> assumptions = new ArrayList<>();
		Money current;
		if (currentSavings == null) {
			current = Money.zero(target.currency());
			assumptions.add(new Assumption("goalSaved", "0 (not provided)", AssumptionLabel.DEFAULT_ASSUMPTION));
		}
		else {
			current = currentSavings;
			assumptions.add(new Assumption("goalSaved", current.toApiString(), AssumptionLabel.USER_STATED));
		}
		return calculate(userSettings.currentMonth(clock), target, current, targetDate, monthlyContribution,
				AssumptionLabel.USER_STATED, assumptions);
	}

	/** A stored goal, optionally with a different contribution or deadline ("what if?"). */
	public SavingsProjection projectGoal(UserId user, UUID goalId, Money monthlyContribution, LocalDate targetDate) {
		SavingsGoal goal = goals.findByIdAndUserId(goalId, user).orElseThrow(() -> new NotFoundException("Goal"));
		UserSettingsView userSettings = settings.get(user);
		requireCurrency(userSettings.baseCurrency(), monthlyContribution, "monthlyContribution");
		List<Assumption> assumptions = new ArrayList<>();
		// The goal's own saved amount, not the total savings in Settings: named so the two are never confused.
		assumptions.add(new Assumption("goalSaved", goal.getCurrent().toApiString() + " (saved toward this goal)",
				AssumptionLabel.USER_PROFILE));
		return calculate(userSettings.currentMonth(clock), goal.getTarget(), goal.getCurrent(),
				targetDate != null ? targetDate : goal.getTargetDate(), monthlyContribution, AssumptionLabel.USER_STATED,
				assumptions);
	}

	/**
	 * What the user's active goals already hold. Every active goal counts, whichever goals a simulation asks about:
	 * money saved toward any of them is set aside. Achieved and archived goals are not counted.
	 */
	@Transactional(readOnly = true)
	public GoalSavings savedInActiveGoals(UserId user) {
		Money total = Money.zero(settings.get(user).baseCurrency());
		int count = 0;
		for (SavingsGoal goal : goals.findCurrent(user)) {
			if (goal.getStatus() == GoalStatus.ACTIVE) {
				total = total.add(goal.getCurrent());
				count++;
			}
		}
		return new GoalSavings(total, count);
	}

	/** Monthly needs of the given active goals, or all of them when {@code goalIds} is null. */
	public GoalNeeds requiredMonthlyForActiveGoals(UserId user, Set<UUID> goalIds) {
		UserSettingsView userSettings = settings.get(user);
		YearMonth current = userSettings.currentMonth(clock);
		Money total = Money.zero(userSettings.baseCurrency());
		List<GoalNeeds.GoalNeed> needs = new ArrayList<>();
		for (SavingsGoal goal : goals.findCurrent(user)) {
			if (goal.getStatus() != GoalStatus.ACTIVE || (goalIds != null && !goalIds.contains(goal.getId()))) {
				continue;
			}
			int months = SavingsMath.monthsRemaining(current, goal.getTargetDate());
			if (months <= 0) {
				needs.add(new GoalNeeds.GoalNeed(goal.getId(), goal.getName(), null, ProjectionStatus.DEADLINE_PASSED));
				continue;
			}
			Money required = SavingsMath.requiredMonthly(goal.remaining(), months);
			total = total.add(required);
			needs.add(new GoalNeeds.GoalNeed(goal.getId(), goal.getName(), required, ProjectionStatus.ON_TRACK));
		}
		return new GoalNeeds(total, needs);
	}

	private SavingsProjection calculate(YearMonth currentMonth, Money target, Money current, LocalDate targetDate,
			Money monthlyContribution, AssumptionLabel contributionLabel, List<Assumption> assumptions) {
		int months = SavingsMath.monthsRemaining(currentMonth, targetDate);
		Money remaining = target.subtract(current);
		remaining = remaining.isNegative() ? Money.zero(target.currency()) : remaining;
		Money required = remaining.isZero() ? Money.zero(target.currency())
				: months > 0 ? SavingsMath.requiredMonthly(remaining, months) : null;

		Money contribution = monthlyContribution != null ? monthlyContribution : required;
		if (monthlyContribution != null) {
			assumptions.add(new Assumption("monthlyContribution", contribution.toApiString(), contributionLabel));
		}
		else if (required != null) {
			assumptions.add(new Assumption("monthlyContribution", required.toApiString() + " (the required amount)",
					AssumptionLabel.DERIVED));
		}
		assumptions.add(new Assumption("return", "0% a year", AssumptionLabel.DEFAULT_ASSUMPTION));
		assumptions.add(new Assumption("contributions", "the same amount at the start of every month from next month",
				AssumptionLabel.DEFAULT_ASSUMPTION));

		Optional<YearMonth> completion = contribution == null ? Optional.empty()
				: SavingsMath.completionMonth(current, target, contribution, currentMonth);
		ProjectionStatus status;
		if (remaining.isZero()) {
			status = ProjectionStatus.ACHIEVED;
		}
		else if (months <= 0) {
			status = ProjectionStatus.DEADLINE_PASSED;
		}
		else if (completion.isEmpty()) {
			status = ProjectionStatus.NEVER;
		}
		else {
			status = completion.get().isAfter(YearMonth.from(targetDate)) ? ProjectionStatus.BEHIND
					: ProjectionStatus.ON_TRACK;
		}

		int seriesMonths = Math.max(months, 0);
		if (completion.isPresent()) {
			seriesMonths = Math.max(seriesMonths, (int) ChronoUnit.MONTHS.between(currentMonth,
					completion.get()));
		}
		seriesMonths = Math.min(seriesMonths, MAX_SERIES_MONTHS);
		var series = contribution == null || seriesMonths == 0 ? List.<SavingsMath.ProjectionPoint>of()
				: SavingsMath.series(current, contribution, currentMonth, seriesMonths);

		return new SavingsProjection(target, current, remaining, targetDate, months, required, contribution, status,
				completion.orElse(null), series, List.copyOf(assumptions));
	}

	private static void requireCurrency(CurrencyCode base, Money money, String field) {
		if (money != null && money.currency() != base) {
			throw new UnsupportedCurrencyException(field + ".currency", base);
		}
	}

}
