package dev.monthendbuddy.common.cache;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code meb.cache} (architecture §11.1). {@code keyPrefix} is {@code meb:{env}}; entries live {@code ttl} plus up to
 * {@code jitter}, so a burst of writes does not expire together.
 */
@ConfigurationProperties("meb.cache")
public record CacheProperties(boolean enabled, String keyPrefix, Duration ttl, Duration jitter) {

	public CacheProperties {
		keyPrefix = keyPrefix == null ? "meb:local" : keyPrefix;
		ttl = ttl == null ? Duration.ofMinutes(10) : ttl;
		jitter = jitter == null ? Duration.ofSeconds(60) : jitter;
	}

}
