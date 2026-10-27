package dev.monthendbuddy.agent.tools;

import java.util.Set;

/**
 * One read-only capability the model may call (LLD §8.2). Each tool is a thin adapter over the same service method
 * as the matching REST endpoint, so the dashboard and the copilot never disagree (D3). Implementations must not
 * write data, run SQL, fetch URLs or read the security context.
 *
 * @param <I> the input record: Bean Validation annotations, no {@code userId} field (D2)
 */
public interface FinanceTool<I> {

	String name();

	/** Shown to the model; say what the tool returns and when to use it. */
	String description();

	Class<I> inputType();

	/** Shown in the UI while the tool runs, e.g. "Reading your September spending". */
	String statusLabel();

	/**
	 * Top-level fields of the result that only the UI needs (such as a chart series). They stay in the evidence but
	 * are not sent to the model, which saves tokens without changing any figure it can quote.
	 */
	default Set<String> uiOnlyFields() {
		return Set.of();
	}

	ToolResult execute(I input, ToolContext context);

}
