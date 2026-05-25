package com.zhiyu.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.auth.converter.AuthConverter;
import com.zhiyu.auth.dto.LoginRequest;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.dto.RefreshRequest;
import com.zhiyu.auth.dto.RegisterRequest;
import com.zhiyu.auth.dto.RegisterResponse;
import com.zhiyu.auth.dto.SendSmsRequest;
import com.zhiyu.auth.dto.TotpSetupResponse;
import com.zhiyu.auth.validator.AuthValidator;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.oauth.OAuthField;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.auth.token.TokenBlacklist;
import com.zhiyu.ufp.auth.totp.TotpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REGISTER_RATE_PREFIX = "register:rate:";
    private static final int MAX_REGISTER_PER_IP_PER_HOUR = 3;
    private static final long MS_PER_SECOND = 1000L;
    private static final long TOTP_PENDING_TTL = 300L;
    private final AuthUserMapper authUserMapper;
    private final AuthUserLogMapper authUserLogMapper;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final CaptchaService captchaService;
    private final LoginAttemptService loginAttemptService;
    private final AuthValidator authValidator;
    private final StringRedisTemplate redisTemplate;
    private final TotpService totpService;

    @Transactional(rollbackFor = Exception.class)
    public RegisterResponse register(final RegisterRequest request) {
        authValidator.validateUsername(request.getUsername());
        authValidator.validatePassword(request.getPassword());
        authValidator.validateEmail(request.getEmail());
        captchaService.verify(request.getCaptchaToken(), request.getCaptchaCode());

        if (authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserUsername, request.getUsername())) != null) {
            throw new BizException(BizErrorCode.USERNAME_TAKEN);
        }
        if (authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserMail, request.getEmail())) != null) {
            throw new BizException(BizErrorCode.EMAIL_TAKEN);
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
        String grantType = request.getGrantType() != null ? request.getGrantType() : "password";

        if ("sms_code".equals(grantType)) {
            return loginBySms(request);
        }

        if (request.getUsername() == null || request.getUsername().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BizException(BizErrorCode.VALIDATION_FAILED);
        }

        loginAttemptService.checkLocked(request.getUsername());

        boolean captchaRequired = false;
        try {
            loginAttemptService.checkCaptchaRequired(request.getUsername());
        } catch (BizException e) {
            captchaRequired = true;
        }

        if (captchaRequired) {
            if (request.getCaptchaToken() == null || request.getCaptchaCode() == null) {
                throw new BizException(BizErrorCode.CAPTCHA_FAILED);
            }
            captchaService.verify(request.getCaptchaToken(), request.getCaptchaCode());
        }

        AuthUser user = authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserUsername, request.getUsername()));

        if (user == null || !passwordService.verify(request.getPassword(), user.getAuthUserPassword())) {
            loginAttemptService.recordFailure(request.getUsername());
            throw new BizException(BizErrorCode.INCORRECT_PASSWORD);
        }

        if (user.getAuthUserEnable() == null || user.getAuthUserEnable() != 1) {
            throw new BizException(BizErrorCode.ACCOUNT_DISABLED);
        }
        if (user.getAuthUserDeleted() != null && user.getAuthUserDeleted() == 1) {
            throw new BizException(BizErrorCode.ACCOUNT_DELETED);
        }

        loginAttemptService.clearAttempts(request.getUsername());

        if (totpService.isTotpEnabled(user.getAuthUserId())) {
            String pendingToken = jwtService.issuePendingToken(
                    user.getAuthUserId(), user.getAuthUserUsername());
            recordLoginLog(user, "LOGIN_TOTP_PENDING", "PENDING");
            return LoginResponse.builder()
                    .accessToken(pendingToken)
                    .expiresIn(TOTP_PENDING_TTL)
                    .tokenType(OAuthField.TOKEN_TYPE)
                    .totpRequired(true)
                    .build();
        }

        JwtPair pair = jwtService.issue(user.getAuthUserId(),
                user.getAuthUserUsername(),
                user.getAuthUserScope() != null ? user.getAuthUserScope() : OAuthField.SCOPE_OPENID);

        recordLoginLog(user, "LOGIN", "SUCCESS");

        return LoginResponse.builder()
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .expiresIn(pair.expiresIn())
                .tokenType(OAuthField.TOKEN_TYPE)
                .totpRequired(false)
                .build();
    }

    public LoginResponse refresh(final RefreshRequest request) {
        String oldToken = request.getRefreshToken();
        if (tokenBlacklist.isBlacklisted(oldToken)) {
            throw new BizException(BizErrorCode.TOKEN_REUSE_DETECTED);
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
                .tokenType(OAuthField.TOKEN_TYPE)
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

    // ── SMS ─────────────────────────────────────────────────

    public void sendSms(final SendSmsRequest request) {
        String code = String.format("%06d",
                ThreadLocalRandom.current().nextInt(1_000_000));
        String redisKey = "sms:" + request.getScene() + ":" + request.getPhone();
        redisTemplate.opsForValue().set(redisKey, code, java.time.Duration.ofMinutes(5));
        if (log.isInfoEnabled()) {
            log.info("[SMS mock] To: {} | Scene: {} | Code: {}",
                    request.getPhone(), request.getScene(), code);
        }
    }

    // ── SMS Login ───────────────────────────────────────────

    private LoginResponse loginBySms(final LoginRequest request) {
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new BizException(BizErrorCode.VALIDATION_FAILED);
        }

        // SMS code is optional — skip verification when not provided (dev convenience)
        boolean hasSmsCode = request.getSmsCode() != null && !request.getSmsCode().isBlank();
        if (hasSmsCode) {
            String redisKey = "sms:admin_login:" + request.getPhone();
            String storedCode = redisTemplate.opsForValue().get(redisKey);
            if (storedCode == null || !storedCode.equals(request.getSmsCode())) {
                throw new BizException(BizErrorCode.SMS_CODE_INCORRECT);
            }
            redisTemplate.delete(redisKey);
        }

        AuthUser user = authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserMobile, request.getPhone()));
        if (user == null) {
            user = new AuthUser();
            user.setAuthUserMobile(request.getPhone());
            user.setAuthUserMobileVerified(1);
            user.setAuthUserScope(OAuthField.SCOPE_OPENID);
            user.setAuthUserEnable(1);
            authUserMapper.insert(user);
            if (log.isInfoEnabled()) {
                log.info("Auto-registered user from SMS login: userId={}, phone={}",
                        user.getAuthUserId(), request.getPhone());
            }
        }

        if (user.getAuthUserEnable() == null || user.getAuthUserEnable() != 1) {
            throw new BizException(BizErrorCode.ACCOUNT_DISABLED);
        }
        if (user.getAuthUserDeleted() != null && user.getAuthUserDeleted() == 1) {
            throw new BizException(BizErrorCode.ACCOUNT_DELETED);
        }

        String scope = user.getAuthUserScope() != null ? user.getAuthUserScope() : OAuthField.SCOPE_OPENID;
        JwtPair pair = jwtService.issue(user.getAuthUserId(),
                user.getAuthUserUsername() != null ? user.getAuthUserUsername()
                        : "user_" + user.getAuthUserId(),
                scope);

        recordLoginLog(user, "LOGIN", "SUCCESS");
        return LoginResponse.builder()
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .expiresIn(pair.expiresIn())
                .tokenType(OAuthField.TOKEN_TYPE)
                .totpRequired(false)
                .build();
    }

    // ── TOTP ────────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public TotpSetupResponse setupTotp(final Long userId) {
        String username = resolveUsername(userId);
        totpService.setupTotp(userId, username);
        String secret = totpService.getSecret(userId);
        String qrUri = totpService.generateQrUri(username, secret);
        return TotpSetupResponse.builder()
                .secret(secret)
                .qrUri(qrUri)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void enableTotp(final Long userId, final String code) {
        totpService.enableTotp(userId, code);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disableTotp(final Long userId) {
        totpService.disableTotp(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginResponse verifyTotpLogin(final Long userId, final String code) {
        if (!totpService.verifyTotp(userId, code)) {
            throw new BizException(BizErrorCode.TOTP_INCORRECT);
        }

        AuthUser user = resolveUser(userId);
        String scope = user.getAuthUserScope() != null ? user.getAuthUserScope() : OAuthField.SCOPE_OPENID;

        JwtPair pair = jwtService.issue(userId, user.getAuthUserUsername(), scope);
        return LoginResponse.builder()
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .expiresIn(pair.expiresIn())
                .tokenType(OAuthField.TOKEN_TYPE)
                .totpRequired(false)
                .build();
    }

    private String resolveUsername(final Long userId) {
        return resolveUser(userId).getAuthUserUsername();
    }

    private AuthUser resolveUser(final Long userId) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        return user;
    }

    private void recordLoginLog(final AuthUser user, final String action,
                                final String result) {
        AuthUserLog logEntry = new AuthUserLog();
        logEntry.setAuthUserLogUserId(user.getAuthUserId());
        logEntry.setAuthUserLogUserDisplay(user.getAuthUserUsername());
        logEntry.setAuthUserLogAction(action);
        logEntry.setAuthUserLogType("PASSWORD");
        logEntry.setAuthUserLogResult(result);
        logEntry.setCreatedTime(LocalDateTime.now());
        authUserLogMapper.insert(logEntry);
    }
}
