package dev.monthendbuddy.agent.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

/**
 * One turn at a time per conversation (architecture §10.8). In memory for the single-instance deployment; a lock
 * left by a crashed request expires after its TTL. Phase 5 can move it to Redis {@code SET NX}.
 */
@Component
public class AgentTurnLock {

	private final ConcurrentMap<UUID, Instant> held = new ConcurrentHashMap<>();

	private final Clock clock;

	AgentTurnLock(Clock clock) {
		this.clock = clock;
	}

	public boolean tryAcquire(UUID conversationId, Duration ttl) {
		Instant now = clock.instant();
		Instant until = now.plus(ttl);
		return held.compute(conversationId,
				(id, existing) -> existing == null || !existing.isAfter(now) ? until : existing) == until;
	}

	public void release(UUID conversationId) {
		held.remove(conversationId);
	}

}
