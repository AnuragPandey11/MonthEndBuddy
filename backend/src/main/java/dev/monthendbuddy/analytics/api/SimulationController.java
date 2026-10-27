package dev.monthendbuddy.analytics.api;

import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.monthendbuddy.analytics.AffordabilityResult;
import dev.monthendbuddy.analytics.AffordabilityService;
import dev.monthendbuddy.common.web.MoneyDto;
import dev.monthendbuddy.common.web.PositiveMoney;
import dev.monthendbuddy.identity.CurrentUser;

@RestController
@RequestMapping("/api/v1/simulations")
class SimulationController {

	private final AffordabilityService affordability;

	SimulationController(AffordabilityService affordability) {
		this.affordability = affordability;
	}

	/** Nothing is saved; {@code goalIds} null means all active goals. */
	@PostMapping("/purchase")
	AffordabilityResult purchase(CurrentUser user, @Valid @RequestBody PurchaseRequest body) {
		return affordability.simulate(user.id(), body.purchaseAmount().toMoney("purchaseAmount"), body.purchaseMonth(),
				body.currentSavingsOverride() == null ? null
						: body.currentSavingsOverride().toMoney("currentSavingsOverride"),
				body.goalIds());
	}

	record PurchaseRequest(@NotNull @PositiveMoney MoneyDto purchaseAmount, YearMonth purchaseMonth,
			@PositiveMoney(allowZero = true) MoneyDto currentSavingsOverride, @Size(max = 50) Set<UUID> goalIds) {
	}

}
