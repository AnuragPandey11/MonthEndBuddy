package dev.monthendbuddy.budgets.api;

import java.net.URI;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.monthendbuddy.budgets.BudgetStatusReport;
import dev.monthendbuddy.budgets.BudgetStatusService;
import dev.monthendbuddy.budgets.application.BudgetService;
import dev.monthendbuddy.budgets.application.BudgetService.BudgetView;
import dev.monthendbuddy.common.web.MoneyDto;
import dev.monthendbuddy.common.web.PositiveMoney;
import dev.monthendbuddy.identity.CurrentUser;
import dev.monthendbuddy.identity.UserSettingsQuery;

@RestController
@RequestMapping("/api/v1/budgets")
class BudgetController {

	private final BudgetService budgets;

	private final BudgetStatusService status;

	private final UserSettingsQuery settings;

	private final Clock clock;

	BudgetController(BudgetService budgets, BudgetStatusService status, UserSettingsQuery settings, Clock clock) {
		this.budgets = budgets;
		this.status = status;
		this.settings = settings;
		this.clock = clock;
	}

	@GetMapping
	List<BudgetView> list(CurrentUser user) {
		return budgets.list(user.id());
	}

	@GetMapping("/status")
	BudgetStatusReport status(CurrentUser user, @RequestParam(required = false) YearMonth month) {
		return status.getStatus(user.id(), month != null ? month : settings.get(user.id()).currentMonth(clock));
	}

	@PostMapping
	ResponseEntity<BudgetView> create(CurrentUser user, @Valid @RequestBody CreateBudgetRequest body) {
		BudgetView created = budgets.create(user.id(), body.categoryId(), body.limit().toMoney("limit"),
				body.alertThresholds());
		return ResponseEntity.created(URI.create("/api/v1/budgets/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	BudgetView update(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody UpdateBudgetRequest body) {
		return budgets.update(user.id(), id, body.limit().toMoney("limit"), body.alertThresholds(), body.version());
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(CurrentUser user, @PathVariable UUID id) {
		budgets.delete(user.id(), id);
		return ResponseEntity.noContent().build();
	}

	/** {@code categoryId} null = the overall budget. */
	record CreateBudgetRequest(UUID categoryId, @NotNull @PositiveMoney MoneyDto limit,
			@Size(max = 5) List<Integer> alertThresholds) {
	}

	record UpdateBudgetRequest(@NotNull @PositiveMoney MoneyDto limit, @Size(max = 5) List<Integer> alertThresholds,
			@NotNull Long version) {
	}

}
