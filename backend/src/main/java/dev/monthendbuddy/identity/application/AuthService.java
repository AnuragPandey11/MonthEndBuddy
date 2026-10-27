package dev.monthendbuddy.identity.application;

import java.time.Instant;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.monthendbuddy.common.UserId;
import dev.monthendbuddy.common.error.ConflictException;
import dev.monthendbuddy.common.error.NotFoundException;
import dev.monthendbuddy.common.error.UnauthorizedException;
import dev.monthendbuddy.identity.DemoAccounts;
import dev.monthendbuddy.identity.domain.User;
import dev.monthendbuddy.identity.infrastructure.UserRepository;

/**
 * Registration, login, token refresh and logout (architecture §5, LLD §3). Login failures are indistinguishable:
 * unknown email and wrong password both cost one password check and return {@code invalid_credentials}.
 */
@Service
public class AuthService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final PasswordPolicy passwordPolicy;

	private final JwtService jwt;

	private final RefreshTokenService refreshTokens;

	private final UserSettingsService settings;

	/** Hash of a random password, checked when the email is unknown so both failure paths take the same time. */
	private final String dummyHash;

	public AuthService(UserRepository users, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
			JwtService jwt, RefreshTokenService refreshTokens, UserSettingsService settings) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.passwordPolicy = passwordPolicy;
		this.jwt = jwt;
		this.refreshTokens = refreshTokens;
		this.settings = settings;
		this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-" + System.nanoTime());
	}

	public static String normalizeEmail(String email) {
		return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	@Transactional
	public AuthTokens register(RegisterCommand command) {
		String email = normalizeEmail(command.email());
		passwordPolicy.check(command.password(), email);
		if (DemoAccounts.DEMO_EMAIL.equals(email) || users.existsByEmail(email)) {
			throw emailTaken();
		}
		User user;
		try {
			user = users.saveAndFlush(User.register(email, passwordEncoder.encode(command.password()),
					command.displayName().trim()));
		}
		catch (DataIntegrityViolationException e) {
			throw emailTaken();
		}
		settings.createDefaults(user.userId());
		return issueTokens(user.userId());
	}

	@Transactional
	public AuthTokens login(LoginCommand command) {
		User user = users.findByEmail(normalizeEmail(command.email())).orElse(null);
		if (user == null) {
			passwordEncoder.matches(command.password(), dummyHash);
			throw new UnauthorizedException("invalid_credentials");
		}
		if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
			throw new UnauthorizedException("invalid_credentials");
		}
		if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
			user.changePassword(passwordEncoder.encode(command.password()));
		}
		return issueTokens(user.userId());
	}

	/** Signs in to the shared demo account; 404 when demo mode is off or not seeded yet (architecture §5.2). */
	@Transactional
	public AuthTokens demoLogin() {
		User demo = users.findByEmail(DemoAccounts.DEMO_EMAIL)
			.filter(User::isDemo)
			.orElseThrow(() -> new NotFoundException("Demo account"));
		return issueTokens(demo.userId());
	}

	public AuthTokens refresh(String rawRefreshToken) {
		RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken);
		JwtService.AccessToken access = jwt.issue(rotation.userId());
		return new AuthTokens(access.value(), access.expiresInSeconds(), rotation.refreshToken().raw(),
				rotation.refreshToken().expiresAt());
	}

	public void logout(String rawRefreshToken) {
		refreshTokens.revokeFamilyOf(rawRefreshToken);
	}

	AuthTokens issueTokens(UserId user) {
		JwtService.AccessToken access = jwt.issue(user);
		RefreshTokenService.IssuedRefreshToken refresh = refreshTokens.issue(user);
		return new AuthTokens(access.value(), access.expiresInSeconds(), refresh.raw(), refresh.expiresAt());
	}

	private static ConflictException emailTaken() {
		return new ConflictException("email_taken", "An account with this email already exists");
	}

	public record RegisterCommand(String email, String password, String displayName) {
	}

	public record LoginCommand(String email, String password) {
	}

	public record AuthTokens(String accessToken, long expiresInSeconds, String refreshToken,
			Instant refreshExpiresAt) {
	}

}
