package dev.monthendbuddy.agent.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.monthendbuddy.agent.llm.LlmGateway;

/**
 * The scripted gateway ({@code meb.agent.provider=scripted}) and the provider health indicator. The real gateway,
 * {@link SpringAiLlmGateway}, registers itself, so Spring AI types stay inside that one class.
 */
@Configuration(proxyBeanMethods = false)
class LlmConfiguration {

	@Bean
	@ConditionalOnProperty(name = "meb.agent.provider", havingValue = "scripted")
	ScriptedLlmGateway scriptedLlmGateway() {
		return new ScriptedLlmGateway();
	}

	/**
	 * Reports whether the copilot can answer, for the UI banner. Always UP: the provider is not a readiness dependency
	 * (architecture §16); without it the rest of the app works normally.
	 */
	@Bean
	HealthIndicator aiProviderHealthIndicator(LlmGateway gateway) {
		return () -> Health.up().withDetail("provider", gateway.state().name()).build();
	}

}
