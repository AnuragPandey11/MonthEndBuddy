package dev.monthendbuddy.support;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests with Redis on (P5.2, P5.3): the cache, the shared rate limits and the turn lock, on the same
 * context as {@link IntegrationTest} plus a Testcontainers Redis. Its own cached context, so the core-mode suite is
 * untouched.
 */
@Import(RedisTestcontainersConfiguration.class)
@TestPropertySource(properties = { "meb.cache.enabled=true", "meb.ratelimit.mode=redis" })
public abstract class RedisIntegrationTest extends IntegrationTest {

}
