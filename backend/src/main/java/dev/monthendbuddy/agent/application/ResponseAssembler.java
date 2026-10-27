package dev.monthendbuddy.agent.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.agent.application.AgentResponse.Evidence;
import dev.monthendbuddy.agent.domain.AgentStatus;
import dev.monthendbuddy.agent.tools.ProposedAction;
import dev.monthendbuddy.agent.tools.ToolExecution;
import dev.monthendbuddy.common.Assumption;

/** Turns a finished turn into the response contract (architecture §10.6). */
@Component
class ResponseAssembler {

	AgentResponse assemble(TurnState state, AgentStatus status, String userMessage) {
		List<Evidence> evidence = new ArrayList<>();
		Set<Assumption> assumptions = new LinkedHashSet<>();
		Set<ProposedAction> actions = new LinkedHashSet<>();
		List<JsonNode> groundingData = new ArrayList<>();
		for (ToolExecution execution : state.executions()) {
			if (execution.arguments() != null) {
				groundingData.add(execution.arguments());
			}
			if (!execution.ok() || state.isClarification(execution)) {
				continue;
			}
			evidence.add(new Evidence(execution.call().id(), execution.call().name(), execution.arguments(),
					execution.evidenceData(), execution.result().provenance(), execution.payload().has("truncated")));
			assumptions.addAll(execution.result().assumptions());
			actions.addAll(execution.result().proposedActions());
			groundingData.add(execution.payload());
		}
		String answer = answer(state, status);
		List<String> unverified = status == AgentStatus.COMPLETE
				? GroundingCheck.unverifiedAmounts(answer, groundingData, userMessage) : List.of();
		return new AgentResponse(null, null, status, answer, evidence, List.copyOf(assumptions),
				state.clarification(), List.copyOf(actions), unverified, state.model(), PromptBuilder.PROMPT_VERSION);
	}

	/** The model's text, or a fixed explanation when the turn did not end with one. */
	private static String answer(TurnState state, AgentStatus status) {
		return switch (status) {
			case COMPLETE -> state.finalText().strip();
			case NEEDS_CLARIFICATION -> state.clarification().question();
			case PROVIDER_UNAVAILABLE -> "The copilot can't answer right now (" + state.providerProblem()
					+ "). Your dashboard and reports still work; try again in a minute.";
			case LIMIT_REACHED -> state.executions().isEmpty()
					? "I couldn't finish this answer within the step limit. Try asking a narrower question."
					: "I couldn't finish this answer within the step limit. The figures I gathered are shown below.";
			case PARTIAL -> state.executions().isEmpty() ? "This took too long to answer. Please try again."
					: "This took too long to answer completely. The figures I gathered are shown below.";
		};
	}

}
