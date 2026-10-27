package dev.monthendbuddy.common.ratelimit;

import dev.monthendbuddy.common.error.RateLimitedException;

/** Token-bucket limiter (LLD §9): in-memory until P5.3, then Redis-backed with in-memory fallback. */
public interface RateLimiter {

	default RateLimitDecision tryConsume(RateLimitBucket bucket, String key) {
		return tryConsume(bucket, key, 1);
	}

	/** Consumes {@code tokens} at once, or nothing if they are not all available. */
	RateLimitDecision tryConsume(RateLimitBucket bucket, String key, long tokens);

	/**
	 * Records usage that has already happened (such as model tokens reported after a call), even beyond the limit;
	 * the bucket then stays empty until it has refilled past the overdraft.
	 */
	void charge(RateLimitBucket bucket, String key, long tokens);

	/** Consumes one token or throws {@link RateLimitedException} (429 + {@code Retry-After}). */
	default void consumeOrThrow(RateLimitBucket bucket, String key) {
		RateLimitDecision decision = tryConsume(bucket, key);
		if (!decision.allowed()) {
			throw new RateLimitedException(decision.retryAfter());
		}
	}

}
