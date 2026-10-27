package dev.monthendbuddy.analytics.api;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.monthendbuddy.analytics.MonthReviewService;
import dev.monthendbuddy.analytics.MonthReviewService.CompleteCommand;
import dev.monthendbuddy.analytics.MonthReviewService.Contribution;
import dev.monthendbuddy.analytics.MonthReviewView;
import dev.monthendbuddy.common.web.MoneyDto;
import dev.monthendbuddy.common.web.PositiveMoney;
import dev.monthendbuddy.identity.CurrentUser;

/** Last month's review (architecture §9.6): read it, then confirm what to do with what was kept, once. */
@RestController
@RequestMapping("/api/v1/month-review")
class MonthReviewController {

	private final MonthReviewService reviews;

	MonthReviewController(MonthReviewService reviews) {
		this.reviews = reviews;
	}

	@GetMapping
	MonthReviewView lastMonth(CurrentUser user) {
		return reviews.lastMonth(user.id());
	}

	/** No amounts at all records the month as skipped. 409 {@code month_already_reviewed} on a second call. */
	@PostMapping
	MonthReviewView complete(CurrentUser user, @Valid @RequestBody ReviewRequest body) {
		List<Contribution> contributions = body.goalContributions() == null ? List.of()
				: body.goalContributions()
					.stream()
					.map(c -> new Contribution(c.goalId(), c.amount().toMoney("amount")))
					.toList();
		return reviews.complete(user.id(), new CompleteCommand(body.month(),
				body.addToSavings() == null ? null : body.addToSavings().toMoney("addToSavings"), contributions));
	}

	record ReviewRequest(@NotNull YearMonth month, @PositiveMoney MoneyDto addToSavings,
			@Size(max = MonthReviewService.MAX_GOALS) List<@Valid GoalContribution> goalContributions) {
	}

	record GoalContribution(@NotNull UUID goalId, @NotNull @PositiveMoney MoneyDto amount) {
	}

}
