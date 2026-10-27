package dev.monthendbuddy.identity.infrastructure;

import java.util.Map;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

import dev.monthendbuddy.common.web.ProblemResponseWriter;
import dev.monthendbuddy.identity.application.AuthProperties;
import dev.monthendbuddy.identity.application.JwtService;

/**
 * Stateless bearer-token security (architecture §5). CSRF protection is off because the API authenticates with a
 * header browsers never attach on their own; the two cookie-authenticated endpoints (refresh, logout) check
 * {@code Origin} and {@code X-MEB-Client} in {@code AuthController} instead.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, ProblemResponseWriter problems)
			throws Exception {
		http.csrf(csrf -> csrf.disable())
			.cors(cors -> cors.disable())
			.httpBasic(basic -> basic.disable())
			.formLogin(form -> form.disable())
			.logout(logout -> logout.disable())
			.requestCache(cache -> cache.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				// The re-dispatch that completes a streamed response (the copilot's SSE). The request it belongs to was
				// authorized on its first dispatch; the bearer filter does not run again, so this one carries no token.
				.dispatcherTypeMatchers(DispatcherType.ASYNC)
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
						"/api/v1/auth/logout", "/api/v1/auth/demo")
				.permitAll()
				// Actuator lives on the management port, which nginx does not proxy (architecture §3).
				.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
				.permitAll()
				// Only enabled in the local profile.
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
				.permitAll()
				.requestMatchers("/error")
				.permitAll()
				.anyRequest()
				.authenticated())
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint((request, response, e) -> {
					Object code = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
					response.setHeader("WWW-Authenticate", "Bearer");
					problems.write(request, response, HttpStatus.UNAUTHORIZED,
							code != null ? code.toString() : "unauthorized", "Authentication required");
				})
				.accessDeniedHandler((request, response, e) -> problems.write(request, response, HttpStatus.FORBIDDEN,
						"forbidden", "Access denied")))
			.addFilterBefore(new JwtAuthenticationFilter(jwt), AbstractPreAuthenticatedProcessingFilter.class);
		return http.build();
	}

	/**
	 * Argon2id by default, behind a delegating encoder so the algorithm can change without a migration; bcrypt is
	 * accepted only for verifying legacy hashes (architecture §5.1).
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		Map<String, PasswordEncoder> encoders = Map.of("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
				"bcrypt", new BCryptPasswordEncoder());
		return new DelegatingPasswordEncoder("argon2", encoders);
	}

}
