package dev.monthendbuddy.agent.tools;

/** A failure sent back to the model so it can correct itself; {@code field} is null when no argument is at fault. */
public record ToolError(String code, String field, String message) {

	public static final String UNKNOWN_TOOL = "UNKNOWN_TOOL";

	public static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";

	public static final String NOT_FOUND = "NOT_FOUND";

	public static final String TOOL_FAILED = "TOOL_FAILED";

}
