package dev.monthendbuddy.common.error;

import org.springframework.http.HttpStatus;

/** 401 with {@code invalid_credentials}, {@code token_expired} or {@code token_reused}. */
public class UnauthorizedException extends MonthEndBuddyException {

	private final String code;

	public UnauthorizedException(String code) {
		super("Authentication failed");
		this.code = code;
	}

	@Override
	public String code() {
		return code;
	}

	@Override
	public HttpStatus status() {
		return HttpStatus.UNAUTHORIZED;
	}

}
