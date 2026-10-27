package dev.monthendbuddy.goals.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.monthendbuddy.common.Money;
import dev.monthendbuddy.common.web.MoneyDto;
import dev.monthendbuddy.common.web.PositiveMoney;
import dev.monthendbuddy.goals.SavingsProjection;
import dev.monthendbuddy.goals.SavingsProjectionService;
import dev.monthendbuddy.goals.application.GoalService;
import dev.monthendbuddy.goals.application.GoalService.GoalView;
import dev.monthendbuddy.identity.CurrentUser;

/** Savings goals and what-if projections; projections never persist anything (architecture §9.3). */
@RestController
@RequestMapping("/api/v1/goals")
class GoalController {

	private final GoalService goals;

	private final SavingsProjectionService projections;

	GoalController(GoalService goals, SavingsProjectionService projections) {
		this.goals = goals;
		this.projections = projections;
	}

	@GetMapping
	List<GoalView> list(CurrentUser user) {
		return goals.list(user.id());
	}

	@PostMapping
	ResponseEntity<GoalView> create(CurrentUser user, @Valid @RequestBody GoalRequest body) {
		GoalView created = goals.create(user.id(), body.toInput());
		return ResponseEntity.created(URI.create("/api/v1/goals/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	GoalView update(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody UpdateGoalRequest body) {
		return goals.update(user.id(), id, body.goal().toInput(), body.version());
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(CurrentUser user, @PathVariable UUID id) {
		goals.delete(user.id(), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/projection")
	SavingsProjection projectGoal(CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody(required = false) GoalWhatIf body) {
		GoalWhatIf whatIf = body == null ? new GoalWhatIf(null, null) : body;
		return projections.projectGoal(user.id(), id, money(whatIf.monthlyContribution(), "monthlyContribution"),
				whatIf.targetDate());
	}

	@PostMapping("/projection")
	SavingsProjection project(CurrentUser user, @Valid @RequestBody ProjectionRequest body) {
		return projections.project(user.id(), body.targetAmount().toMoney("targetAmount"), body.targetDate(),
				money(body.currentSavings(), "currentSavings"), money(body.monthlyContribution(), "monthlyContribution"));
	}

	private static Money money(MoneyDto dto, String field) {
		return dto == null ? null : dto.toMoney(field);
	}

	record GoalRequest(@NotBlank @Size(max = 80) String name, @NotNull @PositiveMoney MoneyDto targetAmount,
			@PositiveMoney(allowZero = true) MoneyDto currentAmount, @NotNull LocalDate targetDate,
			@Min(0) @Max(10000) Integer priority) {

		GoalService.Input toInput() {
			Money target = targetAmount.toMoney("targetAmount");
			return new GoalService.Input(name, target,
					currentAmount == null ? Money.zero(target.currency()) : currentAmount.toMoney("currentAmount"),
					targetDate, priority == null ? 100 : priority);
		}

	}

	record UpdateGoalRequest(@NotBlank @Size(max = 80) String name, @NotNull @PositiveMoney MoneyDto targetAmount,
			@PositiveMoney(allowZero = true) MoneyDto currentAmount, @NotNull LocalDate targetDate,
			@Min(0) @Max(10000) Integer priority, @NotNull Long version) {

		GoalRequest goal() {
			return new GoalRequest(name, targetAmount, currentAmount, targetDate, priority);
		}

	}

	record GoalWhatIf(@PositiveMoney(allowZero = true) MoneyDto monthlyContribution, LocalDate targetDate) {
	}

	record ProjectionRequest(@NotNull @PositiveMoney MoneyDto targetAmount, @NotNull LocalDate targetDate,
			@PositiveMoney(allowZero = true) MoneyDto currentSavings,
			@PositiveMoney(allowZero = true) MoneyDto monthlyContribution) {
	}

}
