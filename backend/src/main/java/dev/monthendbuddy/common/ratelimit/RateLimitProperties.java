package dev.monthendbuddy.common.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code meb.ratelimit}: the backend ({@code in-memory} or, from P5.3, {@code redis}) and, per bucket, a list of
 * limits that must all hold, e.g. 5 per minute and 20 per hour for login. A bucket without limits is unlimited.
 */
@ConfigurationProperties("meb.ratelimit")
public record RateLimitProperties(String mode, Map<RateLimitBucket, List<Limit>> limits) {

	public RateLimitProperties {
		mode = (mode == null) ? "in-memory" : mode;
		limits = (limits == null) ? Map.of() : Map.copyOf(limits);
	}

	public List<Limit> limitsFor(RateLimitBucket bucket) {
		return limits.getOrDefault(bucket, List.of());
	}

	public record Limit(long capacity, Duration period) {
	}

}
