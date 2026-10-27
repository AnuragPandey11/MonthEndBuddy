package dev.monthendbuddy.identity;

import java.util.Objects;

import dev.monthendbuddy.common.UserId;

/**
 * The authenticated caller, resolved from the access token. Controllers declare it as a parameter; it is the only
 * source of a {@link UserId} in the application (architecture §5.3).
 */
public record CurrentUser(UserId id) {

	public CurrentUser {
		Objects.requireNonNull(id, "id");
	}

}
