package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.auth.dto.LoginRequest;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.oauth.OAuthField;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.auth.service.IAuthUserService;
import com.zhiyu.ufp.auth.spi.AuthFlowManager;
import com.zhiyu.ufp.auth.spi.AuthFlowResult;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final IAuthUserService authUserService;
    private final PasswordService passwordService;
    private final AuthFlowManager authFlowManager;

    public LoginResponse login(final LoginRequest request) {
        if (request.getPrivacyConsent() == null || !request.getPrivacyConsent()) {
            throw new BizException(BizErrorCode.PRIVACY_CONSENT_REQUIRED);
        }

        AuthUser user = authUserService.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserUsername, request.getUsername()));

        if (user == null || !passwordService.verify(
                request.getPassword(), user.getAuthUserPassword())) {
            throw new BizException(BizErrorCode.INCORRECT_PASSWORD);
        }
        if (!OAuthField.SCOPE_ADMIN.equals(user.getAuthUserScope())) {
            throw new BizException(BizErrorCode.ACCESS_DENIED);
        }
        if (user.getAuthUserEnable() == null || user.getAuthUserEnable() != 1) {
            throw new BizException(BizErrorCode.ACCOUNT_DISABLED);
        }

        AuthFlowResult result = AuthFlowResult.builder()
                .user(user)
                .scope(OAuthField.SCOPE_ADMIN.toLowerCase(Locale.ROOT))
                .logType("PASSWORD")
                .logAction("LOGIN")
                .build();
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
