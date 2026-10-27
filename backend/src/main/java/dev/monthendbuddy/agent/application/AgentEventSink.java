package dev.monthendbuddy.agent.application;

/**
 * Progress of one turn. The JSON transport collects it and the SSE transport streams it, so both share one code path
 * (architecture §10.7).
 * <p>
 * Each turn ends with exactly one terminal event: {@link #completed} for every {@code AgentStatus}, including
 * {@code PROVIDER_UNAVAILABLE}, or {@link #failed} when the turn threw and there is no response to send.
 */
public interface AgentEventSink {

	void toolStarted(String callId, String tool, String label);

	void toolFinished(String callId, String tool, boolean ok, long millis);

	void completed(AgentResponse response);

	void failed(String code, String message);

}
