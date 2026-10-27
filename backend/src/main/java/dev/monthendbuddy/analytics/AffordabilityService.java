package dev.monthendbuddy.analytics;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.analytics.domain.AffordabilityCalculator;
import dev.monthendbuddy.common.Assumption;
import dev.monthendbuddy.common.AssumptionLabel;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.UnsupportedCurrencyException;
import dev.monthendbuddy.goals.GoalNeeds;
import dev.monthendbuddy.goals.GoalSavings;
import dev.monthendbuddy.goals.ProjectionStatus;
import dev.monthendbuddy.goals.SavingsProjectionService;
import dev.monthendbuddy.identity.UserSettingsQuery;
import dev.monthendbuddy.identity.UserSettingsView;

/**
 * "Can I afford it?" (architecture §9.4). The backend derives income, expenses, savings, the buffer and goal needs
 * itself; the caller supplies only the purchase and, optionally, a savings figure the user just stated (architecture
 * D4). Backs {@code POST /simulations/purchase} and, from Phase 3, the {@code simulatePurchase} tool.
 */
@Service
@Transactional(readOnly = true)
public class AffordabilityService {

	public static final int AVERAGE_MONTHS = 3;

	private final SummaryService summaries;

	private final SavingsProjectionService projections;

	private final UserSettingsQuery settings;

	private final Clock clock;

	public AffordabilityService(SummaryService summaries, SavingsProjectionService projections,
			UserSettingsQuery settings, Clock clock) {
		this.summaries = summaries;
		this.projections = projections;
		this.settings = settings;
		this.clock = clock;
	}

	public AffordabilityResult simulate(UserId user, Money purchase, YearMonth purchaseMonth,
			Money currentSavingsOverride, Set<UUID> goalIds) {
		UserSettingsView userSettings = settings.get(user);
		requireBase(purchase, "purchaseAmount", userSettings);
		requireBase(currentSavingsOverride, "currentSavingsOverride", userSettings);
		MonthlyAverages averages = summaries.averages(user, AVERAGE_MONTHS);
		GoalNeeds needs = projections.requiredMonthlyForActiveGoals(user, goalIds);
		GoalSavings setAside = projections.savedInActiveGoals(user);
		List<Assumption> assumptions = new ArrayList<>();
		List<AffordabilityResult.DataQualityFlag> quality = new ArrayList<>();

		Money savings;
		if (currentSavingsOverride != null) {
			savings = currentSavingsOverride;
			assumptions.add(new Assumption("currentSavings", savings.toApiString(), AssumptionLabel.USER_STATED));
		}
		else if (userSettings.currentSavings() != null) {
			savings = userSettings.currentSavings();
			assumptions.add(new Assumption("currentSavings",
					savings.toApiString() + " (from your settings, updated " + userSettings.currentSavingsUpdatedAt()
							+ ")",
					AssumptionLabel.USER_PROFILE));
		}
		else {
			savings = null;
			quality.add(new AffordabilityResult.DataQualityFlag("MISSING_INPUT",
					"Your current savings are unknown. Add them in Settings or state them for this question."));
		}

		if (averages.monthsUsed() == 0) {
			quality.add(new AffordabilityResult.DataQualityFlag("LOW_DATA",
					"There is no complete month of transactions yet, so income and spending cannot be averaged."));
		}
		else {
			String window = "average of the last " + averages.monthsUsed() + " complete month"
					+ (averages.monthsUsed() == 1 ? "" : "s") + " with data";
			assumptions.add(new Assumption("averageMonthlyIncome", averages.income().toApiString() + " (" + window + ")",
					AssumptionLabel.DERIVED));
			assumptions.add(new Assumption("averageMonthlyExpenses",
					averages.expenses().toApiString() + " (" + window + ")", AssumptionLabel.DERIVED));
			if (averages.lowData()) {
				quality.add(new AffordabilityResult.DataQualityFlag("LOW_DATA", "Only " + averages.monthsUsed()
						+ " of the last " + AVERAGE_MONTHS + " months have transactions, so the averages are rough."));
			}
		}
		// Savings are a total; what goals already hold is set aside, whichever goals this question is about.
		assumptions.add(new Assumption("savingsSetAside", setAside.total().toApiString() + " already saved toward "
				+ setAside.goals() + " active goal" + (setAside.goals() == 1 ? "" : "s")
				+ ", counted as part of your savings and not free to spend", AssumptionLabel.DERIVED));
		if (savings != null && savings.subtract(setAside.total()).isNegative()) {
			quality.add(new AffordabilityResult.DataQualityFlag("GOALS_EXCEED_SAVINGS",
					"Your goals hold more than your savings today, so nothing is free to spend. Update Savings today in "
							+ "Settings or the amounts saved in your goals."));
		}
		assumptions.add(new Assumption("emergencyBuffer", userSettings.emergencyBufferMonths()
				+ " months of average expenses", AssumptionLabel.DEFAULT_ASSUMPTION));
		assumptions.add(new Assumption("goalNeed", needs.total().toApiString() + " a month across "
				+ needs.goals().size() + " active goal" + (needs.goals().size() == 1 ? "" : "s"),
				AssumptionLabel.DERIVED));
		assumptions.add(new Assumption("goalDelay",
				"goal contributions pause until the monthly surplus has earned the purchase back",
				AssumptionLabel.DEFAULT_ASSUMPTION));
		assumptions.add(new Assumption("return", "0% on savings", AssumptionLabel.DEFAULT_ASSUMPTION));

		AffordabilityCalculator.Outcome outcome = AffordabilityCalculator
			.evaluate(new AffordabilityCalculator.Inputs(purchase, savings, setAside.total(), averages.income(),
					averages.expenses(), averages.monthsUsed(), userSettings.emergencyBufferMonths(), needs.total()));

		Integer delay = AffordabilityCalculator.goalDelayMonths(purchase, outcome.surplus());
		List<AffordabilityResult.GoalImpact> impacts = new ArrayList<>();
		for (GoalNeeds.GoalNeed need : needs.goals()) {
			if (need.status() == ProjectionStatus.DEADLINE_PASSED) {
				quality.add(new AffordabilityResult.DataQualityFlag("DEADLINE_PASSED",
						"The deadline of \"" + need.name() + "\" has passed, so it is not counted."));
			}
			impacts.add(new AffordabilityResult.GoalImpact(need.goalId(), need.name(), need.requiredMonthly(),
					need.requiredMonthly() == null ? null : delay));
		}

		return new AffordabilityResult(outcome.verdict(), purchase,
				purchaseMonth != null ? purchaseMonth : userSettings.currentMonth(clock), savings, setAside.total(),
				outcome.available(), outcome.savingsAfter(),
				averages.income(), averages.expenses(), outcome.surplus(), outcome.buffer(), needs.total(),
				outcome.recoveryMonths(), impacts, List.copyOf(assumptions), List.copyOf(quality),
				averages.monthsUsed());
	}

	private static void requireBase(Money money, String field, UserSettingsView userSettings) {
		if (money != null && money.currency() != userSettings.baseCurrency()) {
			throw new UnsupportedCurrencyException(field + ".currency", userSettings.baseCurrency());
		}
	}

}
