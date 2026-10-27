package dev.monthendbuddy.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import dev.monthendbuddy.support.ApiClient;
import dev.monthendbuddy.support.ApiClient.Response;
import dev.monthendbuddy.support.ApiClient.Session;
import dev.monthendbuddy.support.IntegrationTest;

/** Registration, login, token expiry, refresh rotation, reuse detection and logout (architecture §5.2). */
class AuthFlowIT extends IntegrationTest {

	private static final String PASSWORD = "correct-horse-battery";

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void passwordsAreStoredAsArgon2idAndRefreshTokensOnlyAsHashes() {
		String email = ApiClient.uniqueEmail();
		Session session = api().register(email, PASSWORD);
		String hash = jdbc.queryForObject("select password_hash from users where email = ?", String.class, email);
		assertThat(hash).startsWith("{argon2}$argon2id$").doesNotContain(PASSWORD);
		Integer rawTokenRows = jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?",
				Integer.class, session.refreshToken());
		assertThat(rawTokenRows).isZero();
	}

	@Test
	void registrationReturnsAnAccessTokenAndAHardenedRefreshCookie() {
		Response response = api().post("/api/v1/auth/register",
				Map.of("email", ApiClient.uniqueEmail(), "password", PASSWORD, "displayName", "Asha"));
		assertThat(response.status()).isEqualTo(201);
		assertThat(response.json().get("tokenType").asString()).isEqualTo("Bearer");
		assertThat(response.json().get("expiresIn").asInt()).isEqualTo(900);
		assertThat(response.json().get("accessToken").asString()).isNotBlank();
		assertThat(response.header("Cache-Control")).hasValue("no-store");
		assertThat(response.setCookie("meb_refresh")).hasValueSatisfying(cookie -> assertThat(cookie)
			.contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=1209600"));
	}

	@Test
	void accessTokenGrantsTheProfileAndDefaultSettings() {
		String email = ApiClient.uniqueEmail().toUpperCase();
		Session session = api().register(email, PASSWORD);

		JsonNode me = api().bearer(session.accessToken()).get("/api/v1/me").json();
		assertThat(me.get("email").asString()).isEqualTo(email.toLowerCase());
		assertThat(me.get("demo").asBoolean()).isFalse();

		JsonNode settings = api().bearer(session.accessToken()).get("/api/v1/me/settings").json();
		assertThat(settings.get("baseCurrency").asString()).isEqualTo("INR");
		assertThat(settings.get("timezone").asString()).isEqualTo("Asia/Kolkata");
		assertThat(settings.get("currentSavings").isNull()).isTrue();
		assertThat(settings.get("largeTxnThreshold").get("amount").asString()).isEqualTo("25000.00");
		assertThat(settings.get("emergencyBufferMonths").asInt()).isEqualTo(3);
	}

	@Test
	void protectedEndpointsRequireAValidToken() {
		Response missing = api().get("/api/v1/me");
		assertThat(missing.status()).isEqualTo(401);
		assertThat(missing.header("WWW-Authenticate")).hasValue("Bearer");
		assertThat(missing.header("Content-Type")).hasValue("application/problem+json");
		assertThat(missing.json().get("code").asString()).isEqualTo("unauthorized");

		Response garbage = api().bearer("not-a-token").get("/api/v1/me/settings");
		assertThat(garbage.status()).isEqualTo(401);
		assertThat(garbage.json().get("code").asString()).isEqualTo("invalid_token");
	}

	@Test
	void expiredAccessTokenIsRejectedAndRefreshRecoversTheSession() {
		Session session = api().register();
		clock.advance(Duration.ofMinutes(16));

		Response expired = api().bearer(session.accessToken()).get("/api/v1/me");
		assertThat(expired.status()).isEqualTo(401);
		assertThat(expired.json().get("code").asString()).isEqualTo("token_expired");

		Response refreshed = api().refreshCookie(session.refreshToken()).post("/api/v1/auth/refresh", null);
		assertThat(refreshed.status()).isEqualTo(200);
		String newRefresh = refreshed.cookie("meb_refresh").orElseThrow();
		assertThat(newRefresh).isNotEqualTo(session.refreshToken());
		String newAccess = refreshed.json().get("accessToken").asString();
		assertThat(api().bearer(newAccess).get("/api/v1/me").status()).isEqualTo(200);
	}

	@Test
	void reusingARotatedRefreshTokenRevokesTheWholeFamily() {
		Session session = api().register();
		String first = session.refreshToken();
		String second = api().refreshCookie(first).post("/api/v1/auth/refresh", null).cookie("meb_refresh").orElseThrow();

		Response reuse = api().refreshCookie(first).post("/api/v1/auth/refresh", null);
		assertThat(reuse.status()).isEqualTo(401);
		assertThat(reuse.json().get("code").asString()).isEqualTo("token_reused");

		// The legitimate successor was revoked with the family, so the session is over.
		assertThat(api().refreshCookie(second).post("/api/v1/auth/refresh", null).status()).isEqualTo(401);
	}

	@Test
	void refreshTokenExpiresAfterFourteenDays() {
		Session session = api().register();
		clock.advance(Duration.ofDays(14).plusMinutes(1));
		Response response = api().refreshCookie(session.refreshToken()).post("/api/v1/auth/refresh", null);
		assertThat(response.status()).isEqualTo(401);
		assertThat(response.json().get("code").asString()).isEqualTo("token_expired");
	}

	@Test
	void cookieEndpointsRequireTheAllowedOriginAndClientHeader() {
		Session session = api().register();
		Response noClientHeader = api().header("Cookie", "meb_refresh=" + session.refreshToken())
			.header("Origin", ApiClient.ORIGIN)
			.post("/api/v1/auth/refresh", null);
		Response foreignOrigin = api().header("Cookie", "meb_refresh=" + session.refreshToken())
			.header("Origin", "https://evil.example")
			.header("X-MEB-Client", "web")
			.post("/api/v1/auth/refresh", null);
		Response noOrigin = api().header("Cookie", "meb_refresh=" + session.refreshToken())
			.header("X-MEB-Client", "web")
			.post("/api/v1/auth/logout", null);
		for (Response response : new Response[] { noClientHeader, foreignOrigin, noOrigin }) {
			assertThat(response.status()).isEqualTo(403);
			assertThat(response.json().get("code").asString()).isEqualTo("origin_rejected");
		}
		// None of those calls consumed the token.
		assertThat(api().refreshCookie(session.refreshToken()).post("/api/v1/auth/refresh", null).status())
			.isEqualTo(200);
	}

	@Test
	void logoutRevokesTheSessionAndClearsTheCookie() {
		Session session = api().register();
		Response logout = api().refreshCookie(session.refreshToken()).post("/api/v1/auth/logout", null);
		assertThat(logout.status()).isEqualTo(204);
		assertThat(logout.setCookie("meb_refresh")).hasValueSatisfying(c -> assertThat(c).contains("Max-Age=0"));
		assertThat(api().refreshCookie(session.refreshToken()).post("/api/v1/auth/refresh", null).status())
			.isEqualTo(401);
		// Logging out again is harmless.
		assertThat(api().refreshCookie(session.refreshToken()).post("/api/v1/auth/logout", null).status())
			.isEqualTo(204);
	}

	@Test
	void loginWorksCaseInsensitivelyAndReturnsANewSession() {
		String email = ApiClient.uniqueEmail();
		api().register(email, PASSWORD);
		Response login = api().post("/api/v1/auth/login", Map.of("email", "  " + email.toUpperCase(), "password",
				PASSWORD));
		assertThat(login.status()).isEqualTo(200);
		assertThat(login.cookie("meb_refresh")).isPresent();
		assertThat(api().bearer(login.json().get("accessToken").asString()).get("/api/v1/me").status())
			.isEqualTo(200);
	}

	@Test
	void unknownEmailAndWrongPasswordAreIndistinguishable() {
		String email = ApiClient.uniqueEmail();
		api().register(email, PASSWORD);
		Response wrongPassword = api().post("/api/v1/auth/login", Map.of("email", email, "password", "wrong-password-1"));
		Response unknownEmail = api().post("/api/v1/auth/login",
				Map.of("email", ApiClient.uniqueEmail(), "password", "wrong-password-1"));
		assertThat(wrongPassword.status()).isEqualTo(401).isEqualTo(unknownEmail.status());
		assertThat(withoutTraceId(wrongPassword)).isEqualTo(withoutTraceId(unknownEmail));
		assertThat(wrongPassword.json().get("code").asString()).isEqualTo("invalid_credentials");
	}

	@Test
	void registrationValidatesInputAndRejectsDuplicateEmails() {
		Response badEmail = api().post("/api/v1/auth/register",
				Map.of("email", "not-an-email", "password", PASSWORD, "displayName", "X"));
		assertThat(badEmail.status()).isEqualTo(422);
		assertThat(badEmail.json().get("errors").get(0).get("field").asString()).isEqualTo("email");

		Response shortPassword = api().post("/api/v1/auth/register",
				Map.of("email", ApiClient.uniqueEmail(), "password", "short", "displayName", "X"));
		assertThat(shortPassword.status()).isEqualTo(422);

		Response commonPassword = api().post("/api/v1/auth/register",
				Map.of("email", ApiClient.uniqueEmail(), "password", "password1234", "displayName", "X"));
		assertThat(commonPassword.status()).isEqualTo(422);
		assertThat(commonPassword.json().get("errors").get(0).get("message").asString()).isEqualTo("is too common");

		String email = ApiClient.uniqueEmail();
		api().register(email, PASSWORD);
		Response duplicate = api().post("/api/v1/auth/register",
				Map.of("email", email.toUpperCase(), "password", PASSWORD, "displayName", "X"));
		assertThat(duplicate.status()).isEqualTo(409);
		assertThat(duplicate.json().get("code").asString()).isEqualTo("email_taken");
	}

	private static JsonNode withoutTraceId(Response response) {
		ObjectNode body = (ObjectNode) response.json();
		body.remove("traceId");
		return body;
	}

}
