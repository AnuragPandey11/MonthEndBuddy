package dev.monthendbuddy.identity.api;

import java.time.Clock;
import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.monthendbuddy.common.error.ForbiddenException;
import dev.monthendbuddy.common.error.UnauthorizedException;
import dev.monthendbuddy.common.ratelimit.RateLimitBucket;
import dev.monthendbuddy.common.ratelimit.RateLimiter;
import dev.monthendbuddy.identity.application.AuthProperties;
import dev.monthendbuddy.identity.application.AuthService;
import dev.monthendbuddy.identity.application.AuthService.AuthTokens;

/**
 * Authentication endpoints (architecture §5.2). The access token goes in the body and is kept in memory by the SPA;
 * the refresh token goes only in an {@code httpOnly} cookie scoped to {@code /api/v1/auth}.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	static final String REFRESH_COOKIE = "meb_refresh";

	static final String CLIENT_HEADER = "X-MEB-Client";

	private final AuthService auth;

	private final RateLimiter rateLimiter;

	private final AuthProperties properties;

	private final Clock clock;

	AuthController(AuthService auth, RateLimiter rateLimiter, AuthProperties properties, Clock clock) {
		this.auth = auth;
		this.rateLimiter = rateLimiter;
		this.properties = properties;
		this.clock = clock;
	}

	@PostMapping("/register")
	ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
		rateLimiter.consumeOrThrow(RateLimitBucket.REGISTER, request.getRemoteAddr());
		AuthTokens tokens = auth.register(new AuthService.RegisterCommand(body.email(), body.password(),
				body.displayName()));
		return withTokens(ResponseEntity.status(HttpStatus.CREATED), tokens);
	}

	@PostMapping("/login")
	ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
		rateLimiter.consumeOrThrow(RateLimitBucket.LOGIN,
				request.getRemoteAddr() + "|" + AuthService.normalizeEmail(body.email()));
		AuthTokens tokens = auth.login(new AuthService.LoginCommand(body.email(), body.password()));
		return withTokens(ResponseEntity.ok(), tokens);
	}

	/** Synthetic demo data, clearly labelled as such in the UI ({@code demo: true} on {@code GET /me}). */
	@PostMapping("/demo")
	ResponseEntity<TokenResponse> demo(HttpServletRequest request) {
		rateLimiter.consumeOrThrow(RateLimitBucket.DEMO_LOGIN, request.getRemoteAddr());
		return withTokens(ResponseEntity.ok(), auth.demoLogin());
	}

	@PostMapping("/refresh")
	ResponseEntity<TokenResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
			@RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
			@RequestHeader(name = CLIENT_HEADER, required = false) String client) {
		requireFirstPartyClient(origin, client);
		if (refreshToken == null) {
			throw new UnauthorizedException("invalid_token");
		}
		return withTokens(ResponseEntity.ok(), auth.refresh(refreshToken));
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
			@RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
			@RequestHeader(name = CLIENT_HEADER, required = false) String client) {
		requireFirstPartyClient(origin, client);
		auth.logout(refreshToken);
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO)).build();
	}

	/**
	 * CSRF defence for the cookie-authenticated endpoints: on top of {@code SameSite=Strict}, require an allowed
	 * {@code Origin} and the custom header, which a cross-site form or image request cannot send.
	 */
	private void requireFirstPartyClient(String origin, String client) {
		if (!"web".equals(client) || origin == null || !properties.allowedOrigins().contains(origin)) {
			throw new ForbiddenException("origin_rejected", "Request is not from an allowed client");
		}
	}

	private ResponseEntity<TokenResponse> withTokens(ResponseEntity.BodyBuilder builder, AuthTokens tokens) {
		Duration maxAge = Duration.between(clock.instant(), tokens.refreshExpiresAt());
		return builder.header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken(), maxAge))
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.body(new TokenResponse(tokens.accessToken(), "Bearer", tokens.expiresInSeconds()));
	}

	private String refreshCookie(String value, Duration maxAge) {
		return ResponseCookie.from(REFRESH_COOKIE, value)
			.httpOnly(true)
			.secure(properties.refreshCookieSecure())
			.sameSite("Strict")
			.path("/api/v1/auth")
			.maxAge(maxAge)
			.build()
			.toString();
	}

	record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(min = 10, max = 128) String password, @NotBlank @Size(max = 100) String displayName) {
	}

	record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {
	}

	record TokenResponse(String accessToken, String tokenType, long expiresIn) {
	}

}
