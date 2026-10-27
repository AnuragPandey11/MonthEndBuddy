package dev.monthendbuddy.agent.api;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import dev.monthendbuddy.agent.application.AgentEventSink;
import dev.monthendbuddy.agent.application.AgentResponse;

/**
 * Streams a turn as Server-Sent Events (architecture §10.7): {@code tool_started}, {@code tool_finished}, then one
 * {@code final} (the same body as the JSON response) or one {@code error}.
 * <p>
 * A client that goes away does not cancel the turn: sends become no-ops, and the orchestrator still finishes and
 * persists the answer, so reopening the conversation shows it.
 */
final class SseAgentEventSink implements AgentEventSink {

	record ToolStarted(String callId, String tool, String label) {
	}

	record ToolFinished(String callId, String tool, boolean ok, long millis) {
	}

	record StreamError(String code, String message, String traceId) {
	}

	private final SseEmitter emitter;

	private final String traceId;

	/** False once the client disconnected or the stream ended; nothing is sent after that. */
	private volatile boolean open = true;

	SseAgentEventSink(SseEmitter emitter, String traceId) {
		this.emitter = emitter;
		this.traceId = traceId;
		emitter.onCompletion(() -> open = false);
		emitter.onError(e -> open = false);
		emitter.onTimeout(() -> {
			failed("turn_timeout", "The copilot took too long to answer. Reopen the conversation in a moment.");
			finish();
		});
	}

	@Override
	public void toolStarted(String callId, String tool, String label) {
		send("tool_started", new ToolStarted(callId, tool, label));
	}

	@Override
	public void toolFinished(String callId, String tool, boolean ok, long millis) {
		send("tool_finished", new ToolFinished(callId, tool, ok, millis));
	}

	@Override
	public void completed(AgentResponse response) {
		send("final", response);
	}

	@Override
	public void failed(String code, String message) {
		send("error", new StreamError(code, message, traceId));
	}

	/** Ends the stream, unless the client or the container already did. */
	void finish() {
		if (open) {
			open = false;
			emitter.complete();
		}
	}

	private void send(String name, Object data) {
		if (!open) {
			return;
		}
		try {
			emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
		}
		catch (IOException | IllegalStateException e) {
			// The client went away (or the stream already ended). The container completes the emitter itself.
			open = false;
		}
	}

}
