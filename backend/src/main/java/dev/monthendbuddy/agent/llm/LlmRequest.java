package dev.monthendbuddy.agent.llm;

import java.util.List;

public record LlmRequest(List<LlmMessage> messages, List<ToolSpec> tools) {

	public LlmRequest {
		messages = List.copyOf(messages);
		tools = List.copyOf(tools);
	}

}
