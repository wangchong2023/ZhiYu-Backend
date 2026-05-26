package com.zhiyu.ufp.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Phone number validation annotation.
 * Delegates to Hutool {@link cn.hutool.core.lang.Validator#isMobile(CharSequence)}.
 * Accepts null or empty values (use {@code @NotBlank} separately if required).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MobileValidator.class)
public @interface Mobile {

    String message() default "{validation.mobile.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
