package dev.monthendbuddy.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.UnauthorizedException;
import dev.monthendbuddy.identity.domain.RefreshToken;
import dev.monthendbuddy.identity.infrastructure.RefreshTokenRepository;

/**
 * Opaque refresh tokens with rotation and reuse detection (architecture §5.2, LLD §3). Only SHA-256 hashes are
 * stored. Presenting an already-rotated token revokes its whole family, and that revocation must be committed even
 * though the call fails, hence {@code noRollbackFor}.
 */
@Service
public class RefreshTokenService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final RefreshTokenRepository tokens;

	private final AuthProperties properties;

	private final Clock clock;

	public RefreshTokenService(RefreshTokenRepository tokens, AuthProperties properties, Clock clock) {
		this.tokens = tokens;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public IssuedRefreshToken issue(UserId user) {
		Instant now = clock.instant();
		String raw = newRawToken();
		Instant expiresAt = now.plus(properties.refreshTokenTtl());
		tokens.save(RefreshToken.startFamily(user, hash(raw), now, expiresAt));
		return new IssuedRefreshToken(raw, expiresAt);
	}

	/** @throws UnauthorizedException {@code invalid_token}, {@code token_expired} or {@code token_reused} */
	@Transactional(noRollbackFor = UnauthorizedException.class)
	public Rotation rotate(String raw) {
		RefreshToken current = find(raw).orElseThrow(() -> new UnauthorizedException("invalid_token"));
		Instant now = clock.instant();
		if (current.isRevoked()) {
			tokens.revokeFamily(current.getFamilyId(), now);
			throw new UnauthorizedException("token_reused");
		}
		if (current.isExpired(now)) {
			throw new UnauthorizedException("token_expired");
		}
		String nextRaw = newRawToken();
		Instant expiresAt = now.plus(properties.refreshTokenTtl());
		RefreshToken next = tokens.save(current.successor(hash(nextRaw), now, expiresAt));
		current.rotate(next, now);
		return new Rotation(current.getUserId(), new IssuedRefreshToken(nextRaw, expiresAt));
	}

	/** Logout: revokes the presented token's family. Unknown tokens are ignored, so logout is idempotent. */
	@Transactional
	public void revokeFamilyOf(String raw) {
		find(raw).ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), clock.instant()));
	}

	private Optional<RefreshToken> find(String raw) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		return tokens.findByTokenHash(hash(raw));
	}

	private static String newRawToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String raw) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public record IssuedRefreshToken(String raw, Instant expiresAt) {
	}

	public record Rotation(UserId userId, IssuedRefreshToken refreshToken) {
	}

}
