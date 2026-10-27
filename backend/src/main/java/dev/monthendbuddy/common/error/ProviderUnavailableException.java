package dev.monthendbuddy.common.error;

import org.springframework.http.HttpStatus;

/**
 * The AI provider failed or is not configured. The agent turns this into {@code AgentResponse.status =
 * PROVIDER_UNAVAILABLE} with HTTP 200 (LLD §12.2); the 503 mapping applies only if it ever escapes to the web layer.
 */
public class ProviderUnavailableException extends MonthEndBuddyException {

	public ProviderUnavailableException(String message) {
		super(message);
	}

	@Override
	public String code() {
		return "provider_unavailable";
	}

	@Override
	public HttpStatus status() {
		return HttpStatus.SERVICE_UNAVAILABLE;
	}

}
