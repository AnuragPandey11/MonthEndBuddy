package dev.monthendbuddy.agent.application;

import java.util.ArrayList;
import java.util.List;

/** Keeps the events in memory, for the JSON transport and for tests. */
public class CollectingAgentEventSink implements AgentEventSink {

	private final List<String> events = new ArrayList<>();

	@Override
	public void toolStarted(String callId, String tool, String label) {
		events.add("tool_started:" + tool);
	}

	@Override
	public void toolFinished(String callId, String tool, boolean ok, long millis) {
		events.add("tool_finished:" + tool + ":" + (ok ? "ok" : "error"));
	}

	@Override
	public void completed(AgentResponse response) {
		events.add("final:" + response.status());
	}

	@Override
	public void failed(String code, String message) {
		events.add("error:" + code);
	}

	public List<String> events() {
		return List.copyOf(events);
	}

}
