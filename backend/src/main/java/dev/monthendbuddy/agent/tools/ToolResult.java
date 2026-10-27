package dev.monthendbuddy.agent.tools;

import java.util.List;

import dev.monthendbuddy.common.Assumption;

/** What a tool returns: typed {@code data} with its provenance and assumptions, or an {@code error}. */
public record ToolResult(Object data, Provenance provenance, List<Assumption> assumptions,
		List<ProposedAction> proposedActions, ToolError error) {

	public ToolResult {
		assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
		proposedActions = proposedActions == null ? List.of() : List.copyOf(proposedActions);
	}

	public static ToolResult ok(Object data, Provenance provenance, List<Assumption> assumptions) {
		return new ToolResult(data, provenance, assumptions, List.of(), null);
	}

	public static ToolResult error(String code, String field, String message) {
		return new ToolResult(null, null, List.of(), List.of(), new ToolError(code, field, message));
	}

	public ToolResult withProposedActions(List<ProposedAction> actions) {
		return new ToolResult(data, provenance, assumptions, actions, error);
	}

	public boolean ok() {
		return error == null;
	}

}
