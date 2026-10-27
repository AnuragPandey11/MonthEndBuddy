package dev.monthendbuddy.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.common.error.RateLimitedException;

class InMemoryRateLimiterTest {

	private final RateLimiter limiter = new InMemoryRateLimiter(new RateLimitProperties("in-memory",
			Map.of(RateLimitBucket.LOGIN, List.of(new RateLimitProperties.Limit(2, Duration.ofMinutes(1)),
					new RateLimitProperties.Limit(3, Duration.ofHours(1))))));

	@Test
	void allowsUpToCapacityThenRejectsWithRetryAfter() {
		assertThat(limiter.tryConsume(RateLimitBucket.LOGIN, "ip|a").allowed()).isTrue();
		assertThat(limiter.tryConsume(RateLimitBucket.LOGIN, "ip|a").allowed()).isTrue();
		RateLimitDecision third = limiter.tryConsume(RateLimitBucket.LOGIN, "ip|a");
		assertThat(third.allowed()).isFalse();
		assertThat(third.retryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1));
	}

	@Test
	void keysAreIndependent() {
		limiter.tryConsume(RateLimitBucket.LOGIN, "ip|a");
		limiter.tryConsume(RateLimitBucket.LOGIN, "ip|a");
		assertThat(limiter.tryConsume(RateLimitBucket.LOGIN, "ip|b").allowed()).isTrue();
	}

	@Test
	void bucketsWithoutLimitsAreUnlimited() {
		for (int i = 0; i < 1000; i++) {
			assertThat(limiter.tryConsume(RateLimitBucket.CSV_IMPORT, "user").allowed()).isTrue();
		}
	}

	@Test
	void consumeOrThrowRaisesRateLimitedException() {
		limiter.consumeOrThrow(RateLimitBucket.LOGIN, "k");
		limiter.consumeOrThrow(RateLimitBucket.LOGIN, "k");
		assertThatThrownBy(() -> limiter.consumeOrThrow(RateLimitBucket.LOGIN, "k"))
			.isInstanceOf(RateLimitedException.class);
	}

}
