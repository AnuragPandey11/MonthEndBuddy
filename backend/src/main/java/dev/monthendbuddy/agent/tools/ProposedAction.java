package dev.monthendbuddy.agent.tools;

import java.util.Map;

/**
 * A change the copilot suggests but never makes (D4). The UI shows it as a button that opens a pre-filled form;
 * saving the form calls the normal REST endpoint, so the user confirms every write.
 */
public record ProposedAction(ActionType type, Map<String, Object> payload, String label) {

	public enum ActionType {

		CREATE_BUDGET, UPDATE_BUDGET, CREATE_GOAL, UPDATE_GOAL_CONTRIBUTION

	}

}
