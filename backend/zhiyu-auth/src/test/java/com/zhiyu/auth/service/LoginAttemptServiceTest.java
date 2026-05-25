package com.zhiyu.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @InjectMocks
    private LoginAttemptService service;

    @Test
    void shouldAllowLoginWhenUnderLimit() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        assertThatCode(() -> service.checkLocked("user1")).doesNotThrowAnyException();
    }

    @Test
    void shouldLockAfterMaxAttempts() {
        when(redisTemplate.hasKey(contains("lock"))).thenReturn(true);
        assertThatThrownBy(() -> service.checkLocked("user1"))
                .hasMessageContaining("Account locked");
    }

    @Test
    void shouldRequireCaptchaAfter3Failures() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("3");
        assertThatThrownBy(() -> service.checkCaptchaRequired("user1"))
                .hasMessageContaining("CAPTCHA verification failed");
    }

    @Test
    void shouldNotRequireCaptchaUnder3Failures() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("2");
        assertThatCode(() -> service.checkCaptchaRequired("user1")).doesNotThrowAnyException();
    }

    @Test
    void shouldNotRequireCaptchaWhenNoFailures() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        assertThatCode(() -> service.checkCaptchaRequired("user1")).doesNotThrowAnyException();
    }

    // ── recordFailure ───────────────────────────────────────────

    @Test
    void shouldSetExpireOnFirstFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        service.recordFailure("user1");

        verify(redisTemplate).expire(contains("attempt"), eq(java.time.Duration.ofMinutes(5)));
    }

    @Test
    void shouldNotSetExpireOnSubsequentFailures() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(3L);

        service.recordFailure("user1");

        verify(redisTemplate, never()).expire(anyString(), any(java.time.Duration.class));
    }

    @Test
    void shouldLockAccountAfterMaxAttempts() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(5L);

        service.recordFailure("user1");

        verify(valueOps).set(contains("lock"), eq("1"),
                eq(java.time.Duration.ofMinutes(15)));
    }

    @Test
    void shouldNotLockAccountAt4Failures() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(4L);

        service.recordFailure("user1");

        verify(valueOps, never()).set(anyString(), anyString(), any(java.time.Duration.class));
    }

    @Test
    void shouldHandleNullIncrementResult() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(null);

        // Null result treated as 0, no NPE, no side effects
        service.recordFailure("user1");
    }

    @Test
    void shouldLockAccountAboveMaxAttempts() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(6L);

        service.recordFailure("user1");

        verify(valueOps).set(contains("lock"), eq("1"),
                eq(java.time.Duration.ofMinutes(15)));
    }

    // ── clearAttempts ───────────────────────────────────────────

    @Test
    void shouldClearBothAttemptAndLockKeys() {
        service.clearAttempts("user1");

        verify(redisTemplate).delete(contains("attempt"));
        verify(redisTemplate).delete(contains("lock"));
    }

    // ── checkLocked with null remaining ─────────────────────────

    @Test
    void shouldHandleNullRemainingOnLockedCheck() {
        when(redisTemplate.hasKey(contains("lock"))).thenReturn(true);

        assertThatThrownBy(() -> service.checkLocked("user1"))
                .hasMessageContaining("Account locked");
    }

    // ── checkCaptchaRequired with exactly threshold ──────────────

    @Test
    void shouldRequireCaptchaAtThreshold() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("3");
        assertThatThrownBy(() -> service.checkCaptchaRequired("user1"))
                .hasMessageContaining("CAPTCHA verification failed");
    }

    // ── checkCaptchaRequired above threshold ────────────────────

    @Test
    void shouldRequireCaptchaAboveThreshold() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("5");
        assertThatThrownBy(() -> service.checkCaptchaRequired("user1"))
                .hasMessageContaining("CAPTCHA verification failed");
    }
}
