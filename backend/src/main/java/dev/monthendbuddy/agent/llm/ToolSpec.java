package dev.monthendbuddy.agent.llm;

/** What the model is told about one tool; the gateway derives the JSON schema from {@code inputType}. */
public record ToolSpec(String name, String description, Class<?> inputType) {

}
