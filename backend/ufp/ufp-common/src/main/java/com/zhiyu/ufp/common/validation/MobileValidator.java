package com.zhiyu.ufp.common.validation;

import cn.hutool.core.lang.Validator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for {@link Mobile} annotation.
 * Uses Hutool {@link Validator#isMobile(CharSequence)} for phone number validation.
 */
public class MobileValidator implements ConstraintValidator<Mobile, String> {

    @Override
    public boolean isValid(final String value, final ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return Validator.isMobile(value);
    }
}
