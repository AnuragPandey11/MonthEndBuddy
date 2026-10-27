package dev.monthendbuddy.identity.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import dev.monthendbuddy.common.error.ValidationException;

class PasswordPolicyTest {

	private final PasswordPolicy policy = new PasswordPolicy();

	@Test
	void acceptsALongUncommonPassword() {
		assertThatCode(() -> policy.check("correct-horse-battery", "a@example.test")).doesNotThrowAnyException();
	}

	@Test
	void enforcesLengthBounds() {
		assertThatThrownBy(() -> policy.check("short-pw9", "a@example.test")).isInstanceOf(ValidationException.class);
		assertThatCode(() -> policy.check("ten-chars!", "a@example.test")).doesNotThrowAnyException();
		assertThatThrownBy(() -> policy.check("x".repeat(129), "a@example.test"))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsCommonPasswordsCaseInsensitively() {
		assertThatThrownBy(() -> policy.check("Password1234", "a@example.test"))
			.isInstanceOf(ValidationException.class)
			.hasFieldOrPropertyWithValue("errors",
					java.util.List.of(new dev.monthendbuddy.common.error.FieldViolation("password", "is too common")));
	}

	@Test
	void rejectsTheEmailAddressAsPassword() {
		assertThatThrownBy(() -> policy.check("Someone@Example.test", "someone@example.test"))
			.isInstanceOf(ValidationException.class);
	}

}
