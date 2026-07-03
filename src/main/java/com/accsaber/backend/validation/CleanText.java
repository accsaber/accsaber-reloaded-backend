package com.accsaber.backend.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = CleanTextValidator.class)
@Target(FIELD)
@Retention(RUNTIME)
public @interface CleanText {

    String message() default "must not contain blocked or abusive language";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
