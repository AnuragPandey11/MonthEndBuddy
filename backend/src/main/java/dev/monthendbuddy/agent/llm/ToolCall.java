package dev.monthendbuddy.agent.llm;

/** A tool the model asked to run; {@code argumentsJson} is untrusted model output. */
public record ToolCall(String id, String name, String argumentsJson) {

}
