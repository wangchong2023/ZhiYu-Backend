package com.zhiyu.ufp.auth.token;

import com.zhiyu.ufp.common.cache.CacheKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class TokenBlacklist {

    private final StringRedisTemplate redisTemplate;

    public void add(final String token, final long ttlSeconds) {
        redisTemplate.opsForValue().set(CacheKeys.TOKEN_BLACKLIST + token, "1", Duration.ofSeconds(ttlSeconds));
    }

    public boolean isBlacklisted(final String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(CacheKeys.TOKEN_BLACKLIST + token));
    }
}
