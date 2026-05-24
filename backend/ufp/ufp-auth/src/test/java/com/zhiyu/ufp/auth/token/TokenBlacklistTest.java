package com.zhiyu.ufp.auth.token;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private TokenBlacklist tokenBlacklist;

    @Test
    void shouldAddTokenToBlacklist() {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.signature";
        long ttlSeconds = 3600L;

        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        tokenBlacklist.add(token, ttlSeconds);

        verify(valueOps).set("token:blacklist:" + token, "1", Duration.ofSeconds(ttlSeconds));
    }

    @Test
    void shouldReturnTrueWhenTokenIsBlacklisted() {
        String token = "blacklisted-token";
        when(redisTemplate.hasKey("token:blacklist:" + token)).thenReturn(true);

        boolean result = tokenBlacklist.isBlacklisted(token);

        assertThat(result).isTrue();
        verify(redisTemplate).hasKey("token:blacklist:" + token);
    }

    @Test
    void shouldReturnFalseWhenTokenIsNotBlacklisted() {
        String token = "clean-token";
        when(redisTemplate.hasKey("token:blacklist:" + token)).thenReturn(false);

        boolean result = tokenBlacklist.isBlacklisted(token);

        assertThat(result).isFalse();
        verify(redisTemplate).hasKey("token:blacklist:" + token);
    }

    @Test
    void shouldReturnFalseWhenKeyCheckReturnsNull() {
        String token = "null-check-token";
        when(redisTemplate.hasKey("token:blacklist:" + token)).thenReturn(null);

        boolean result = tokenBlacklist.isBlacklisted(token);

        assertThat(result).isFalse();
    }

    @Test
    void shouldUseCorrectPrefix() {
        String token = "test-token";
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        tokenBlacklist.add(token, 60L);

        verify(valueOps).set("token:blacklist:test-token", "1", Duration.ofSeconds(60L));
    }
}
