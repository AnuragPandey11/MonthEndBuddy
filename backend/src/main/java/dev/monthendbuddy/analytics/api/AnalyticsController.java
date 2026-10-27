package dev.monthendbuddy.analytics.api;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.monthendbuddy.analytics.CategoryBreakdown;
import dev.monthendbuddy.analytics.ComparisonService;
import dev.monthendbuddy.analytics.MonthlySummary;
import dev.monthendbuddy.analytics.PeriodComparison;
import dev.monthendbuddy.analytics.SummaryService;
import dev.monthendbuddy.analytics.TrendSeries;
import dev.monthendbuddy.common.Period;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.identity.CurrentUser;
import dev.monthendbuddy.identity.UserSettingsQuery;

/**
 * Dashboard figures (architecture §13.2). Months are {@code YYYY-MM}; omitted months default to the current month in
 * the user's timezone. The SPA calls these in parallel; there is deliberately no composite dashboard endpoint.
 */
@RestController
@RequestMapping("/api/v1/analytics")
class AnalyticsController {

	private final SummaryService summaries;

	private final ComparisonService comparisons;

	private final UserSettingsQuery settings;

	private final Clock clock;

	AnalyticsController(SummaryService summaries, ComparisonService comparisons, UserSettingsQuery settings,
			Clock clock) {
		this.summaries = summaries;
		this.comparisons = comparisons;
		this.settings = settings;
		this.clock = clock;
	}

	@GetMapping("/summary")
	MonthlySummary summary(CurrentUser user, @RequestParam(required = false) YearMonth month) {
		return summaries.monthlySummary(user.id(), month != null ? month : currentMonth(user));
	}

	@GetMapping("/categories")
	CategoryBreakdown categories(CurrentUser user,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		if ((from == null) != (to == null)) {
			throw ValidationException.of(from == null ? "from" : "to", "give both from and to, or neither");
		}
		Period period = from != null ? new Period(from, to) : Period.ofMonth(currentMonth(user));
		return summaries.categoryBreakdown(user.id(), period);
	}

	@GetMapping("/trends")
	TrendSeries trends(CurrentUser user, @RequestParam(defaultValue = "6") int months) {
		return summaries.trends(user.id(), months);
	}

	@GetMapping("/compare")
	PeriodComparison compare(CurrentUser user, @RequestParam(required = false) YearMonth current,
			@RequestParam(required = false) YearMonth previous) {
		YearMonth currentMonth = current != null ? current : currentMonth(user);
		return comparisons.compare(user.id(), currentMonth, previous != null ? previous : currentMonth.minusMonths(1));
	}

	private YearMonth currentMonth(CurrentUser user) {
		return settings.get(user.id()).currentMonth(clock);
	}

}
