/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: LoginAttemptServiceTest.java
 * 创建时间: 2026-05-27
 * 描述: LoginAttemptService 登录尝试次数与锁定状态控制层逻辑的单元测试类。
 */
package com.zhiyu.auth.service;

import com.zhiyu.ufp.common.cache.ICacheOperate;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 类名: LoginAttemptServiceTest
 * 描述: 测试连续登录失败锁定的次数边界。利用 Mock 模拟统一缓存门面 ICacheOperate 的行为。
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private ICacheOperate cacheOperate;

    @InjectMocks
    private LoginAttemptService service;

    @Test
    void shouldAllowLoginWhenUnderLimit() {
        when(cacheOperate.get(anyString())).thenReturn(null);
        assertThatCode(() -> service.checkLocked("user1")).doesNotThrowAnyException();
    }

    @Test
    void shouldLockAfterMaxAttempts() {
        when(cacheOperate.get(contains("lock"))).thenReturn("1");
        assertThatThrownBy(() -> service.checkLocked("user1"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.ACCOUNT_LOCKED.getCode());
    }

    @Test
    void shouldRequireCaptchaAfter3Failures() {
        when(cacheOperate.get(anyString())).thenReturn("3");
        assertThatThrownBy(() -> service.checkCaptchaRequired("user1"))
                .hasMessageContaining("CAPTCHA verification failed");
    }

    @Test
    void shouldNotRequireCaptchaUnder3Failures() {
        when(cacheOperate.get(anyString())).thenReturn("2");
        assertThatCode(() -> service.checkCaptchaRequired("user1")).doesNotThrowAnyException();
    }

    @Test
    void shouldNotRequireCaptchaWhenNoFailures() {
        when(cacheOperate.get(anyString())).thenReturn(null);
        assertThatCode(() -> service.checkCaptchaRequired("user1")).doesNotThrowAnyException();
    }

    // ── recordFailure ───────────────────────────────────────────

    @Test
    void shouldSetExpireOnFirstFailure() {
        when(cacheOperate.get(anyString())).thenReturn(null);

        service.recordFailure("user1");

        // 当次数为1时，应存入尝试记录，时效为 300 秒（5分钟）
        verify(cacheOperate).set(contains("attempt"), eq("1"), eq(300L), eq(TimeUnit.SECONDS));
    }

    @Test
    void shouldNotLockAccountAt4Failures() {
        when(cacheOperate.get(anyString())).thenReturn("3");

        service.recordFailure("user1");

        // 从3自增到4，应记录尝试但不触发锁定
        verify(cacheOperate).set(contains("attempt"), eq("4"), eq(300L), eq(TimeUnit.SECONDS));
        verify(cacheOperate, never()).set(contains("lock"), any(), anyLong(), any());
    }

    @Test
    void shouldLockAccountAfterMaxAttempts() {
        when(cacheOperate.get(anyString())).thenReturn("4");

        service.recordFailure("user1");

        // 从4自增到5，应触发锁定 900 秒（15分钟）
        verify(cacheOperate).set(contains("attempt"), eq("5"), eq(300L), eq(TimeUnit.SECONDS));
        verify(cacheOperate).set(contains("lock"), eq("1"), eq(900L), eq(TimeUnit.SECONDS));
    }

    // ── clearAttempts ───────────────────────────────────────────

    @Test
    void shouldClearBothAttemptAndLockKeys() {
        service.clearAttempts("user1");

        verify(cacheOperate).delete(contains("attempt"));
        verify(cacheOperate).delete(contains("lock"));
    }
}
