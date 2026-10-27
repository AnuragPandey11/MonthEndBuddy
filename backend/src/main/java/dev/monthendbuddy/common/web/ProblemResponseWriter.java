package dev.monthendbuddy.common.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes a problem body from code that runs outside Spring MVC (security filters, entry points), in the same shape
 * as {@link GlobalExceptionHandler}.
 */
@Component
public class ProblemResponseWriter {

	private final JsonMapper jsonMapper;

	public ProblemResponseWriter(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
			String detail) throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", GlobalExceptionHandler.PROBLEM_BASE + code);
		body.put("title", status.getReasonPhrase());
		body.put("status", status.value());
		body.put("detail", detail);
		body.put("instance", request.getRequestURI());
		body.put("code", code);
		body.put("traceId", MDC.get(CorrelationIdFilter.MDC_KEY));
		response.setStatus(status.value());
		// Same header as Spring MVC problems; Jackson writes UTF-8 bytes, which JSON requires anyway.
		response.setContentType("application/problem+json");
		jsonMapper.writeValue(response.getOutputStream(), body);
	}

}
