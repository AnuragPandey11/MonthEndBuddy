package dev.monthendbuddy.agent.llm;

import java.util.List;

/**
 * A provider-neutral chat message. The orchestrator keeps the conversation in these types and the gateway converts
 * them into fresh provider messages on every call, so provider metadata (such as a model's reasoning) is never sent
 * back (ADR 0001).
 */
public sealed interface LlmMessage {

	record System(String text) implements LlmMessage {
	}

	record User(String text) implements LlmMessage {
	}

	record Assistant(String text, List<ToolCall> toolCalls) implements LlmMessage {

		public Assistant {
			toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
		}

	}

	/** The JSON result of one tool call, sent back to the model. */
	record ToolResult(String toolCallId, String toolName, String json) implements LlmMessage {
	}

}
