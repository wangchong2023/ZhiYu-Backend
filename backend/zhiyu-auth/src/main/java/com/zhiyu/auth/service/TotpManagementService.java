package com.zhiyu.auth.service;

import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.dto.TotpSetupResponse;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.enums.AuthGrantType;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.service.IAuthUserService;
import com.zhiyu.ufp.auth.spi.AuthFlowContext;
import com.zhiyu.ufp.auth.spi.AuthFlowManager;
import com.zhiyu.ufp.auth.spi.AuthFlowResult;
import com.zhiyu.ufp.auth.totp.TotpService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TotpManagementService {

    private final IAuthUserService authUserService;
    private final TotpService totpService;
    private final AuthFlowManager authFlowManager;

    @Transactional(rollbackFor = Exception.class)
    public TotpSetupResponse setupTotp(final Long userId) {
        AuthUser user = authUserService.selectById(userId);
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
                .tokenType("Bearer")
                .totpRequired(false)
                .build();
    }
}