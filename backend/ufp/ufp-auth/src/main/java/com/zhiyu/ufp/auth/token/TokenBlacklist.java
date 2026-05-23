package com.zhiyu.ufp.auth.token;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class TokenBlacklist {

    private static final String PREFIX = "token:blacklist:";
    private final StringRedisTemplate redisTemplate;

    public void add(final String token, final long ttlSeconds) {
        redisTemplate.opsForValue().set(PREFIX + token, "1", Duration.ofSeconds(ttlSeconds));
    }

    public boolean isBlacklisted(final String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + token));
    }
}
