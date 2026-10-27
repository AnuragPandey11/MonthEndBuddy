package dev.monthendbuddy.agent.llm;

import dev.monthendbuddy.common.error.ProviderUnavailableException;

/**
 * The model, behind a port (architecture D13). The orchestrator depends only on this; the provider is configuration.
 */
public interface LlmGateway {

	/** @throws ProviderUnavailableException when the provider is not configured, down, or rate limiting us */
	LlmReply complete(LlmRequest request);

	/** For the health indicator and the UI banner: whether a real provider is configured and the circuit is closed. */
	ProviderState state();

	enum ProviderState {

		AVAILABLE, NOT_CONFIGURED, CIRCUIT_OPEN

	}

}
