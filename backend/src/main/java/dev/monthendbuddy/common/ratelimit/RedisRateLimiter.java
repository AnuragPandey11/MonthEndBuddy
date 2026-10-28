package dev.monthendbuddy.common.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConfigurationBuilder;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.api.StatefulRedisConnection;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Token buckets shared through Redis (architecture §11.2, P5.3), so every instance enforces one limit. Redis is
 * optional: while it is unreachable each call falls back to the in-memory limiter (per-instance limits, as in the
 * core stack). After any error the connection is dropped and re-opened by a later call, at most every couple of
 * seconds, so a Redis restart (or a closed client) heals by itself. Errors count in
 * {@code meb.ratelimit.redis.errors}.
 */
class RedisRateLimiter implements RateLimiter {

	private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

	private static final Duration RECONNECT_BACKOFF = Duration.ofSeconds(2);

	private final RateLimitProperties properties;

	private final InMemoryRateLimiter fallback;

	private final Supplier<StatefulRedisConnection<String, byte[]>> connect;

	private final String prefix;

	private final Counter errors;

	private volatile ProxyManager<String> proxies;

	private StatefulRedisConnection<String, byte[]> connection;

	private volatile Instant retryAt = Instant.MIN;

	RedisRateLimiter(RateLimitProperties properties, InMemoryRateLimiter fallback,
			Supplier<StatefulRedisConnection<String, byte[]>> connect, String prefix, MeterRegistry meters) {
		this.properties = properties;
		this.fallback = fallback;
		this.connect = connect;
		this.prefix = prefix;
		this.errors = meters.counter("meb.ratelimit.redis.errors");
	}

	@Override
	public RateLimitDecision tryConsume(RateLimitBucket bucket, String key, long tokens) {
		List<RateLimitProperties.Limit> limits = properties.limitsFor(bucket);
		if (limits.isEmpty()) {
			return RateLimitDecision.allow();
		}
		try {
			ConsumptionProbe probe = withRedis(
					p -> p.builder().build(key(bucket, key), () -> configuration(limits)).tryConsumeAndReturnRemaining(tokens));
			return probe.isConsumed() ? RateLimitDecision.allow()
					: RateLimitDecision.reject(Duration.ofNanos(probe.getNanosToWaitForRefill()));
		}
		catch (RuntimeException e) {
			return fallback.tryConsume(bucket, key, tokens);
		}
	}

	@Override
	public void charge(RateLimitBucket bucket, String key, long tokens) {
		List<RateLimitProperties.Limit> limits = properties.limitsFor(bucket);
		if (limits.isEmpty() || tokens <= 0) {
			return;
		}
		try {
			withRedis(p -> {
				p.builder().build(key(bucket, key), () -> configuration(limits)).consumeIgnoringRateLimits(tokens);
				return null;
			});
		}
		catch (RuntimeException e) {
			fallback.charge(bucket, key, tokens);
		}
	}

	/**
	 * Runs one Redis call. A failure on an existing connection is retried once on a fresh one, since a closed
	 * connection is not an outage; a second failure starts the reconnect backoff and the caller falls back.
	 */
	private <R> R withRedis(Function<ProxyManager<String>, R> call) {
		boolean hadConnection = proxies != null;
		try {
			return call.apply(proxies());
		}
		catch (RuntimeException first) {
			errors.increment();
			drop(false);
			if (!hadConnection) {
				backOff(first);
				throw first;
			}
			try {
				return call.apply(proxies());
			}
			catch (RuntimeException second) {
				errors.increment();
				backOff(second);
				throw second;
			}
		}
	}

	private String key(RateLimitBucket bucket, String key) {
		return prefix + ":rl:" + bucket.name().toLowerCase() + ":" + key;
	}

	private ProxyManager<String> proxies() {
		ProxyManager<String> current = proxies;
		if (current != null) {
			return current;
		}
		synchronized (this) {
			if (proxies == null) {
				if (Instant.now().isBefore(retryAt)) {
					throw new IllegalStateException("Redis unavailable; retrying after " + retryAt);
				}
				try {
					connection = connect.get();
					proxies = Bucket4jLettuce.casBasedBuilder(connection)
						.expirationAfterWrite(
								ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
						.build();
				}
				catch (RuntimeException e) {
					retryAt = Instant.now().plus(RECONNECT_BACKOFF);
					throw e;
				}
			}
			return proxies;
		}
	}

	private void backOff(RuntimeException e) {
		log.debug("Rate limiting in memory while Redis is unavailable: {}", e.toString());
		drop(true);
	}

	/** Drops the connection after an error; with {@code backoff}, no new one is opened for a couple of seconds. */
	private synchronized void drop(boolean backoff) {
		proxies = null;
		if (backoff) {
			retryAt = Instant.now().plus(RECONNECT_BACKOFF);
		}
		if (connection != null) {
			try {
				connection.closeAsync();
			}
			catch (RuntimeException ignored) {
				// Already closed.
			}
			connection = null;
		}
	}

	private static BucketConfiguration configuration(List<RateLimitProperties.Limit> limits) {
		ConfigurationBuilder builder = BucketConfiguration.builder();
		for (RateLimitProperties.Limit limit : limits) {
			builder.addLimit(Bandwidth.builder()
				.capacity(limit.capacity())
				.refillIntervally(limit.capacity(), limit.period())
				.build());
		}
		return builder.build();
	}

}
