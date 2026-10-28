package dev.monthendbuddy.common.cache;

import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** The Redis cache, with {@code meb.cache.enabled=true} (the full stack, P5.2). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CacheProperties.class)
@ConditionalOnProperty(name = "meb.cache.enabled", havingValue = "true")
class RedisCacheConfiguration {

	@Bean
	RedisCacheGenerationService redisCacheGenerationService(StringRedisTemplate redis, CacheProperties properties,
			MeterRegistry meters) {
		return new RedisCacheGenerationService(redis, properties, meters);
	}

	@Bean
	UserScopedCache redisUserScopedCache(StringRedisTemplate redis, RedisCacheGenerationService generations,
			JsonMapper json, CacheProperties properties, MeterRegistry meters) {
		return new RedisUserScopedCache(redis, generations, json, properties, meters);
	}

}
