package dev.monthendbuddy.common.cache;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.redis.core.StringRedisTemplate;

import dev.monthendbuddy.common.UserId;

/**
 * The per-user generation counter in Redis, {@code meb:{env}:u:{userId}:gen}, an {@code INCR} counter with no TTL
 * (architecture §11.1). A failed bump never reaches the request (it runs after commit, when the write is already
 * done); instead the user is marked stale in this process, and their reads skip the cache until a bump succeeds or
 * the longest entry lifetime has passed, so a Redis blip can never serve pre-write figures.
 */
class RedisCacheGenerationService implements CacheGenerationService {

	private static final Logger log = LoggerFactory.getLogger(RedisCacheGenerationService.class);

	private final StringRedisTemplate redis;

	private final String prefix;

	private final Counter errors;

	private final Cache<UserId, Boolean> stale;

	RedisCacheGenerationService(StringRedisTemplate redis, CacheProperties properties, MeterRegistry meters) {
		this.redis = redis;
		this.prefix = properties.keyPrefix();
		this.errors = meters.counter("meb.cache.errors");
		Duration longest = properties.ttl().plus(properties.jitter());
		this.stale = Caffeine.newBuilder().expireAfterWrite(longest).maximumSize(100_000).build();
	}

	String key(UserId user) {
		return prefix + ":u:" + user.value() + ":gen";
	}

	@Override
	public long current(UserId user) {
		String value = redis.opsForValue().get(key(user));
		return value == null ? 0 : Long.parseLong(value);
	}

	@Override
	public void bump(UserId user) {
		try {
			redis.opsForValue().increment(key(user));
			stale.invalidate(user);
		}
		catch (RuntimeException e) {
			errors.increment();
			stale.put(user, Boolean.TRUE);
			log.warn("Cache generation bump failed for a user; their reads bypass the cache for now: {}",
					e.toString());
		}
	}

	/** True while a failed bump may have left pre-write entries readable. */
	boolean isStale(UserId user) {
		return stale.getIfPresent(user) != null;
	}

}
