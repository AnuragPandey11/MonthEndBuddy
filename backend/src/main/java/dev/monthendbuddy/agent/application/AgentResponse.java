package dev.monthendbuddy.agent.application;

import java.util.List;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.agent.domain.AgentStatus;
import dev.monthendbuddy.agent.tools.AskClarifyingQuestionTool.Clarification;
import dev.monthendbuddy.agent.tools.Provenance;
import dev.monthendbuddy.agent.tools.ProposedAction;
import dev.monthendbuddy.common.Assumption;

/**
 * The copilot's answer (architecture §10.6). The UI renders {@code evidence[].result} as cards, keyed by tool name:
 * those are the verified figures, and {@code answer} is the explanation. {@code unverifiedAmounts} lists amounts in
 * the answer that appear in no evidence, argument or user message (the grounding check); the UI marks them
 * "estimate — not from your data".
 */
public record AgentResponse(UUID conversationId, UUID messageId, AgentStatus status, String answer,
		List<Evidence> evidence, List<Assumption> assumptions, Clarification clarification,
		List<ProposedAction> proposedActions, List<String> unverifiedAmounts, String model, String promptVersion) {

	/** One successful tool call. {@code truncated} is true when the result was cut to fit the size cap. */
	public record Evidence(String toolCallId, String tool, JsonNode args, JsonNode result, Provenance provenance,
			boolean truncated) {
	}

	/** What {@code agent_messages.tool_metadata} stores, so a reopened conversation renders the same cards. */
	public record StoredTurn(List<Evidence> evidence, List<Assumption> assumptions, Clarification clarification,
			List<ProposedAction> proposedActions, List<String> unverifiedAmounts) {
	}

	StoredTurn stored() {
		return new StoredTurn(evidence, assumptions, clarification, proposedActions, unverifiedAmounts);
	}

	AgentResponse withIds(UUID conversation, UUID message) {
		return new AgentResponse(conversation, message, status, answer, evidence, assumptions, clarification,
				proposedActions, unverifiedAmounts, model, promptVersion);
	}

}
