package dev.monthendbuddy.common.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis client behaviour for the full stack, where the cache or the rate limits use Redis (architecture §11). Redis
 * is optional, so the client must fail fast: while disconnected it rejects commands at once instead of queueing them
 * until the command timeout, and every command has the short timeout from {@code spring.data.redis.timeout}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("${meb.cache.enabled:false} or '${meb.ratelimit.mode:in-memory}' == 'redis'")
class RedisClientConfiguration {

	@Bean
	LettuceClientConfigurationBuilderCustomizer failFastRedisClient() {
		return builder -> builder.clientOptions(ClientOptions.builder()
			.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
			.autoReconnect(true)
			.timeoutOptions(TimeoutOptions.enabled())
			.build());
	}

}
