package dev.monthendbuddy.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base of every application error. {@code GlobalExceptionHandler} maps it to an RFC 9457 problem with a stable
 * {@link #code()} (LLD §12.2). Messages may reach clients, so they must not contain financial data.
 */
public abstract class MonthEndBuddyException extends RuntimeException {

	protected MonthEndBuddyException(String message) {
		super(message);
	}

	/** Stable, lower-snake-case error code, e.g. {@code not_found}. */
	public abstract String code();

	public abstract HttpStatus status();

}
