package dev.monthendbuddy.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.IntegrationTest;

/** Login is limited per client IP and email (architecture §11.2); this context uses a limit of 3 per minute. */
@TestPropertySource(properties = { "meb.ratelimit.limits.login[0].capacity=3",
		"meb.ratelimit.limits.login[0].period=1m" })
class AuthRateLimitIT extends IntegrationTest {

	@Test
	void loginIsRateLimitedPerEmailWithRetryAfter() {
		String email = ApiClient.uniqueEmail();
		Map<String, String> attempt = Map.of("email", email, "password", "wrong-password-1");
		for (int i = 0; i < 3; i++) {
			assertThat(api().post("/api/v1/auth/login", attempt).status()).isEqualTo(401);
		}
		Response limited = api().post("/api/v1/auth/login", attempt);
		assertThat(limited.status()).isEqualTo(429);
		assertThat(limited.json().get("code").asString()).isEqualTo("rate_limited");
		assertThat(Integer.parseInt(limited.header("Retry-After").orElseThrow())).isBetween(1, 60);

		// The same email in a different case shares the bucket; another email does not.
		assertThat(api().post("/api/v1/auth/login", Map.of("email", email.toUpperCase(), "password", "x-x-x-x-x-x"))
			.status()).isEqualTo(429);
		assertThat(api().post("/api/v1/auth/login",
				Map.of("email", ApiClient.uniqueEmail(), "password", "wrong-password-1")).status()).isEqualTo(401);
	}

}
