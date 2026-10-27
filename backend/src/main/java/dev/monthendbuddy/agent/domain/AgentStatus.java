package dev.monthendbuddy.agent.domain;

/** How a copilot turn ended (architecture §10.6, §10.8). */
public enum AgentStatus {

	/** The model gave a final answer. */
	COMPLETE,
	/** The model asked a clarifying question; the turn waits for the user. */
	NEEDS_CLARIFICATION,
	/** The turn timed out; the response holds the evidence gathered so far. */
	PARTIAL,
	/** The provider is not configured, failing or rate limited. Returned with HTTP 200. */
	PROVIDER_UNAVAILABLE,
	/** The iteration or tool-call cap was reached before a final answer. */
	LIMIT_REACHED

}
