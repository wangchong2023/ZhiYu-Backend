package com.zhiyu.auth.service;

import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String ATTEMPT_PREFIX = "login:attempt:";
    private static final String LOCK_PREFIX = "login:lock:";
    private static final int MAX_ATTEMPTS = 5;
    private static final int CAPTCHA_THRESHOLD = 3;
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final int ERR_ACCOUNT_LOCKED = 40106;
    private static final int ERR_CAPTCHA_REQUIRED = 40111;
    private static final long SECONDS_PER_MINUTE = 60L;

    private final StringRedisTemplate redisTemplate;

    public void checkLocked(final String username) {
        String lockKey = LOCK_PREFIX + username;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            Long remaining = redisTemplate.getExpire(lockKey);
            throw new BizException(ERR_ACCOUNT_LOCKED,
                    "账号已被临时锁定，请 "
                            + (remaining != null ? remaining / SECONDS_PER_MINUTE + " 分钟后重试" : "稍后重试"));
        }
    }

    public void checkCaptchaRequired(final String username) {
        String key = ATTEMPT_PREFIX + username;
        String val = redisTemplate.opsForValue().get(key);
        int attempts = val != null ? Integer.parseInt(val) : 0;
        if (attempts >= CAPTCHA_THRESHOLD) {
            throw new BizException(BizErrorCode.CAPTCHA_FAILED);
        }
    }

    public void recordFailure(final String username) {
        String key = ATTEMPT_PREFIX + username;
        Long result = redisTemplate.opsForValue().increment(key);
        long count = (result != null) ? result : 0L;
        if (count == 1L) {
            redisTemplate.expire(key, WINDOW);
        }
        if (count >= MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set(LOCK_PREFIX + username, "1", LOCK_DURATION);
        }
    }

    public void clearAttempts(final String username) {
        redisTemplate.delete(ATTEMPT_PREFIX + username);
        redisTemplate.delete(LOCK_PREFIX + username);
    }
}
