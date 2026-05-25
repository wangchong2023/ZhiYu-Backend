package com.zhiyu.auth.service;

import com.zhiyu.ufp.common.cache.CacheKeys;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int CAPTCHA_THRESHOLD = 3;
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    public void checkLocked(final String username) {
        String lockKey = CacheKeys.key(CacheKeys.LOGIN_LOCK, username);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            throw new BizException(BizErrorCode.ACCOUNT_LOCKED);
        }
    }

    public void checkCaptchaRequired(final String username) {
        String key = CacheKeys.key(CacheKeys.LOGIN_ATTEMPT, username);
        String val = redisTemplate.opsForValue().get(key);
        int attempts = val != null ? Integer.parseInt(val) : 0;
        if (attempts >= CAPTCHA_THRESHOLD) {
            throw new BizException(BizErrorCode.CAPTCHA_FAILED);
        }
    }

    public void recordFailure(final String username) {
        String key = CacheKeys.key(CacheKeys.LOGIN_ATTEMPT, username);
        Long result = redisTemplate.opsForValue().increment(key);
        long count = (result != null) ? result : 0L;
        if (count == 1L) {
            redisTemplate.expire(key, WINDOW);
        }
        if (count >= MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set(
                    CacheKeys.key(CacheKeys.LOGIN_LOCK, username), "1", LOCK_DURATION);
        }
    }

    public void clearAttempts(final String username) {
        redisTemplate.delete(CacheKeys.key(CacheKeys.LOGIN_ATTEMPT, username));
        redisTemplate.delete(CacheKeys.key(CacheKeys.LOGIN_LOCK, username));
    }
}
