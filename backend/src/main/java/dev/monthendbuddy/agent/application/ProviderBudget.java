package dev.monthendbuddy.agent.application;

import org.springframework.stereotype.Component;

import dev.monthendbuddy.agent.llm.LlmMessage;
import dev.monthendbuddy.agent.llm.LlmReply;
import dev.monthendbuddy.agent.llm.LlmRequest;
import dev.monthendbuddy.common.error.ProviderUnavailableException;
import dev.monthendbuddy.common.ratelimit.RateLimitBucket;
import dev.monthendbuddy.common.ratelimit.RateLimiter;

/**
 * Keeps all users together under the provider's limits (architecture §11.2): model calls per minute, and tokens per
 * minute, because the free tier allows 8,000 TPM. The budget is checked when a turn starts: over budget, the turn
 * ends at once as {@code PROVIDER_UNAVAILABLE} instead of the provider answering 429. Later calls in a started turn
 * are charged without being refused, possibly into overdraft, so a turn is never cut off after it has gathered
 * evidence; the next turn then waits for the refill. Each call reserves an estimate and settles the reported usage.
 */
@Component
class ProviderBudget {

	private static final String KEY = "provider";

	/** Room for the model's reply in the up-front estimate. */
	private static final int COMPLETION_ALLOWANCE = 400;

	private final RateLimiter limiter;

	ProviderBudget(RateLimiter limiter) {
		this.limiter = limiter;
	}

	/**
	 * Returns the tokens reserved, to pass to {@link #settle}.
	 * @param firstCallOfTurn whether this call starts a turn, the only point at which the budget can refuse
	 */
	long reserve(LlmRequest request, boolean firstCallOfTurn) {
		long estimate = estimateTokens(request);
		if (!firstCallOfTurn) {
			limiter.charge(RateLimitBucket.PROVIDER_GLOBAL, KEY, 1);
			limiter.charge(RateLimitBucket.PROVIDER_TOKENS, KEY, estimate);
			return estimate;
		}
		if (!limiter.tryConsume(RateLimitBucket.PROVIDER_GLOBAL, KEY).allowed()) {
			throw new ProviderUnavailableException("the copilot is busy");
		}
		if (!limiter.tryConsume(RateLimitBucket.PROVIDER_TOKENS, KEY, estimate).allowed()) {
			throw new ProviderUnavailableException("the copilot is busy");
		}
		return estimate;
	}

	void settle(long reserved, LlmReply reply) {
		limiter.charge(RateLimitBucket.PROVIDER_TOKENS, KEY, reply.totalTokens() - reserved);
	}

	/** About four characters per token, plus the tool schemas and the reply. */
	static long estimateTokens(LlmRequest request) {
		long chars = 0;
		for (LlmMessage message : request.messages()) {
			chars += switch (message) {
				case LlmMessage.System s -> s.text().length();
				case LlmMessage.User u -> u.text().length();
				case LlmMessage.Assistant a -> (a.text() == null ? 0 : a.text().length())
						+ a.toolCalls().stream().mapToLong(c -> c.argumentsJson() == null ? 0 : c.argumentsJson().length()).sum();
				case LlmMessage.ToolResult t -> t.json().length();
			};
		}
		long toolSchemas = request.tools().size() * 120L;
		return chars / 4 + toolSchemas + COMPLETION_ALLOWANCE;
	}

}
