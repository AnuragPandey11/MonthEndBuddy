package dev.monthendbuddy.common.error;

import org.springframework.http.HttpStatus;

/**
 * A missing resource, and equally a resource owned by another user: both look the same to the caller, so IDs
 * cannot be probed (architecture §5.3).
 */
public class NotFoundException extends MonthEndBuddyException {

	public NotFoundException(String resource) {
		super(resource + " not found");
	}

	@Override
	public String code() {
		return "not_found";
	}

	@Override
	public HttpStatus status() {
		return HttpStatus.NOT_FOUND;
	}

}
