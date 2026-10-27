package dev.monthendbuddy.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.test.context.TestPropertySource;

import dev.monthendbuddy.agent.infrastructure.SpringAiLlmGateway;
import dev.monthendbuddy.agent.llm.LlmGateway;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.IntegrationTest;

/**
 * Without {@code GROQ_API_KEY} the real gateway is wired, the app boots, health stays UP, and chat answers
 * {@code PROVIDER_UNAVAILABLE} without calling the provider (architecture §10.2).
 */
@TestPropertySource(properties = { "meb.agent.provider=groq", "spring.ai.openai.api-key=" })
class NoProviderKeyIT extends IntegrationTest {

	@Autowired
	LlmGateway gateway;

	@Autowired
	@Qualifier("aiProviderHealthIndicator")
	HealthIndicator aiProvider;

	@Test
	void bootsAndAnswersProviderUnavailable() {
		assertThat(gateway).isInstanceOf(SpringAiLlmGateway.class);
		assertThat(gateway.state()).isEqualTo(LlmGateway.ProviderState.NOT_CONFIGURED);
		assertThat(aiProvider.health().getStatus()).isEqualTo(Status.UP);

		Response response = api().bearer(api().register().accessToken())
			.post("/api/v1/agent/chat", Map.of("message", "How am I doing?"));

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.json().get("status").asString()).isEqualTo("PROVIDER_UNAVAILABLE");
	}

}
