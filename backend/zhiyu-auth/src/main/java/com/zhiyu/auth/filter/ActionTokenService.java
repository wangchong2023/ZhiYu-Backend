package com.zhiyu.auth.filter;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class ActionTokenService {

    private static final String PREFIX = "action-token:used:";

    private final StringRedisTemplate redisTemplate;

    public boolean isUsed(final String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
    }

    public void markUsed(final String jti, final long expiresAtEpochSeconds) {
        long now = System.currentTimeMillis() / 1000;
        long ttl = Math.max(expiresAtEpochSeconds - now, 1);
        redisTemplate.opsForValue().set(PREFIX + jti, "1", Duration.ofSeconds(ttl));
    }
}
