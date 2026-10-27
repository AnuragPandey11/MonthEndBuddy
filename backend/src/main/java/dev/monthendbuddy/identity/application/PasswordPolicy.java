package dev.monthendbuddy.identity.application;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.monthendbuddy.common.error.ValidationException;

/**
 * Password rules (architecture §5.1): 10 to 128 characters, not on the common-password list, and not the email
 * address. The upper bound keeps Argon2 hashing cheap enough to resist abuse.
 */
@Component
public class PasswordPolicy {

	public static final int MIN_LENGTH = 10;

	public static final int MAX_LENGTH = 128;

	private final Set<String> common;

	public PasswordPolicy() {
		try (InputStream in = PasswordPolicy.class.getResourceAsStream("/identity/common-passwords.txt")) {
			if (in == null) {
				throw new IllegalStateException("identity/common-passwords.txt is missing");
			}
			common = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.map(line -> line.toLowerCase(Locale.ROOT))
				.collect(Collectors.toUnmodifiableSet());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void check(String password, String normalizedEmail) {
		if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
			throw ValidationException.of("password",
					"must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
		}
		String lower = password.toLowerCase(Locale.ROOT);
		if (common.contains(lower)) {
			throw ValidationException.of("password", "is too common");
		}
		if (lower.equals(normalizedEmail)) {
			throw ValidationException.of("password", "must not be your email address");
		}
	}

}
