package dev.monthendbuddy.common.error;

/** One entry of a problem's {@code errors[]}: the offending field and a human-readable message. */
public record FieldViolation(String field, String message) {

}
