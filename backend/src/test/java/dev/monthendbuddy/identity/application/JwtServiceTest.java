package dev.monthendbuddy.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.UnauthorizedException;
import dev.monthendbuddy.support.MutableClock;

class JwtServiceTest {

	private static final String SECRET = "unit-test-secret-0123456789abcdef-xyz";

	private final MutableClock clock = new MutableClock();

	private final JwtService jwt = new JwtService(properties(SECRET), clock);

	private final UserId user = new UserId(UUID.randomUUID());

	private static AuthProperties properties(String secret) {
		return new AuthProperties(secret, Duration.ofMinutes(15), Duration.ofDays(14), List.of(), true);
	}

	@Test
	void issuedTokenParsesBackToTheUser() {
		JwtService.AccessToken token = jwt.issue(user);
		assertThat(token.expiresInSeconds()).isEqualTo(900);
		assertThat(jwt.parse(token.value()).id()).isEqualTo(user);
	}

	@Test
	void tokenExpiresExactlyAtItsTtl() {
		String token = jwt.issue(user).value();
		clock.advance(Duration.ofMinutes(15).minusSeconds(1));
		assertThat(jwt.parse(token).id()).isEqualTo(user);
		clock.advance(Duration.ofSeconds(1));
		assertThatThrownBy(() -> jwt.parse(token)).isInstanceOf(UnauthorizedException.class)
			.hasFieldOrPropertyWithValue("code", "token_expired");
	}

	@Test
	void tamperedOrForeignTokensAreInvalid() {
		String token = jwt.issue(user).value();
		String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
		String foreign = new JwtService(properties("another-secret-0123456789abcdef-abc"), clock).issue(user).value();
		for (String bad : List.of(tampered, foreign, "not-a-jwt", "")) {
			assertThatThrownBy(() -> jwt.parse(bad)).isInstanceOf(UnauthorizedException.class)
				.hasFieldOrPropertyWithValue("code", "invalid_token");
		}
	}

	@Test
	void appRefusesToStartWithAShortSecret() {
		assertThatThrownBy(() -> properties("too-short")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("at least 32 bytes");
		assertThatThrownBy(() -> properties(null)).isInstanceOf(IllegalStateException.class);
	}

}
