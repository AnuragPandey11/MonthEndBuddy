package dev.monthendbuddy.common.ratelimit;

import java.time.Duration;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.local.LocalBucketBuilder;

/**
 * Per-process token buckets, one per (bucket, key). Idle keys are evicted after their longest window, so a stream of
 * distinct IPs or emails cannot grow memory without bound. Limits are per instance, which is acceptable for the
 * single-instance local deployment; P5.3 adds the shared Redis implementation.
 */
public class InMemoryRateLimiter implements RateLimiter {

	private final RateLimitProperties properties;

	private final Map<RateLimitBucket, Cache<String, Bucket>> buckets = new EnumMap<>(RateLimitBucket.class);

	public InMemoryRateLimiter(RateLimitProperties properties) {
		this.properties = properties;
		for (RateLimitBucket bucket : RateLimitBucket.values()) {
			Duration longest = properties.limitsFor(bucket)
				.stream()
				.map(RateLimitProperties.Limit::period)
				.max(Comparator.naturalOrder())
				.orElse(Duration.ofHours(1));
			buckets.put(bucket, Caffeine.newBuilder().expireAfterAccess(longest).maximumSize(100_000).build());
		}
	}

	@Override
	public RateLimitDecision tryConsume(RateLimitBucket bucket, String key, long tokens) {
		List<RateLimitProperties.Limit> limits = properties.limitsFor(bucket);
		if (limits.isEmpty()) {
			return RateLimitDecision.allow();
		}
		ConsumptionProbe probe = buckets.get(bucket)
			.get(key, k -> newBucket(limits))
			.tryConsumeAndReturnRemaining(tokens);
		return probe.isConsumed() ? RateLimitDecision.allow()
				: RateLimitDecision.reject(Duration.ofNanos(probe.getNanosToWaitForRefill()));
	}

	@Override
	public void charge(RateLimitBucket bucket, String key, long tokens) {
		List<RateLimitProperties.Limit> limits = properties.limitsFor(bucket);
		if (!limits.isEmpty() && tokens > 0) {
			buckets.get(bucket).get(key, k -> newBucket(limits)).consumeIgnoringRateLimits(tokens);
		}
	}

	private static Bucket newBucket(List<RateLimitProperties.Limit> limits) {
		LocalBucketBuilder builder = Bucket.builder();
		for (RateLimitProperties.Limit limit : limits) {
			builder.addLimit(Bandwidth.builder()
				.capacity(limit.capacity())
				.refillIntervally(limit.capacity(), limit.period())
				.build());
		}
		return builder.build();
	}

}
