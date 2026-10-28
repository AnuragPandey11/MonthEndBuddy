package dev.monthendbuddy.agent.infrastructure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;

import dev.monthendbuddy.agent.llm.LlmGateway;
import dev.monthendbuddy.agent.llm.LlmMessage;
import dev.monthendbuddy.agent.llm.LlmReply;
import dev.monthendbuddy.agent.llm.LlmRequest;
import dev.monthendbuddy.agent.llm.ToolCall;
import dev.monthendbuddy.common.error.ProviderUnavailableException;

/**
 * A fake model for tests and the offline demo (LLD §8.1). Each queued step sees the request (so a test can answer
 * from the tool results it was sent) and returns a reply, or throws to simulate a provider failure. With nothing
 * queued it reports the provider as unavailable, unless it runs the offline demo script: then every question gets
 * one real {@code getMonthlySummary} call and, after {@code demoDelay}, a fixed answer. The delay makes the stream
 * observable end to end (the P4.5 exit check through nginx); the figures come from the tool result's evidence card.
 */
public class ScriptedLlmGateway implements LlmGateway {

	static final String DEMO_ANSWER = "This is the offline demo, so this answer is scripted rather than written by "
			+ "a model. The card below shows this month's figures, read from your own transactions.";

	private final Duration demoDelay;

	private final Deque<Function<LlmRequest, LlmReply>> script = new ConcurrentLinkedDeque<>();

	private final List<LlmRequest> requests = new ArrayList<>();

	/** The test gateway: replies only from the script. */
	public ScriptedLlmGateway() {
		this(null);
	}

	/** {@code demoDelay} non-null turns on the offline demo script for turns with nothing queued. */
	public ScriptedLlmGateway(Duration demoDelay) {
		this.demoDelay = demoDelay;
	}

	public ScriptedLlmGateway then(Function<LlmRequest, LlmReply> step) {
		script.addLast(step);
		return this;
	}

	public ScriptedLlmGateway thenReply(LlmReply reply) {
		return then(request -> reply);
	}

	public ScriptedLlmGateway thenFail() {
		return then(request -> {
			throw new ProviderUnavailableException("Scripted provider failure");
		});
	}

	public synchronized List<LlmRequest> requests() {
		return List.copyOf(requests);
	}

	public synchronized void reset() {
		script.clear();
		requests.clear();
	}

	/** Steps run outside the lock, so a test can block one turn while another request arrives. */
	@Override
	public LlmReply complete(LlmRequest request) {
		Function<LlmRequest, LlmReply> step;
		synchronized (this) {
			requests.add(request);
			step = script.pollFirst();
		}
		if (step == null) {
			if (demoDelay != null) {
				return demo(request);
			}
			throw new ProviderUnavailableException("No scripted reply queued");
		}
		return step.apply(request);
	}

	/** First a summary lookup; once its result is back, a pause and the fixed answer. */
	private LlmReply demo(LlmRequest request) {
		List<LlmMessage> messages = request.messages();
		if (messages.isEmpty() || !(messages.getLast() instanceof LlmMessage.ToolResult)) {
			return new LlmReply(null, List.of(new ToolCall("demo_1", "getMonthlySummary", "{}")), 0, 0,
					"offline-demo");
		}
		try {
			Thread.sleep(demoDelay);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return new LlmReply(DEMO_ANSWER, List.of(), 0, 0, "offline-demo");
	}

	@Override
	public ProviderState state() {
		return ProviderState.AVAILABLE;
	}

}
