package dev.monthendbuddy.common.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * First filter in the chain (architecture §4.3): puts a {@code traceId} in the MDC for every log line of the request
 * and echoes it in the {@value #HEADER} response header and in every problem body, so a user-visible error links to
 * a log line. A well-formed inbound header is reused; anything else is replaced, so the value is safe to log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Trace-Id";

	public static final String MDC_KEY = "traceId";

	private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{8,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String inbound = request.getHeader(HEADER);
		String traceId = (inbound != null && VALID.matcher(inbound).matches()) ? inbound
				: UUID.randomUUID().toString().replace("-", "");
		MDC.put(MDC_KEY, traceId);
		response.setHeader(HEADER, traceId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}

}
