package com.zhiyu.ufp.common.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class BizErrorCodeTest {

    @ParameterizedTest
    @EnumSource(BizErrorCode.class)
    void shouldHaveNonEmptyI18nKey(BizErrorCode code) {
        assertThat(code.getI18nKey()).isNotBlank();
        assertThat(code.getI18nKey()).startsWith("error.");
    }

    @ParameterizedTest
    @EnumSource(BizErrorCode.class)
    void shouldHaveNonEmptyMessage(BizErrorCode code) {
        assertThat(code.getMessage()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(BizErrorCode.class)
    void shouldHavePositiveCode(BizErrorCode code) {
        assertThat(code.getCode()).isPositive();
    }

    @Test
    void shouldImplementErrorCodeInterface() {
        assertThat(BizErrorCode.VALIDATION_FAILED).isInstanceOf(ErrorCode.class);
    }

    @Test
    void shouldReturnCorrectCode() {
        assertThat(BizErrorCode.TOKEN_EXPIRED.getCode()).isEqualTo(40101);
        assertThat(BizErrorCode.ACCESS_DENIED.getCode()).isEqualTo(40301);
        assertThat(BizErrorCode.RESOURCE_NOT_FOUND.getCode()).isEqualTo(40401);
        assertThat(BizErrorCode.INTERNAL_ERROR.getCode()).isEqualTo(50001);
    }

    @Test
    void shouldReturnCorrectMessage() {
        assertThat(BizErrorCode.ACCOUNT_LOCKED.getMessage()).isEqualTo(
                "Account temporarily locked, please retry in 15 minutes");
        assertThat(BizErrorCode.INVALID_TOKEN.getMessage()).isEqualTo("Invalid access token");
    }

    @Test
    void shouldContainAllCategories() {
        // 通用错误类型
        assertThat(BizErrorCode.valueOf("VALIDATION_FAILED")).isNotNull();
        // 认证错误类型
        assertThat(BizErrorCode.valueOf("TOKEN_EXPIRED")).isNotNull();
        // 鉴权错误类型
        assertThat(BizErrorCode.valueOf("ACCESS_DENIED")).isNotNull();
        // 用户相关错误类型
        assertThat(BizErrorCode.valueOf("USERNAME_TAKEN")).isNotNull();
        // 订阅相关错误类型
        assertThat(BizErrorCode.valueOf("ORDER_NOT_FOUND")).isNotNull();
        // 限流错误类型
        assertThat(BizErrorCode.valueOf("TOO_MANY_REQUESTS")).isNotNull();
        // 服务端错误类型
        assertThat(BizErrorCode.valueOf("INTERNAL_ERROR")).isNotNull();
    }
}
