package com.zhiyu.ufp.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BizExceptionTest {

    @Test
    void shouldCreateWithCodeAndMessage() {
        BizException ex = new BizException(40001, "Validation failed");

        assertThat(ex.getCode()).isEqualTo(40001);
        assertThat(ex.getMessage()).isEqualTo("Validation failed");
        assertThat(ex.getErrorCode()).isNull();
    }

    @Test
    void shouldCreateWithErrorCode() {
        BizException ex = new BizException(BizErrorCode.RESOURCE_NOT_FOUND);

        assertThat(ex.getCode()).isEqualTo(40401);
        assertThat(ex.getMessage()).isEqualTo("Resource not found");
        assertThat(ex.getErrorCode()).isEqualTo(BizErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void shouldBeRuntimeException() {
        BizException ex = new BizException(BizErrorCode.INTERNAL_ERROR);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
