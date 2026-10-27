package dev.monthendbuddy.common.error;

import org.springframework.http.HttpStatus;

/** 403: the request is authenticated (or public) but refused, e.g. a refresh call from a foreign origin. */
public class ForbiddenException extends MonthEndBuddyException {

	private final String code;

	public ForbiddenException(String code, String message) {
		super(message);
		this.code = code;
	}

	@Override
	public String code() {
		return code;
	}

	@Override
	public HttpStatus status() {
		return HttpStatus.FORBIDDEN;
	}

}
