package dev.monthendbuddy.agent.llm;

import java.util.List;

/** The model's answer: final text, or tool calls to run first. */
public record LlmReply(String text, List<ToolCall> toolCalls, int promptTokens, int completionTokens, String model) {

	public LlmReply {
		toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
	}

	public boolean hasToolCalls() {
		return !toolCalls.isEmpty();
	}

	public int totalTokens() {
		return promptTokens + completionTokens;
	}

}
