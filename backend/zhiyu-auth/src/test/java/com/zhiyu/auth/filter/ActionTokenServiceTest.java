package com.zhiyu.auth.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActionTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private ActionTokenService actionTokenService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── isUsed ────────────────────────────────────────────────

    @Test
    void shouldReturnTrueWhenKeyExists() {
        when(redisTemplate.hasKey("action-token:used:jti-001")).thenReturn(true);

        boolean used = actionTokenService.isUsed("jti-001");

        assertThat(used).isTrue();
    }

    @Test
    void shouldReturnFalseWhenKeyDoesNotExist() {
        when(redisTemplate.hasKey("action-token:used:jti-002")).thenReturn(false);

        boolean used = actionTokenService.isUsed("jti-002");

        assertThat(used).isFalse();
    }

    @Test
    void shouldReturnFalseWhenHasKeyReturnsNull() {
        when(redisTemplate.hasKey("action-token:used:jti-003")).thenReturn(null);

        boolean used = actionTokenService.isUsed("jti-003");

        assertThat(used).isFalse();
    }

    // ── markUsed ──────────────────────────────────────────────

    @Test
    void shouldMarkTokenAsUsed() {
        long futureExpiry = System.currentTimeMillis() / 1000 + 300;

        actionTokenService.markUsed("jti-new", futureExpiry);

        verify(valueOps).set("action-token:used:jti-new", "1", Duration.ofSeconds(300));
    }

    @Test
    void shouldUseMinimumTtlOfOneSecond() {
        // Expiry is in the past → TTL should be clamped to 1
        long pastExpiry = System.currentTimeMillis() / 1000 - 100;

        actionTokenService.markUsed("jti-past", pastExpiry);

        verify(valueOps).set("action-token:used:jti-past", "1", Duration.ofSeconds(1));
    }

    @Test
    void shouldCalculateExactTtlForFutureExpiry() {
        long now = System.currentTimeMillis() / 1000;
        long expiresAt = now + 60; // 60 seconds from now

        actionTokenService.markUsed("jti-future", expiresAt);

        verify(valueOps).set("action-token:used:jti-future", "1", Duration.ofSeconds(60));
    }

    @Test
    void shouldSetValueAsStringOne() {
        long futureExpiry = System.currentTimeMillis() / 1000 + 900;

        actionTokenService.markUsed("jti-tag", futureExpiry);

        verify(valueOps).set("action-token:used:jti-tag", "1", Duration.ofSeconds(900));
    }

    // ── Key prefix format ─────────────────────────────────────

    @Test
    void shouldPrefixKeyCorrectly() {
        when(redisTemplate.hasKey("action-token:used:abc123")).thenReturn(true);

        actionTokenService.isUsed("abc123");

        verify(redisTemplate).hasKey("action-token:used:abc123");
    }
}
