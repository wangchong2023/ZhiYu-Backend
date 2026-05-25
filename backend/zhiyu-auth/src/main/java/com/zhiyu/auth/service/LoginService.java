package com.zhiyu.auth.service;

import com.zhiyu.auth.dto.LoginRequest;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.dto.RefreshRequest;
import com.zhiyu.ufp.auth.enums.AuthGrantType;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.oauth.OAuthField;
import com.zhiyu.ufp.auth.spi.AuthFlowContext;
import com.zhiyu.ufp.auth.spi.AuthFlowManager;
import com.zhiyu.ufp.auth.spi.AuthFlowResult;
import com.zhiyu.ufp.auth.token.TokenBlacklist;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private static final long MS_PER_SECOND = 1000L;

    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final AuthFlowManager authFlowManager;

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
}