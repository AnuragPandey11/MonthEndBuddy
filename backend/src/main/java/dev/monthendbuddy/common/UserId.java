package dev.monthendbuddy.common;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of the authenticated user. It only ever comes from the security context ({@code CurrentUser}); no
 * request, command or tool input carries one (architecture D2).
 */
public record UserId(UUID value) {

	public UserId {
		Objects.requireNonNull(value, "value");
	}

	public static UserId of(String raw) {
		return new UserId(UUID.fromString(raw));
	}

	@Override
	public String toString() {
		return value.toString();
	}

}
