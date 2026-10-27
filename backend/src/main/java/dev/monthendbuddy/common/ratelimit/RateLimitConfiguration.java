package dev.monthendbuddy.common.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitConfiguration {

	@Bean
	@ConditionalOnProperty(name = "meb.ratelimit.mode", havingValue = "in-memory", matchIfMissing = true)
	RateLimiter inMemoryRateLimiter(RateLimitProperties properties) {
		return new InMemoryRateLimiter(properties);
	}

}
