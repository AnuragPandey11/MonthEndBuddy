package dev.monthendbuddy.common.cache;

import static dev.monthendbuddy.transactions.TransactionFixtures.SALARY;
import static dev.monthendbuddy.transactions.TransactionFixtures.transaction;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.YearMonth;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import dev.monthendbuddy.agent.application.AgentTurnLock;
import dev.monthendbuddy.common.ratelimit.RateLimitBucket;
import dev.monthendbuddy.common.ratelimit.RateLimiter;
import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.IntegrationTest;
import dev.monthendbuddy.transactions.TransactionFixtures;

/**
 * Redis is optional (architecture §11): with it unreachable, figures stay correct and requests stay fast, and a
 * generation bump that failed during the outage never lets pre-write figures back out once Redis returns. This class
 * has its own Redis container, so pausing it cannot disturb other tests.
 */
@Import(RedisOutageIT.OwnRedis.class)
@TestPropertySource(properties = { "meb.cache.enabled=true", "meb.ratelimit.mode=redis" })
class RedisOutageIT extends IntegrationTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class OwnRedis {

		@Bean
		@ServiceConnection(name = "redis")
		GenericContainer<?> outageRedis() {
			return new GenericContainer<>(DockerImageName.parse(System.getProperty("redis.image", "redis:7.4-alpine")))
				.withExposedPorts(6379);
		}

	}

	@Autowired
	@Qualifier("outageRedis")
	GenericContainer<?> redis;

	@Autowired
	MeterRegistry meters;

	@Autowired
	RateLimiter limiter;

	@Autowired
	AgentTurnLock lock;

	private final YearMonth month = YearMonth.from(TransactionFixtures.today());

	@AfterEach
	void resume() {
		if (Boolean.TRUE.equals(redis.getDockerClient().inspectContainerCmd(redis.getContainerId()).exec().getState().getPaused())) {
			redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
		}
	}

	private void pause() {
		redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();
	}

	private void unpause() {
		redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
	}

	private String expenses(ApiClient as) {
		return as.get("/api/v1/analytics/summary?month=" + month).json().get("expenses").get("amount").asString();
	}

	@Test
	void figuresStayCorrectAndFastWhileRedisIsDownAndAfterItReturns() {
		ApiClient as = api().bearer(api().register().accessToken());
		as.post("/api/v1/transactions", transaction("INCOME", "95000", month.atDay(1), "Salary", null, SALARY));
		as.post("/api/v1/transactions", TransactionFixtures.expense("1000", month.atDay(1), "Groceries"));
		assertThat(expenses(as)).isEqualTo("1000.00");
		assertThat(expenses(as)).as("now cached").isEqualTo("1000.00");
		double errors = meters.counter("meb.cache.errors").count();

		pause();
		long started = System.nanoTime();
		as.post("/api/v1/transactions", TransactionFixtures.expense("500", month.atDay(1), "Lunch"));
		assertThat(expenses(as)).as("read from the database").isEqualTo("1500.00");
		Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
		assertThat(elapsed).as("a write and a read with Redis down").isLessThan(Duration.ofSeconds(3));
		assertThat(meters.counter("meb.cache.errors").count()).isGreaterThan(errors);

		unpause();
		// The bump failed during the outage, so the ₹1,000 entry is still in Redis under the current generation;
		// reads skip the cache for this user instead of serving it.
		assertThat(expenses(as)).isEqualTo("1500.00");
		as.post("/api/v1/transactions", TransactionFixtures.expense("250", month.atDay(1), "Snack"));
		assertThat(expenses(as)).as("a successful bump puts the cache back in use").isEqualTo("1750.00");
		assertThat(expenses(as)).isEqualTo("1750.00");
	}

	@Test
	void rateLimitsAndTheTurnLockFallBackToMemoryWhileRedisIsDown() {
		String key = "outage-" + java.util.UUID.randomUUID();
		java.util.UUID conversation = java.util.UUID.randomUUID();
		pause();
		long started = System.nanoTime();
		assertThat(limiter.tryConsume(RateLimitBucket.LOGIN, key).allowed()).isTrue();
		assertThat(lock.tryAcquire(conversation, Duration.ofSeconds(30))).isTrue();
		assertThat(lock.tryAcquire(conversation, Duration.ofSeconds(30))).as("still one turn at a time").isFalse();
		lock.release(conversation);
		assertThat(lock.tryAcquire(conversation, Duration.ofSeconds(30))).isTrue();
		lock.release(conversation);
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
		assertThat(meters.counter("meb.ratelimit.redis.errors").count()).isPositive();
	}

}
