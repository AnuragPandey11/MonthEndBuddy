package dev.monthendbuddy.common.error;

import java.time.Duration;

import org.springframework.http.HttpStatus;

/** 429; the handler also sets {@code Retry-After} (architecture §11.2). */
public class RateLimitedException extends MonthEndBuddyException {

	private final Duration retryAfter;

	public RateLimitedException(Duration retryAfter) {
		super("Too many requests");
		this.retryAfter = retryAfter;
	}

	public Duration retryAfter() {
		return retryAfter;
	}

	@Override
	public String code() {
		return "rate_limited";
	}

	@Override
	public HttpStatus status() {
		return HttpStatus.TOO_MANY_REQUESTS;
	}

}
