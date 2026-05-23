package com.zhiyu.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.auth.converter.AuthConverter;
import com.zhiyu.auth.dto.LoginRequest;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.dto.RefreshRequest;
import com.zhiyu.auth.dto.RegisterRequest;
import com.zhiyu.auth.dto.RegisterResponse;
import com.zhiyu.auth.validator.AuthValidator;
import com.zhiyu.ufp.common.exception.BizException;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.auth.token.TokenBlacklist;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REGISTER_RATE_PREFIX = "register:rate:";
    private static final int MAX_REGISTER_PER_IP_PER_HOUR = 3;
    private static final long MS_PER_SECOND = 1000L;
    private static final int ERR_USERNAME_TAKEN = 40901;
    private static final int ERR_EMAIL_REGISTERED = 40902;
    private static final int ERR_CAPTCHA_REQUIRED = 40111;
    private static final int ERR_BAD_CREDENTIALS = 40105;
    private static final int ERR_ACCOUNT_DISABLED = 40107;
    private static final int ERR_ACCOUNT_DELETED = 40108;
    private static final int ERR_REFRESH_TOKEN_USED = 40103;

    private final AuthUserMapper authUserMapper;
    private final AuthUserLogMapper authUserLogMapper;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final CaptchaService captchaService;
    private final LoginAttemptService loginAttemptService;
    private final AuthValidator authValidator;
    private final StringRedisTemplate redisTemplate;

    @Transactional(rollbackFor = Exception.class)
    public RegisterResponse register(final RegisterRequest request) {
        authValidator.validateUsername(request.getUsername());
        authValidator.validatePassword(request.getPassword());
        authValidator.validateEmail(request.getEmail());
        captchaService.verify(request.getCaptchaToken(), request.getCaptchaCode());

        if (authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserUsername, request.getUsername())) != null) {
            throw new BizException(ERR_USERNAME_TAKEN, "用户名已被占用");
        }
        if (authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserMail, request.getEmail())) != null) {
            throw new BizException(ERR_EMAIL_REGISTERED, "邮箱已被注册");
        }

        AuthUser user = AuthConverter.INSTANCE.toEntity(request);
        user.setAuthUserPassword(passwordService.hash(request.getPassword()));
        authUserMapper.insert(user);

        return RegisterResponse.builder()
                .userId(user.getAuthUserId())
                .username(user.getAuthUserUsername())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(final LoginRequest request) {
        loginAttemptService.checkLocked(request.getUsername());

        boolean captchaRequired = false;
        try {
            loginAttemptService.checkCaptchaRequired(request.getUsername());
        } catch (BizException e) {
            captchaRequired = true;
        }

        if (captchaRequired) {
            if (request.getCaptchaToken() == null || request.getCaptchaCode() == null) {
                throw new BizException(ERR_CAPTCHA_REQUIRED, "需要验证码");
            }
            captchaService.verify(request.getCaptchaToken(), request.getCaptchaCode());
        }

        AuthUser user = authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserUsername, request.getUsername()));

        if (user == null || !passwordService.verify(request.getPassword(), user.getAuthUserPassword())) {
            loginAttemptService.recordFailure(request.getUsername());
            throw new BizException(ERR_BAD_CREDENTIALS, "用户名或密码错误");
        }

        if (user.getAuthUserEnable() == null || user.getAuthUserEnable() != 1) {
            throw new BizException(ERR_ACCOUNT_DISABLED, "账号已被禁用");
        }
        if (user.getAuthUserDeleted() != null && user.getAuthUserDeleted() == 1) {
            throw new BizException(ERR_ACCOUNT_DELETED, "账号已注销");
        }

        loginAttemptService.clearAttempts(request.getUsername());

        JwtPair pair = jwtService.issue(user.getAuthUserId(),
                user.getAuthUserUsername(),
                user.getAuthUserScope() != null ? user.getAuthUserScope() : "openid");

        recordLoginLog(user, "LOGIN", "SUCCESS", null);

        return LoginResponse.builder()
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .expiresIn(pair.expiresIn())
                .tokenType("Bearer")
                .totpRequired(false)
                .build();
    }

    public LoginResponse refresh(final RefreshRequest request) {
        String oldToken = request.getRefreshToken();
        if (tokenBlacklist.isBlacklisted(oldToken)) {
            throw new BizException(ERR_REFRESH_TOKEN_USED, "Refresh Token 已被使用");
        }
        var claims = jwtService.verify(oldToken);
        long remainingTtl = claims.exp() - System.currentTimeMillis() / MS_PER_SECOND;
        tokenBlacklist.add(oldToken, Math.max(remainingTtl, 1));

        Long userId = jwtService.getUserId(oldToken);
        JwtPair pair = jwtService.issue(userId, claims.username(), claims.scope());

        return LoginResponse.builder()
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .expiresIn(pair.expiresIn())
                .tokenType("Bearer")
                .totpRequired(false)
                .build();
    }

    public void logout(final String accessToken, final String refreshToken) {
        if (accessToken != null) {
            try {
                var claims = jwtService.verify(accessToken);
                long ttl = claims.exp() - System.currentTimeMillis() / MS_PER_SECOND;
                if (ttl > 0) {
                    tokenBlacklist.add(accessToken, ttl);
                }
            } catch (Exception ignored) { }
        }
        if (refreshToken != null) {
            try {
                var claims = jwtService.verify(refreshToken);
                long ttl = claims.exp() - System.currentTimeMillis() / MS_PER_SECOND;
                if (ttl > 0) {
                    tokenBlacklist.add(refreshToken, ttl);
                }
            } catch (Exception ignored) { }
        }
    }

    private void recordLoginLog(final AuthUser user, final String action,
                                final String result, final String failureReason) {
        AuthUserLog logEntry = new AuthUserLog();
        logEntry.setAuthUserLogUserId(user.getAuthUserId());
        logEntry.setAuthUserLogUserDisplay(user.getAuthUserUsername());
        logEntry.setAuthUserLogAction(action);
        logEntry.setAuthUserLogResult(result);
        logEntry.setCreatedTime(LocalDateTime.now());
        authUserLogMapper.insert(logEntry);
    }
}
