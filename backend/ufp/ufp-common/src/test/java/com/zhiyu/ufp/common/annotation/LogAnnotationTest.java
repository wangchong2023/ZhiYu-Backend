package com.zhiyu.ufp.common.annotation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class LogAnnotationTest {

    @Test
    void shouldHaveCorrectTarget() {
        // @Log is designed for methods only
        Log log = getLogAnnotation();
        assertThat(log).isNotNull();
    }

    @Test
    void shouldDefaultLogParamsToTrue() {
        Log log = getLogAnnotation();
        assertThat(log.logParams()).isTrue();
    }

    @Test
    void shouldDefaultLogResultToFalse() {
        Log log = getLogAnnotation();
        assertThat(log.logResult()).isFalse();
    }

    @Test
    void shouldAllowCustomOperation() {
        Log log = getLogAnnotation();
        assertThat(log.operation()).isEqualTo("UPDATE");
    }

    @Test
    void shouldAllowCustomValue() {
        Log log = getLogAnnotation();
        assertThat(log.value()).isEqualTo("Update user profile");
    }

    private Log getLogAnnotation() {
        try {
            Method method = AnnotatedClass.class.getMethod("annotatedMethod");
            return method.getAnnotation(Log.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    static class AnnotatedClass {
        @Log(value = "Update user profile", operation = "UPDATE")
        public void annotatedMethod() {
        }
    }
}
