package dev.monthendbuddy.agent.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * One turn at a time per conversation (architecture §10.8, §11.3). With {@code meb.ratelimit.mode=redis} the lock is
 * {@code SET meb:{env}:agentlock:{conversationId} <token> NX EX <ttl>}, shared by every instance; it is released with a
 * compare-and-delete on this turn's token, so a turn whose lock already expired cannot delete the next turn's lock.
 * Without Redis, or while it is unreachable, the lock is held in this process, as in the core stack. A lock left by a
 * crashed request expires after its TTL either way.
 */
@Component
public class AgentTurnLock {

	private static final Logger log = LoggerFactory.getLogger(AgentTurnLock.class);

	private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>(
			"if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
			Long.class);

	private final ConcurrentMap<UUID, Instant> held = new ConcurrentHashMap<>();

	/** The token of each Redis lock this process holds, for the compare-and-delete. */
	private final ConcurrentMap<UUID, String> tokens = new ConcurrentHashMap<>();

	private final Clock clock;

	private final StringRedisTemplate redis;

	private final String prefix;

	AgentTurnLock(Clock clock, ObjectProvider<StringRedisTemplate> redis,
			@Value("${meb.ratelimit.mode:in-memory}") String mode,
			@Value("${meb.cache.key-prefix:meb:local}") String prefix) {
		this.clock = clock;
		this.redis = "redis".equals(mode) ? redis.getIfAvailable() : null;
		this.prefix = prefix;
	}

	public boolean tryAcquire(UUID conversationId, Duration ttl) {
		if (redis != null) {
			String token = UUID.randomUUID().toString();
			try {
				boolean acquired = Boolean.TRUE
					.equals(redis.opsForValue().setIfAbsent(key(conversationId), token, ttl));
				if (acquired) {
					tokens.put(conversationId, token);
				}
				return acquired;
			}
			catch (RuntimeException e) {
				log.debug("Turn lock in memory while Redis is unavailable: {}", e.toString());
			}
		}
		Instant now = clock.instant();
		Instant until = now.plus(ttl);
		return held.compute(conversationId,
				(id, existing) -> existing == null || !existing.isAfter(now) ? until : existing) == until;
	}

	public void release(UUID conversationId) {
		held.remove(conversationId);
		String token = tokens.remove(conversationId);
		if (token != null && redis != null) {
			try {
				redis.execute(RELEASE, List.of(key(conversationId)), token);
			}
			catch (RuntimeException e) {
				// The lock expires on its TTL.
				log.debug("Turn lock release failed; it expires on its own: {}", e.toString());
			}
		}
	}

	private String key(UUID conversationId) {
		return prefix + ":agentlock:" + conversationId;
	}

}
