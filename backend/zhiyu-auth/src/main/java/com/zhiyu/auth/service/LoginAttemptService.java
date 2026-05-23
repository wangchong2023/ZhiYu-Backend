package com.zhiyu.auth.service;

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

    private final StringRedisTemplate redisTemplate;

    public void checkLocked(String username) {
        String lockKey = LOCK_PREFIX + username;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            Long remaining = redisTemplate.getExpire(lockKey);
            throw new BizException(40106,
                    "账号已被临时锁定，请 " + (remaining != null ? remaining / 60 + " 分钟后重试" : "稍后重试"));
        }
    }

    public void checkCaptchaRequired(String username) {
        String key = ATTEMPT_PREFIX + username;
        String val = redisTemplate.opsForValue().get(key);
        int attempts = val != null ? Integer.parseInt(val) : 0;
        if (attempts >= CAPTCHA_THRESHOLD) {
            throw new BizException(40111, "需要验证码");
        }
    }

    public void recordFailure(String username) {
        String key = ATTEMPT_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == 1) {
            redisTemplate.expire(key, WINDOW);
        }
        if (count != null && count >= MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set(LOCK_PREFIX + username, "1", LOCK_DURATION);
        }
    }

    public void clearAttempts(String username) {
        redisTemplate.delete(ATTEMPT_PREFIX + username);
        redisTemplate.delete(LOCK_PREFIX + username);
    }
}
