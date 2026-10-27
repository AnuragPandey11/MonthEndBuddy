package dev.monthendbuddy.agent.tools;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import dev.monthendbuddy.analytics.AffordabilityResult;
import dev.monthendbuddy.analytics.AffordabilityService;

/**
 * Same service call as {@code POST /api/v1/simulations/purchase}. The model supplies only the purchase and what the
 * user stated; income, expenses, savings, the buffer and goal needs are derived by the backend (D5).
 */
@Component
class SimulatePurchaseTool implements FinanceTool<SimulatePurchaseTool.PurchaseInput> {

	private final AffordabilityService affordability;

	private final Clock clock;

	SimulatePurchaseTool(AffordabilityService affordability, Clock clock) {
		this.affordability = affordability;
		this.clock = clock;
	}

	record PurchaseInput(
			@JsonPropertyDescription("Price of the purchase") @NotNull @Positive @DecimalMax("1000000000") BigDecimal purchaseAmount,
			@JsonProperty(required = false) @JsonPropertyDescription("Month of the purchase as YYYY-MM. Omit for the current month.")
			@Pattern(regexp = CompareMonthlySpendingTool.MONTH, message = "must be a month in YYYY-MM format") String purchaseMonth,
			@JsonProperty(required = false) @JsonPropertyDescription("The user's total savings today, goal money included, only if the user stated a figure in this conversation") @PositiveOrZero @DecimalMax("1000000000") BigDecimal currentSavingsOverride,
			@JsonProperty(required = false) @JsonPropertyDescription("Goal IDs from the user context to protect. Omit for all active goals.") @Size(max = 20) List<UUID> goalIds) {
	}

	@Override
	public String name() {
		return "simulatePurchase";
	}

	@Override
	public String description() {
		return "Checks whether the user can afford a purchase: verdict, the savings already set aside for goals, the "
				+ "free savings left after it, the emergency buffer, monthly surplus, how long it takes to recover, "
				+ "and how it delays each goal. Every input is labelled with its source.";
	}

	@Override
	public Class<PurchaseInput> inputType() {
		return PurchaseInput.class;
	}

	@Override
	public String statusLabel() {
		return "Simulating the purchase";
	}

	@Override
	public ToolResult execute(PurchaseInput input, ToolContext context) {
		YearMonth month = input.purchaseMonth() == null ? context.currentMonth() : YearMonth.parse(input.purchaseMonth());
		AffordabilityResult result = affordability.simulate(context.userId(),
				Tools.money(input.purchaseAmount(), context), month,
				Tools.money(input.currentSavingsOverride(), context),
				input.goalIds() == null ? null : new HashSet<>(input.goalIds()));
		return ToolResult.ok(result, new Provenance("transactions, settings and goals",
				result.monthsOfData() + " complete months", context.currency(), clock.instant(), null),
				result.assumptions());
	}

}
