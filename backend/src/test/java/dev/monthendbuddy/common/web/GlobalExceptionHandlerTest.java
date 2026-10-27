package dev.monthendbuddy.common.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.monthendbuddy.common.error.NotFoundException;
import dev.monthendbuddy.common.error.RateLimitedException;
import dev.monthendbuddy.common.error.UnsupportedCurrencyException;
import dev.monthendbuddy.common.CurrencyCode;

class GlobalExceptionHandlerTest {

	/** Nested in a test class, so the application's component scan ignores it (Spring Boot TypeExcludeFilter). */
	@RestController
	static class ProbeController {

		record Body(@NotBlank String description, @NotNull @PositiveMoney MoneyDto amount) {
		}

		@GetMapping("/probe/missing")
		void missing() {
			throw new NotFoundException("Transaction");
		}

		@GetMapping("/probe/limited")
		void limited() {
			throw new RateLimitedException(Duration.ofSeconds(30));
		}

		@GetMapping("/probe/currency")
		void currency() {
			throw new UnsupportedCurrencyException("amount.currency", CurrencyCode.INR);
		}

		@GetMapping("/probe/boom")
		void boom() {
			throw new IllegalStateException("secret internal detail");
		}

		@PostMapping("/probe/body")
		void body(@Valid @RequestBody Body body) {
		}

	}

	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
		.setControllerAdvice(new GlobalExceptionHandler())
		.addFilters(new CorrelationIdFilter())
		.build();

	@Test
	void notFoundIsAProblemWithCodeAndTraceId() throws Exception {
		mvc.perform(get("/probe/missing"))
			.andExpect(status().isNotFound())
			.andExpect(header().string("Content-Type", "application/problem+json"))
			.andExpect(jsonPath("$.type").value("https://monthendbuddy.dev/problems/not_found"))
			.andExpect(jsonPath("$.code").value("not_found"))
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{32}")));
	}

	@Test
	void beanValidationFailuresAre422WithFieldErrors() throws Exception {
		mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON)
			.content("{\"description\":\"\",\"amount\":{\"amount\":\"-5\",\"currency\":\"INR\"}}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.errors.length()").value(2))
			.andExpect(jsonPath("$.errors[?(@.field == 'description')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field == 'amount')].message")
				.value("must be a positive amount with at most 2 decimal places"));
	}

	@Test
	void unsupportedCurrencyKeepsItsOwnCode() throws Exception {
		mvc.perform(get("/probe/currency"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("unsupported_currency"))
			.andExpect(jsonPath("$.errors[0].field").value("amount.currency"));
	}

	@Test
	void malformedJsonIs400() throws Exception {
		mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("malformed_request"));
	}

	@Test
	void rateLimitedSetsRetryAfter() throws Exception {
		mvc.perform(get("/probe/limited"))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", "30"))
			.andExpect(jsonPath("$.code").value("rate_limited"));
	}

	@Test
	void unexpectedErrorsDoNotLeakTheirMessage() throws Exception {
		mvc.perform(get("/probe/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("internal_error"))
			.andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
	}

	@Test
	void problemsBuiltBySpringMvcAlsoCarryCodeAndTraceId() throws Exception {
		mvc.perform(post("/probe/missing"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.code").value("method_not_allowed"))
			.andExpect(jsonPath("$.type").value("https://monthendbuddy.dev/problems/method_not_allowed"))
			.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{32}")));
	}

	@Test
	void validInboundTraceIdIsReusedAndInvalidOneReplaced() throws Exception {
		mvc.perform(get("/probe/missing").header("X-Trace-Id", "client-trace-1234"))
			.andExpect(header().string("X-Trace-Id", "client-trace-1234"))
			.andExpect(jsonPath("$.traceId").value("client-trace-1234"));
		mvc.perform(get("/probe/missing").header("X-Trace-Id", "bad\nvalue"))
			.andExpect(header().string("X-Trace-Id", matchesPattern("[0-9a-f]{32}")));
	}

}
