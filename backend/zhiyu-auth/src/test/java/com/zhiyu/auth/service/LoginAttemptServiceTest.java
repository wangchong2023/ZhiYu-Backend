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
        when(redisTemplate.getExpire(anyString())).thenReturn(900L);
        assertThatThrownBy(() -> service.checkLocked("user1"))
                .hasMessageContaining("已被临时锁定");
    }

    @Test
    void shouldRequireCaptchaAfter3Failures() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("3");
        assertThatThrownBy(() -> service.checkCaptchaRequired("user1"))
                .hasMessageContaining("需要验证码");
    }

    @Test
    void shouldNotRequireCaptchaUnder3Failures() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("2");
        assertThatCode(() -> service.checkCaptchaRequired("user1")).doesNotThrowAnyException();
    }
}
