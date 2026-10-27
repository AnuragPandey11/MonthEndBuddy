package dev.monthendbuddy.common.web;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import dev.monthendbuddy.common.error.FieldViolation;
import dev.monthendbuddy.common.error.MonthEndBuddyException;
import dev.monthendbuddy.common.error.RateLimitedException;
import dev.monthendbuddy.common.error.ValidationException;

/**
 * Maps every error to an RFC 9457 problem (architecture §13.1): {@code type}, {@code title}, {@code status}, a stable
 * {@code code}, the request's {@code traceId}, and {@code errors[]} for validation failures. Bean Validation failures
 * are 422, not Spring's default 400. Unexpected exceptions never leak their message.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	public static final String PROBLEM_BASE = "https://monthendbuddy.dev/problems/";

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final Map<Integer, String> CODES_BY_STATUS = Map.of(400, "bad_request", 401, "unauthorized", 403,
			"forbidden", 404, "not_found", 405, "method_not_allowed", 406, "not_acceptable", 409, "conflict", 413,
			"payload_too_large", 415, "unsupported_media_type", 422, "validation_failed");

	@ExceptionHandler(MonthEndBuddyException.class)
	ResponseEntity<ProblemDetail> handleApplicationException(MonthEndBuddyException ex) {
		List<FieldViolation> errors = (ex instanceof ValidationException v) ? v.errors() : List.of();
		HttpHeaders headers = new HttpHeaders();
		if (ex instanceof RateLimitedException r) {
			headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, r.retryAfter().toSeconds())));
		}
		return ResponseEntity.status(ex.status())
			.headers(headers)
			.body(problem(ex.status(), ex.code(), ex.getMessage(), errors));
	}

	/** A concurrent update won the race between the version check and the write. */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(problem(HttpStatus.CONFLICT, "version_conflict", "The resource was changed by another request",
					List.of()));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
		List<FieldViolation> errors = ex.getConstraintViolations()
			.stream()
			.map(v -> new FieldViolation(lastNode(v.getPropertyPath().toString()), v.getMessage()))
			.toList();
		return validationFailed(errors);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		return ResponseEntity.status(status).body(problem(status, "internal_error", "An unexpected error occurred", List.of()));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldViolation> errors = new ArrayList<>();
		ex.getBindingResult()
			.getFieldErrors()
			.forEach(e -> errors.add(new FieldViolation(e.getField(), e.getDefaultMessage())));
		ex.getBindingResult()
			.getGlobalErrors()
			.forEach(e -> errors.add(new FieldViolation(e.getObjectName(), e.getDefaultMessage())));
		return ResponseEntity.unprocessableContent().body(validationProblem(errors));
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldViolation> errors = new ArrayList<>();
		ex.getParameterValidationResults().forEach(result -> {
			String parameter = result.getMethodParameter().getParameterName();
			for (MessageSourceResolvable error : result.getResolvableErrors()) {
				String field = (error instanceof FieldError f) ? f.getField() : parameter;
				errors.add(new FieldViolation(field, error.getDefaultMessage()));
			}
		});
		return ResponseEntity.unprocessableContent().body(validationProblem(errors));
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return ResponseEntity.badRequest()
			.body(problem(HttpStatus.BAD_REQUEST, "malformed_request", "The request body could not be read", List.of()));
	}

	/** Adds {@code code}, {@code type} and {@code traceId} to the problems Spring MVC builds itself (404, 405, ...). */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		// Spring creates the ProblemDetail inside super (body is often null here), so enrich the result.
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail detail
				&& (detail.getProperties() == null || !detail.getProperties().containsKey("code"))) {
			String code = CODES_BY_STATUS.getOrDefault(statusCode.value(), "error");
			detail.setType(URI.create(PROBLEM_BASE + code));
			detail.setProperty("code", code);
			detail.setProperty("traceId", MDC.get(CorrelationIdFilter.MDC_KEY));
		}
		return response;
	}

	private ResponseEntity<ProblemDetail> validationFailed(List<FieldViolation> errors) {
		return ResponseEntity.unprocessableContent().body(validationProblem(errors));
	}

	private static ProblemDetail validationProblem(List<FieldViolation> errors) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "validation_failed", "Validation failed", errors);
	}

	private static ProblemDetail problem(HttpStatusCode status, String code, String detail, List<FieldViolation> errors) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create(PROBLEM_BASE + code));
		problem.setProperty("code", code);
		problem.setProperty("traceId", MDC.get(CorrelationIdFilter.MDC_KEY));
		if (!errors.isEmpty()) {
			problem.setProperty("errors", errors);
		}
		return problem;
	}

	private static String lastNode(String propertyPath) {
		int dot = propertyPath.lastIndexOf('.');
		return dot < 0 ? propertyPath : propertyPath.substring(dot + 1);
	}

}
