package dev.monthendbuddy.agent.infrastructure;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;

import dev.monthendbuddy.agent.llm.LlmGateway;
import dev.monthendbuddy.agent.llm.LlmReply;
import dev.monthendbuddy.agent.llm.LlmRequest;
import dev.monthendbuddy.common.error.ProviderUnavailableException;

/**
 * A fake model for tests and the offline demo (LLD §8.1). Each queued step sees the request (so a test can answer
 * from the tool results it was sent) and returns a reply, or throws to simulate a provider failure. With nothing
 * queued it reports the provider as unavailable.
 */
public class ScriptedLlmGateway implements LlmGateway {

	private final Deque<Function<LlmRequest, LlmReply>> script = new ConcurrentLinkedDeque<>();

	private final List<LlmRequest> requests = new ArrayList<>();

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
			throw new ProviderUnavailableException("No scripted reply queued");
		}
		return step.apply(request);
	}

	@Override
	public ProviderState state() {
		return ProviderState.AVAILABLE;
	}

}
