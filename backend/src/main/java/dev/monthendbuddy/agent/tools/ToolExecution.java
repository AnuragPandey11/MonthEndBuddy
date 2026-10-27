package dev.monthendbuddy.agent.tools;

import tools.jackson.databind.JsonNode;

import dev.monthendbuddy.agent.llm.ToolCall;

/**
 * One executed tool call. {@code payload} is exactly what the model was sent; {@code evidenceData} is the same
 * result as the UI renders it, which may add chart-only fields ({@link FinanceTool#uiOnlyFields()}). Both are
 * size-capped. {@code arguments} is the parsed argument object, or null when the arguments were not valid JSON.
 */
public record ToolExecution(ToolCall call, JsonNode arguments, ToolResult result, JsonNode payload,
		JsonNode evidenceData, long durationMillis) {

	public boolean ok() {
		return result.ok();
	}

}
