package dev.monthendbuddy.analytics;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.analytics.domain.MonthReview;
import dev.monthendbuddy.analytics.infrastructure.MonthReviewRepository;
import dev.monthendbuddy.common.DemoDataReset;
import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.ConflictException;
import dev.monthendbuddy.common.error.UnsupportedCurrencyException;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.goals.GoalCommands;
import dev.monthendbuddy.identity.UserSavingsCommands;
import dev.monthendbuddy.identity.UserSettingsQuery;
import dev.monthendbuddy.identity.UserSettingsView;

/**
 * The month-end review (architecture §9.6). Once a month has ended, its net cash flow ("what you kept") is offered
 * once: the user may add it, or part of it, to Savings today and set some aside for goals. Nothing changes until they
 * confirm. A review is recorded once per user and month, in the same transaction as the writes it makes, so a retry
 * or a double click is rejected instead of adding the money twice, and a failed goal update undoes the savings update.
 */
@Service
public class MonthReviewService implements DemoDataReset {

	/** A review may set money aside for at most this many goals at once. */
	public static final int MAX_GOALS = 10;

	private final SummaryService summaries;

	private final MonthReviewRepository reviews;

	private final UserSettingsQuery settings;

	private final UserSavingsCommands savings;

	private final GoalCommands goals;

	private final Clock clock;

	public MonthReviewService(SummaryService summaries, MonthReviewRepository reviews, UserSettingsQuery settings,
			UserSavingsCommands savings, GoalCommands goals, Clock clock) {
		this.summaries = summaries;
		this.reviews = reviews;
		this.settings = settings;
		this.savings = savings;
		this.goals = goals;
		this.clock = clock;
	}

	/** Last month, in the user's timezone, and whether it is due for review. */
	@Transactional(readOnly = true)
	public MonthReviewView lastMonth(UserId user) {
		UserSettingsView userSettings = settings.get(user);
		YearMonth month = userSettings.currentMonth(clock).minusMonths(1);
		MonthlySummary summary = summaries.monthlySummary(user, month);
		long count = summary.coverage().transactionCount();
		Instant updatedAt = userSettings.currentSavingsUpdatedAt();
		boolean updatedSince = updatedAt != null && !LocalDate.ofInstant(updatedAt, userSettings.timezone())
			.isBefore(month.plusMonths(1).atDay(1));
		return reviews.findByUserIdAndMonthStart(user, month.atDay(1))
			.map(r -> new MonthReviewView(month, summary.net(), count, r.getStatus(), userSettings.currentSavings(),
					updatedAt, updatedSince, r.getAddedToSavings(), r.getSetAsideForGoals()))
			.orElseGet(() -> new MonthReviewView(month, summary.net(), count,
					count > 0 && summary.net().isPositive() ? MonthReviewStatus.DUE : MonthReviewStatus.NOTHING_KEPT,
					userSettings.currentSavings(), updatedAt, updatedSince, null, null));
	}

	/**
	 * Applies the user's choices for last month and records the review. With nothing to add or set aside, the month is
	 * recorded as skipped. Amounts are the user's to choose; they are not checked against what the month kept.
	 */
	@Transactional
	public MonthReviewView complete(UserId user, CompleteCommand command) {
		UserSettingsView userSettings = settings.get(user);
		YearMonth lastMonth = userSettings.currentMonth(clock).minusMonths(1);
		if (!lastMonth.equals(command.month())) {
			throw ValidationException.of("month", "only last month (" + lastMonth + ") can be reviewed");
		}
		requireBase(command.addToSavings(), "addToSavings", userSettings);
		if (command.addToSavings() != null && userSettings.currentSavings() == null) {
			throw ValidationException.of("addToSavings",
					"set your savings today in Settings first, so there is a balance to add to");
		}
		List<Contribution> contributions = command.goalContributions() == null ? List.of()
				: command.goalContributions();
		if (contributions.size() > MAX_GOALS) {
			throw ValidationException.of("goalContributions", "at most " + MAX_GOALS + " goals at a time");
		}
		Money setAside = null;
		Set<UUID> seen = new HashSet<>();
		for (int i = 0; i < contributions.size(); i++) {
			Contribution c = contributions.get(i);
			requireBase(c.amount(), "goalContributions[" + i + "].amount", userSettings);
			if (!seen.add(c.goalId())) {
				throw ValidationException.of("goalContributions[" + i + "].goalId", "appears more than once");
			}
			setAside = setAside == null ? c.amount() : setAside.add(c.amount());
		}

		// The review row goes in first: the unique (user, month) index turns a concurrent second confirmation into a
		// conflict before any money moves.
		if (reviews.findByUserIdAndMonthStart(user, lastMonth.atDay(1)).isPresent()) {
			throw alreadyReviewed(lastMonth);
		}
		Money kept = summaries.monthlySummary(user, lastMonth).net();
		try {
			reviews.saveAndFlush(MonthReview.record(user, lastMonth, kept, command.addToSavings(), setAside));
		}
		catch (DataIntegrityViolationException e) {
			throw alreadyReviewed(lastMonth);
		}

		if (command.addToSavings() != null) {
			savings.addToCurrentSavings(user, command.addToSavings());
		}
		for (int i = 0; i < contributions.size(); i++) {
			Contribution c = contributions.get(i);
			if (!goals.contribute(user, c.goalId(), c.amount())) {
				// Another user's goal looks exactly like a missing one; the exception rolls back everything above.
				throw ValidationException.of("goalContributions[" + i + "].goalId", "is not one of your active goals");
			}
		}
		return lastMonth(user);
	}

	@Override
	@Transactional
	public void deleteDemoData(UserId demoUser) {
		reviews.deleteAllForDemoReset(demoUser);
	}

	private static ConflictException alreadyReviewed(YearMonth month) {
		return new ConflictException("month_already_reviewed", month + " has already been reviewed");
	}

	private static void requireBase(Money money, String field, UserSettingsView userSettings) {
		if (money != null && money.currency() != userSettings.baseCurrency()) {
			throw new UnsupportedCurrencyException(field + ".currency", userSettings.baseCurrency());
		}
	}

	/** Positive amounts, validated at the API edge. {@code addToSavings} and {@code goalContributions} are optional. */
	public record CompleteCommand(YearMonth month, Money addToSavings, List<Contribution> goalContributions) {
	}

	public record Contribution(UUID goalId, Money amount) {
	}

}
