package dev.monthendbuddy.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import dev.monthendbuddy.agent.application.AgentTurnLock;
import dev.monthendbuddy.agent.infrastructure.ScriptedLlmGateway;
import dev.monthendbuddy.agent.llm.LlmReply;
import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.RedisIntegrationTest;

/** Shared rate limits and the turn lock in Redis (architecture §11.2, §11.3, P5.3). */
class RedisRateLimitIT extends RedisIntegrationTest {

	@Autowired
	RateLimiter limiter;

	@Autowired
	AgentTurnLock lock;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	ScriptedLlmGateway llm;

	@Test
	void bucketsLiveInRedisAndRefuseOnceEmpty() {
		String key = "shared-" + UUID.randomUUID();
		assertThat(limiter.tryConsume(RateLimitBucket.LOGIN, key, 10_000).allowed()).isTrue();
		RateLimitDecision refused = limiter.tryConsume(RateLimitBucket.LOGIN, key);
		assertThat(refused.allowed()).isFalse();
		assertThat(refused.retryAfter()).isPositive();
		assertThat(redis.hasKey("meb:local:rl:login:" + key)).as("the bucket every instance shares").isTrue();

		limiter.charge(RateLimitBucket.LOGIN, "charged-" + key, 5);
		assertThat(redis.hasKey("meb:local:rl:login:charged-" + key)).isTrue();
	}

	@Test
	void theTurnLockIsARedisKeyThatOnlyItsOwnerCanRelease() {
		UUID conversation = UUID.randomUUID();
		String key = "meb:local:agentlock:" + conversation;
		assertThat(lock.tryAcquire(conversation, Duration.ofSeconds(30))).isTrue();
		assertThat(redis.hasKey(key)).isTrue();
		assertThat(redis.getExpire(key)).isBetween(1L, 30L);
		assertThat(lock.tryAcquire(conversation, Duration.ofSeconds(30))).isFalse();
		lock.release(conversation);
		assertThat(redis.hasKey(key)).isFalse();

		// This turn's lock expired and another instance took the conversation: releasing must not delete theirs.
		assertThat(lock.tryAcquire(conversation, Duration.ofSeconds(30))).isTrue();
		redis.opsForValue().set(key, "another instance's token", Duration.ofSeconds(30));
		lock.release(conversation);
		assertThat(redis.opsForValue().get(key)).isEqualTo("another instance's token");
		redis.delete(key);
	}

	@Test
	void everyTurnReleasesItsLockWhetherItAnswersOrFails() {
		ApiClient user = api().bearer(api().register().accessToken());
		llm.reset();
		llm.thenReply(new LlmReply("First.", List.of(), 1, 1, "scripted"));
		String id = user.post("/api/v1/agent/chat", Map.of("message", "Start")).json().get("conversationId").asString();
		assertThat(redis.hasKey("meb:local:agentlock:" + id)).isFalse();

		llm.thenFail();
		assertThat(user.post("/api/v1/agent/chat", Map.of("conversationId", id, "message", "Again")).status())
			.isEqualTo(200);
		assertThat(redis.hasKey("meb:local:agentlock:" + id)).as("released after a provider failure").isFalse();

		llm.thenReply(new LlmReply("Third.", List.of(), 1, 1, "scripted"));
		assertThat(user.post("/api/v1/agent/chat", Map.of("conversationId", id, "message", "Once more")).status())
			.isEqualTo(200);
	}

}
