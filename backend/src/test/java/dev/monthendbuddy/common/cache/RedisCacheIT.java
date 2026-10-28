package dev.monthendbuddy.common.cache;

import static dev.monthendbuddy.transactions.TransactionFixtures.SALARY;
import static dev.monthendbuddy.transactions.TransactionFixtures.transaction;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.RedisIntegrationTest;
import dev.monthendbuddy.transactions.TransactionFixtures;

/** The Redis cache (architecture §11.1, P5.2): hits, invalidation by generation, read ordering, JSON round trips. */
class RedisCacheIT extends RedisIntegrationTest {

	@Autowired
	MeterRegistry meters;

	@Autowired
	UserScopedCache cache;

	@Autowired
	RedisCacheGenerationService generations;

	private final YearMonth month = YearMonth.from(TransactionFixtures.today());

	private ApiClient as;

	@BeforeEach
	void user() {
		as = api().bearer(api().register().accessToken());
		as.post("/api/v1/transactions", transaction("INCOME", "95000", month.atDay(1), "Salary", null, SALARY));
		as.post("/api/v1/transactions", TransactionFixtures.expense("1200", month.atDay(1), "Groceries"));
	}

	private double count(String result) {
		return meters.counter("meb.cache.requests", "result", result).count();
	}

	@Test
	void aRepeatedReadIsAHitAndAWriteMakesTheNextReadMiss() {
		String summary = "/api/v1/analytics/summary?month=" + month;
		as.get(summary);
		double hits = count("hit");
		String cached = as.get(summary).body();
		assertThat(count("hit")).isEqualTo(hits + 1);

		as.post("/api/v1/transactions", TransactionFixtures.expense("800", month.atDay(1), "Lunch"));
		double misses = count("miss");
		String fresh = as.get(summary).body();
		assertThat(count("miss")).as("the write bumped the generation").isEqualTo(misses + 1);
		assertThat(fresh).isNotEqualTo(cached).contains("\"2000.00\"");
	}

	@Test
	void everyCachedKindReadsBackExactlyAsComputed() {
		for (String path : new String[] { "/api/v1/analytics/summary?month=" + month,
				"/api/v1/analytics/categories?from=" + month.atDay(1) + "&to=" + month.atEndOfMonth(),
				"/api/v1/analytics/trends?months=6",
				"/api/v1/analytics/compare?current=" + month + "&previous=" + month.minusMonths(1) }) {
			String computed = as.get(path).body();
			double hits = count("hit");
			assertThat(as.get(path).body()).as(path).isEqualTo(computed);
			assertThat(count("hit")).as(path + " came from Redis").isEqualTo(hits + 1);
		}
	}

	@Test
	void aWriteThatCommitsDuringALoadNeverLeavesPreWriteFiguresUnderTheNewGeneration() throws Exception {
		UserId user = new UserId(UUID.randomUUID());
		CountDownLatch loading = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<String> slowRead = CompletableFuture.supplyAsync(() -> cache.getOrLoad(user, "summary",
				"race", String.class, () -> {
					loading.countDown();
					await(release);
					return "before the write";
				}));
		assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
		generations.bumpAfterCommit(user); // the write commits while the read is still loading
		release.countDown();
		assertThat(slowRead.get(5, TimeUnit.SECONDS)).isEqualTo("before the write");

		assertThat(cache.getOrLoad(user, "summary", "race", String.class, () -> "after the write"))
			.isEqualTo("after the write");
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
