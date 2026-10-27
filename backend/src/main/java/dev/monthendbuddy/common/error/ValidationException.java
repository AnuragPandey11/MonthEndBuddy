package dev.monthendbuddy.common.error;

import java.util.List;

import org.springframework.http.HttpStatus;

/** Input that is well-formed but not acceptable: 422 with per-field {@code errors[]}. */
public class ValidationException extends MonthEndBuddyException {

	private final List<FieldViolation> errors;

	public ValidationException(List<FieldViolation> errors) {
		super("Validation failed");
		this.errors = List.copyOf(errors);
	}

	public static ValidationException of(String field, String message) {
		return new ValidationException(List.of(new FieldViolation(field, message)));
	}

	public List<FieldViolation> errors() {
		return errors;
	}

	@Override
	public String code() {
		return "validation_failed";
	}

	@Override
	public HttpStatus status() {
		return HttpStatus.UNPROCESSABLE_CONTENT;
	}

}
