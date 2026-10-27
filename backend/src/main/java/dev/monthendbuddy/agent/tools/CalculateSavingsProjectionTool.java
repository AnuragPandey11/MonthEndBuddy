package dev.monthendbuddy.agent.tools;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import dev.monthendbuddy.agent.tools.ProposedAction.ActionType;
import dev.monthendbuddy.common.error.ValidationException;
import dev.monthendbuddy.goals.SavingsProjection;
import dev.monthendbuddy.goals.SavingsProjectionService;

/**
 * Same service calls as {@code POST /api/v1/goals/{id}/projection} (a saved goal) and
 * {@code POST /api/v1/goals/projection} (an unsaved target). An unsaved target comes with a {@code CREATE_GOAL}
 * proposal, so the user can keep it with one confirmation.
 */
@Component
class CalculateSavingsProjectionTool implements FinanceTool<CalculateSavingsProjectionTool.ProjectionInput> {

	private final SavingsProjectionService projections;

	private final Clock clock;

	CalculateSavingsProjectionTool(SavingsProjectionService projections, Clock clock) {
		this.projections = projections;
		this.clock = clock;
	}

	/** Either {@code goalId}, or {@code targetAmount} with {@code targetDate}. Amounts are in the user's currency. */
	record ProjectionInput(
			@JsonProperty(required = false) @JsonPropertyDescription("ID of a saved goal from the user context. Omit for a new target.") UUID goalId,
			@JsonProperty(required = false) @JsonPropertyDescription("Target amount for a new target") @Positive @DecimalMax("1000000000") BigDecimal targetAmount,
			@JsonProperty(required = false) @JsonPropertyDescription("Deadline as YYYY-MM-DD; for a saved goal, overrides its deadline") LocalDate targetDate,
			@JsonProperty(required = false) @JsonPropertyDescription("Amount already saved toward a new target; only if the user said so") @PositiveOrZero @DecimalMax("1000000000") BigDecimal currentSavings,
			@JsonProperty(required = false) @JsonPropertyDescription("Monthly contribution to test; omit to get the required amount") @Positive @DecimalMax("1000000000") BigDecimal monthlyContribution) {
	}

	@Override
	public String name() {
		return "calculateSavingsProjection";
	}

	@Override
	public String description() {
		return "Projects a savings goal: the monthly amount required to reach it by its deadline, and when a given "
				+ "monthly contribution would reach it. Pass goalId for a saved goal, or targetAmount and targetDate "
				+ "for a new one.";
	}

	@Override
	public Class<ProjectionInput> inputType() {
		return ProjectionInput.class;
	}

	@Override
	public String statusLabel() {
		return "Projecting your savings";
	}

	@Override
	public Set<String> uiOnlyFields() {
		return Set.of("series");
	}

	@Override
	public ToolResult execute(ProjectionInput input, ToolContext context) {
		SavingsProjection projection;
		if (input.goalId() != null) {
			projection = projections.projectGoal(context.userId(), input.goalId(),
					Tools.money(input.monthlyContribution(), context), input.targetDate());
		}
		else {
			if (input.targetAmount() == null || input.targetDate() == null) {
				throw ValidationException.of("targetAmount", "give goalId, or both targetAmount and targetDate");
			}
			projection = projections.project(context.userId(), Tools.money(input.targetAmount(), context),
					input.targetDate(), Tools.money(input.currentSavings(), context),
					Tools.money(input.monthlyContribution(), context));
		}
		Provenance provenance = new Provenance(input.goalId() != null ? "goals" : "calculation",
				"until " + projection.targetDate(), context.currency(), clock.instant(), null);
		ToolResult result = ToolResult.ok(projection, provenance, projection.assumptions());
		if (input.goalId() != null) {
			return result;
		}
		return result.withProposedActions(List.of(new ProposedAction(ActionType.CREATE_GOAL,
				Map.of("targetAmount", Tools.wire(projection.target()), "currentAmount", Tools.wire(projection.current()),
						"targetDate", projection.targetDate().toString()),
				"Save this as a goal")));
	}

}
