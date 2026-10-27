package dev.monthendbuddy.common.error;

import org.springframework.http.HttpStatus;

/** 409: a stale {@code version} ({@code version_conflict}) or a turn already running ({@code turn_in_progress}). */
public class ConflictException extends MonthEndBuddyException {

	private final String code;

	public ConflictException(String code, String message) {
		super(message);
		this.code = code;
	}

	public static ConflictException versionConflict() {
		return new ConflictException("version_conflict", "The resource was changed by another request");
	}

	@Override
	public String code() {
		return code;
	}

	@Override
	public HttpStatus status() {
		return HttpStatus.CONFLICT;
	}

}
