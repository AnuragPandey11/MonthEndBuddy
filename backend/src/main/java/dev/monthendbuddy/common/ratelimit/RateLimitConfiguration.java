package dev.monthendbuddy.common.ratelimit;

import io.lettuce.core.RedisClient;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitConfiguration {

	@Bean
	@ConditionalOnProperty(name = "meb.ratelimit.mode", havingValue = "in-memory", matchIfMissing = true)
	RateLimiter inMemoryRateLimiter(RateLimitProperties properties) {
		return new InMemoryRateLimiter(properties);
	}

	/**
	 * Shared limits in Redis (P5.3), on the Lettuce client Spring Boot configured (with its fail-fast options), and the
	 * in-memory limiter as the fallback during an outage.
	 */
	@Bean
	@ConditionalOnProperty(name = "meb.ratelimit.mode", havingValue = "redis")
	RateLimiter redisRateLimiter(RateLimitProperties properties, LettuceConnectionFactory redis,
			@Value("${meb.cache.key-prefix:meb:local}") String prefix, MeterRegistry meters) {
		return new RedisRateLimiter(properties, new InMemoryRateLimiter(properties),
				() -> ((RedisClient) redis.getRequiredNativeClient())
					.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)),
				prefix, meters);
	}

}
