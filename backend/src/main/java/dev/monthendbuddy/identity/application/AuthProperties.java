package dev.monthendbuddy.identity.application;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code meb.auth} (architecture §5.2). The app refuses to start without a JWT secret of at least 32 bytes.
 *
 * @param jwtSecret HS256 secret ({@code JWT_SECRET})
 * @param accessTokenTtl access-token lifetime, 15 minutes by default
 * @param refreshTokenTtl sliding refresh-token lifetime, 14 days by default
 * @param allowedOrigins origins accepted on the cookie-authenticated refresh and logout endpoints
 * @param refreshCookieSecure whether the refresh cookie is {@code Secure}; browsers accept it on http://localhost
 */
@ConfigurationProperties("meb.auth")
public record AuthProperties(String jwtSecret, Duration accessTokenTtl, Duration refreshTokenTtl,
		List<String> allowedOrigins, Boolean refreshCookieSecure) {

	public static final int MIN_SECRET_BYTES = 32;

	public AuthProperties {
		if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"meb.auth.jwt-secret (JWT_SECRET) must be set to at least " + MIN_SECRET_BYTES + " bytes");
		}
		accessTokenTtl = (accessTokenTtl == null) ? Duration.ofMinutes(15) : accessTokenTtl;
		refreshTokenTtl = (refreshTokenTtl == null) ? Duration.ofDays(14) : refreshTokenTtl;
		allowedOrigins = (allowedOrigins == null) ? List.of() : List.copyOf(allowedOrigins);
		refreshCookieSecure = (refreshCookieSecure == null) ? Boolean.TRUE : refreshCookieSecure;
	}

}
