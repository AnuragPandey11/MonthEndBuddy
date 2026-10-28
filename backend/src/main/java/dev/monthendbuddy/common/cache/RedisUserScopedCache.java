package dev.monthendbuddy.common.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.data.redis.core.StringRedisTemplate;

import dev.monthendbuddy.common.UserId;

/**
 * Cache-aside for per-user aggregates in Redis (architecture §11.1). Keys are
 * {@code meb:{env}:u:{userId}:g{generation}:{kind}:{params}}. The generation is read <em>before</em> the loader runs,
 * so a write that commits during the load bumps it and orphans the result instead of storing pre-write figures under
 * the new generation. Any Redis error falls through to the loader (fail-open) and counts in {@code meb.cache.errors}.
 */
class RedisUserScopedCache implements UserScopedCache {

	private static final Logger log = LoggerFactory.getLogger(RedisUserScopedCache.class);

	private final StringRedisTemplate redis;

	private final RedisCacheGenerationService generations;

	private final JsonMapper json;

	private final CacheProperties properties;

	private final Counter hits;

	private final Counter misses;

	private final Counter errors;

	RedisUserScopedCache(StringRedisTemplate redis, RedisCacheGenerationService generations, JsonMapper json,
			CacheProperties properties, MeterRegistry meters) {
		this.redis = redis;
		this.generations = generations;
		this.json = json;
		this.properties = properties;
		this.hits = meters.counter("meb.cache.requests", "result", "hit");
		this.misses = meters.counter("meb.cache.requests", "result", "miss");
		this.errors = meters.counter("meb.cache.errors");
	}

	@Override
	public <T> T getOrLoad(UserId user, String kind, String params, Class<T> type, Supplier<T> loader) {
		if (generations.isStale(user)) {
			return loader.get();
		}
		String key;
		try {
			key = properties.keyPrefix() + ":u:" + user.value() + ":g" + generations.current(user) + ":" + kind + ":"
					+ params;
			String cached = redis.opsForValue().get(key);
			if (cached != null) {
				hits.increment();
				return json.readValue(cached, type);
			}
		}
		catch (RuntimeException e) {
			failed(e);
			return loader.get();
		}
		misses.increment();
		T value = loader.get();
		try {
			redis.opsForValue().set(key, json.writeValueAsString(value), lifetime());
		}
		catch (RuntimeException e) {
			failed(e);
		}
		return value;
	}

	private Duration lifetime() {
		long jitterMs = properties.jitter().toMillis();
		return properties.ttl().plusMillis(jitterMs > 0 ? ThreadLocalRandom.current().nextLong(jitterMs + 1) : 0);
	}

	private void failed(RuntimeException e) {
		errors.increment();
		log.debug("Cache unavailable, reading from the database: {}", e.toString());
	}

}
