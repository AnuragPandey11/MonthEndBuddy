package dev.monthendbuddy.common.error;

import org.springframework.http.HttpStatus;

/** An entity refused a state change it does not allow, e.g. discarding a committed import (LLD §12.1). */
public class IllegalStateTransitionException extends MonthEndBuddyException {

	public IllegalStateTransitionException(String message) {
		super(message);
	}

	@Override
	public String code() {
		return "invalid_state";
	}

	@Override
	public HttpStatus status() {
		return HttpStatus.CONFLICT;
	}

}
