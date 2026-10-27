package dev.monthendbuddy.identity.infrastructure;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import dev.monthendbuddy.common.error.UnauthorizedException;
import dev.monthendbuddy.identity.CurrentUser;
import dev.monthendbuddy.identity.application.JwtService;

/**
 * Resolves {@code Authorization: Bearer <jwt>} into a {@link CurrentUser} principal. An invalid token leaves the
 * request unauthenticated and records why, so the entry point can answer {@code token_expired} or
 * {@code invalid_token}. Registered only inside the security chain, not as a servlet filter.
 */
class JwtAuthenticationFilter extends OncePerRequestFilter {

	static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".error";

	private static final String BEARER = "Bearer ";

	private final JwtService jwt;

	JwtAuthenticationFilter(JwtService jwt) {
		this.jwt = jwt;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
			try {
				CurrentUser user = jwt.parse(header.substring(BEARER.length()).trim());
				SecurityContext context = SecurityContextHolder.createEmptyContext();
				context.setAuthentication(new PreAuthenticatedAuthenticationToken(user, null, List.of()));
				SecurityContextHolder.setContext(context);
			}
			catch (UnauthorizedException e) {
				request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.code());
			}
		}
		chain.doFilter(request, response);
	}

}
