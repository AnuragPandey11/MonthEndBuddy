package dev.monthendbuddy.identity.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.UnauthorizedException;
import dev.monthendbuddy.identity.CurrentUser;

/**
 * Issues and verifies short-lived HS256 access tokens (architecture §5.2). Expiry is checked here against the
 * injected {@link Clock} (no skew: one server both issues and verifies), so tests control time and an expired token
 * gets the distinct {@code token_expired} code.
 */
@Service
public class JwtService {

	private final NimbusJwtEncoder encoder;

	private final NimbusJwtDecoder decoder;

	private final AuthProperties properties;

	private final Clock clock;

	public JwtService(AuthProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		SecretKey key = new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
		this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		// Signature and algorithm are still verified by the decoder; timestamps are checked below.
		this.decoder.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
	}

	public AccessToken issue(UserId user) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(user.toString())
			.issuedAt(now)
			.expiresAt(now.plus(properties.accessTokenTtl()))
			.id(UUID.randomUUID().toString())
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AccessToken(token, properties.accessTokenTtl().toSeconds());
	}

	/** @throws UnauthorizedException {@code token_expired} or {@code invalid_token} */
	public CurrentUser parse(String token) {
		Jwt jwt;
		try {
			jwt = decoder.decode(token);
		}
		catch (JwtException e) {
			throw new UnauthorizedException("invalid_token");
		}
		if (jwt.getExpiresAt() == null || jwt.getSubject() == null) {
			throw new UnauthorizedException("invalid_token");
		}
		if (!clock.instant().isBefore(jwt.getExpiresAt())) {
			throw new UnauthorizedException("token_expired");
		}
		try {
			return new CurrentUser(UserId.of(jwt.getSubject()));
		}
		catch (IllegalArgumentException e) {
			throw new UnauthorizedException("invalid_token");
		}
	}

	public record AccessToken(String value, long expiresInSeconds) {
	}

}
