/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: LoginAttemptService.java
 * 创建时间: 2026-05-27
 * 描述: 登录尝试次数与账户临时锁定状态服务类。
 *      基于统一缓存抽象门面 ICacheOperate 实现，解除对具体 Redis 模板的强耦合依赖，符合依赖倒置原则。
 */
package com.zhiyu.auth.service;

import com.zhiyu.ufp.common.cache.CacheKeys;
import com.zhiyu.ufp.common.cache.ICacheOperate;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 类名: LoginAttemptService
 * 描述: 负责跟踪用户登录失败的尝试次数。如果在特定时间窗口内连续失败次数达到限制，则临时锁定账户。
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int CAPTCHA_THRESHOLD = 3;
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final ICacheOperate cacheOperate;

    /**
     * 描述: 检查当前用户是否已被锁定。若已被锁定，则直接抛出账户锁定的业务异常。
     * @param username 用户名
     */
    public void checkLocked(final String username) {
        String lockKey = CacheKeys.key(CacheKeys.LOGIN_LOCK, username);
        if (cacheOperate.get(lockKey) != null) {
            throw new BizException(BizErrorCode.ACCOUNT_LOCKED);
        }
    }

    /**
     * 描述: 检查当前用户连续失败次数是否达到需要输入验证码的安全阈值。
     * @param username 用户名
     */
    public void checkCaptchaRequired(final String username) {
        String key = CacheKeys.key(CacheKeys.LOGIN_ATTEMPT, username);
        Object val = cacheOperate.get(key);
        int attempts = val != null ? Integer.parseInt(val.toString()) : 0;
        if (attempts >= CAPTCHA_THRESHOLD) {
            throw new BizException(BizErrorCode.CAPTCHA_FAILED);
        }
    }

    /**
     * 描述: 记录一次登录失败尝试。累加失败次数并在达到限制后对账户实施临时锁定。
     * @param username 用户名
     */
    public void recordFailure(final String username) {
        String key = CacheKeys.key(CacheKeys.LOGIN_ATTEMPT, username);
        Object val = cacheOperate.get(key);
        int count = (val != null) ? Integer.parseInt(val.toString()) : 0;
        count++;
        
        // 重新存入缓存，并重置/应用其存活窗口
        cacheOperate.set(key, String.valueOf(count), WINDOW.toSeconds(), TimeUnit.SECONDS);

        if (count >= MAX_ATTEMPTS) {
            cacheOperate.set(
                    CacheKeys.key(CacheKeys.LOGIN_LOCK, username), "1", LOCK_DURATION.toSeconds(), TimeUnit.SECONDS);
        }
    }

    /**
     * 描述: 登录成功后，清除该用户下的所有失败尝试记录及锁定状态。
     * @param username 用户名
     */
    public void clearAttempts(final String username) {
        cacheOperate.delete(CacheKeys.key(CacheKeys.LOGIN_ATTEMPT, username));
        cacheOperate.delete(CacheKeys.key(CacheKeys.LOGIN_LOCK, username));
    }
}
