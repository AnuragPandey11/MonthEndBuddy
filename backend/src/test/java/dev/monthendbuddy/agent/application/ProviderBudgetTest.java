package dev.monthendbuddy.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.agent.llm.LlmMessage;
import dev.monthendbuddy.agent.llm.LlmReply;
import dev.monthendbuddy.agent.llm.LlmRequest;
import dev.monthendbuddy.common.error.ProviderUnavailableException;
import dev.monthendbuddy.common.ratelimit.InMemoryRateLimiter;
import dev.monthendbuddy.common.ratelimit.RateLimitBucket;
import dev.monthendbuddy.common.ratelimit.RateLimitProperties;
import dev.monthendbuddy.common.ratelimit.RateLimitProperties.Limit;

class ProviderBudgetTest {

	private final ProviderBudget budget = new ProviderBudget(new InMemoryRateLimiter(new RateLimitProperties(
			"in-memory", Map.of(RateLimitBucket.PROVIDER_GLOBAL, List.of(new Limit(100, Duration.ofMinutes(1))),
					RateLimitBucket.PROVIDER_TOKENS, List.of(new Limit(3000, Duration.ofMinutes(1)))))));

	private final LlmRequest request = new LlmRequest(List.of(new LlmMessage.User("x".repeat(4000))), List.of());

	@Test
	void aStartedTurnIsNeverCutOffButTheNextTurnWaits() {
		long reserved = budget.reserve(request, true);
		budget.settle(reserved, new LlmReply("", List.of(), 2000, 200, "m"));
		// Over budget now, but calls inside the running turn still go through.
		for (int i = 0; i < 3; i++) {
			budget.settle(budget.reserve(request, false), new LlmReply("", List.of(), 2000, 200, "m"));
		}
		assertThatThrownBy(() -> budget.reserve(request, true)).isInstanceOf(ProviderUnavailableException.class);
	}

	@Test
	void estimateCoversTextToolsAndTheReply() {
		assertThat(ProviderBudget.estimateTokens(request)).isEqualTo(1000 + 400);
	}

}
