package dev.monthendbuddy.common.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * A {@link MoneyDto} whose amount is a plain decimal greater than zero with at most 2 decimal places and at most 15
 * integer digits (the {@code NUMERIC(19,4)} column), and whose currency is a three-letter code (LLD §11.3). {@code null} is valid;
 * combine with {@code @NotNull} when the value is required.
 */
@Documented
@Constraint(validatedBy = PositiveMoneyValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
public @interface PositiveMoney {

	String message() default "must be a positive amount with at most 2 decimal places";

	/** Accept zero as well, e.g. an amount already saved. */
	boolean allowZero() default false;

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
