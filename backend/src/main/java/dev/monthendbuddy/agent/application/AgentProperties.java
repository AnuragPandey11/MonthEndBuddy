package dev.monthendbuddy.agent.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code meb.agent} (architecture §10.2): limits on one copilot turn. */
@ConfigurationProperties("meb.agent")
public record AgentProperties(String provider, int maxToolIterations, int maxToolCallsPerTurn, Duration modelTimeout,
		Duration turnTimeout, int historyTurns, int toolResultMaxRows) {

}
