package dev.monthendbuddy.common.ratelimit;

import java.time.Duration;

/** Outcome of one attempt; {@code retryAfter} is zero when allowed. */
public record RateLimitDecision(boolean allowed, Duration retryAfter) {

	public static RateLimitDecision allow() {
		return new RateLimitDecision(true, Duration.ZERO);
	}

	public static RateLimitDecision reject(Duration retryAfter) {
		return new RateLimitDecision(false, retryAfter);
	}

}
