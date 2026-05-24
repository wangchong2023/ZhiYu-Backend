package com.zhiyu.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaptchaServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @InjectMocks
    private CaptchaService captchaService;

    @Test
    void shouldGenerateAndVerifyCaptcha() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        var resp = captchaService.generate("zhiyu_login");
        assertThat(resp.getCaptchaToken()).isNotBlank();
        assertThat(resp.getCaptchaImage()).startsWith("data:image/png;base64,");
    }

    @Test
    void shouldVerifyCorrectCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("A3x9");

        captchaService.verify("test-token", "A3x9");
    }

    @Test
    void shouldThrowOnWrongCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("A3x9");

        assertThatThrownBy(() -> captchaService.verify("test-token", "wrong"))
                .hasMessageContaining("Verification code is incorrect or expired");
    }

    @Test
    void shouldThrowOnExpiredCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> captchaService.verify("test-token", "any"))
                .hasMessageContaining("Verification code is incorrect or expired");
    }
}
