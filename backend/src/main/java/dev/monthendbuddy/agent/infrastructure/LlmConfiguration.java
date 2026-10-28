package dev.monthendbuddy.agent.infrastructure;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
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

	/**
	 * With {@code meb.agent.scripted-demo=true} (off by default, and never in tests) a turn with nothing queued runs
	 * the offline demo script instead of reporting the provider as unavailable.
	 */
	@Bean
	@ConditionalOnProperty(name = "meb.agent.provider", havingValue = "scripted")
	ScriptedLlmGateway scriptedLlmGateway(@Value("${meb.agent.scripted-demo:false}") boolean demo,
			@Value("${meb.agent.scripted-demo-delay:2s}") Duration delay) {
		return demo ? new ScriptedLlmGateway(delay) : new ScriptedLlmGateway();
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
