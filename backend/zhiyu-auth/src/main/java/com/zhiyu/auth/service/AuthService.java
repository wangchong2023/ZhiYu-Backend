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
import com.zhiyu.ufp.common.cache.CacheKeys;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.enums.AuthGrantType;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.oauth.OAuthField;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.auth.spi.AuthFlowContext;
import com.zhiyu.ufp.auth.spi.AuthFlowManager;
import com.zhiyu.ufp.auth.spi.AuthFlowResult;
import com.zhiyu.ufp.auth.token.TokenBlacklist;
import com.zhiyu.ufp.auth.totp.TotpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long MS_PER_SECOND = 1000L;
    private final AuthUserMapper authUserMapper;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final CaptchaService captchaService;
    private final LoginAttemptService loginAttemptService;
    private final AuthValidator authValidator;
    private final StringRedisTemplate redisTemplate;
    private final TotpService totpService;
    private final AuthFlowManager authFlowManager;

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
        AuthGrantType grantType = resolveGrantType(request.getGrantType());

        AuthFlowContext context = buildContext(grantType, request);
        AuthFlowResult result = authFlowManager.authenticate(context);
        JwtPair pair = authFlowManager.finalizeLogin(result);

        return LoginResponse.builder()
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .expiresIn(pair.expiresIn())
                .tokenType(OAuthField.TOKEN_TYPE)
                .totpRequired(result.isTotpPending())
                .isNewUser(result.isNewUser())
                .build();
    }

    private AuthGrantType resolveGrantType(final String raw) {
        if (raw == null || "password".equals(raw)) {
            return AuthGrantType.PASSWORD;
        }
        if ("sms_code".equals(raw)) {
            return AuthGrantType.SMS;
        }
        return AuthGrantType.PASSWORD;
    }

    private AuthFlowContext buildContext(final AuthGrantType grantType, final LoginRequest request) {
        return AuthFlowContext.of(grantType)
                .with("username", request.getUsername())
                .with("password", request.getPassword())
                .with("phone", request.getPhone())
                .with("smsCode", request.getSmsCode())
                .with("captchaToken", request.getCaptchaToken())
                .with("captchaCode", request.getCaptchaCode())
                .with("privacyConsent", request.getPrivacyConsent());
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
        String redisKey = CacheKeys.key(CacheKeys.SMS_CODE, request.getScene(), request.getPhone());
        redisTemplate.opsForValue().set(redisKey, code, java.time.Duration.ofMinutes(5));
        if (log.isInfoEnabled()) {
            log.info("[SMS mock] To: {} | Scene: {} | Code: {}",
                    request.getPhone(), request.getScene(), code);
        }
    }

    // ── TOTP ────────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public TotpSetupResponse setupTotp(final Long userId) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        String username = user.getAuthUserUsername();
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
        AuthFlowContext context = AuthFlowContext.of(AuthGrantType.TOTP)
                .with("userId", userId)
                .with("totpCode", code);
        AuthFlowResult result = authFlowManager.authenticate(context);
        JwtPair pair = authFlowManager.finalizeLogin(result);

        return LoginResponse.builder()
                .accessToken(pair.accessToken())
                .refreshToken(pair.refreshToken())
                .expiresIn(pair.expiresIn())
                .tokenType(OAuthField.TOKEN_TYPE)
                .totpRequired(false)
                .build();
    }
}
