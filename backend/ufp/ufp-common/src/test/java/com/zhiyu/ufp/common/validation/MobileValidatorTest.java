package com.zhiyu.ufp.common.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MobileValidatorTest {

    private final MobileValidator validator = new MobileValidator();
    private final ConstraintValidatorContext context = mock(ConstraintValidatorContext.class);

    @ParameterizedTest
    @NullAndEmptySource
    void shouldAcceptNullOrEmpty(String value) {
        assertThat(validator.isValid(value, context)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"13800138000", "15912345678", "18888888888"})
    void shouldAcceptValidChineseMobileNumbers(String value) {
        assertThat(validator.isValid(value, context)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "abcdefghijk", "1380013800", "138001380001"})
    void shouldRejectInvalidPhoneNumbers(String value) {
        assertThat(validator.isValid(value, context)).isFalse();
    }

    @Test
    void shouldRejectNonNumericString() {
        assertThat(validator.isValid("not-a-phone", context)).isFalse();
    }
}
