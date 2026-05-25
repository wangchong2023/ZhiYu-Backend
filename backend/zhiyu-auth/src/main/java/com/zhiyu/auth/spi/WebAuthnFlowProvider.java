package com.zhiyu.auth.spi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.enums.AuthGrantType;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.spi.AuthFlowContext;
import com.zhiyu.ufp.auth.spi.AuthFlowProvider;
import com.zhiyu.ufp.auth.spi.AuthFlowResult;
import com.zhiyu.ufp.auth.webauthn.WebAuthnService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class WebAuthnFlowProvider implements AuthFlowProvider {

    private static final int ERR_USER_NOT_FOUND = 40401;

    private final WebAuthnService webAuthnService;
    private final AuthUserMapper authUserMapper;

    @Override
    public AuthGrantType supportedGrantType() {
        return AuthGrantType.WEB_AUTHN;
    }

    @Override
    public AuthFlowResult authenticate(final AuthFlowContext context) {
        String challengeId = context.get("challengeId");
        String credentialJson = context.get("credentialJson");

        String username;
        try {
            var result = webAuthnService.finishAuthentication(challengeId, credentialJson);
            username = result.getUsername();
        } catch (IOException | AssertionFailedException e) {
            throw new BizException(BizErrorCode.WEBAUTHN_FAILED, e);
        }

        AuthUser user = authUserMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserUsername, username));
        if (user == null) {
            throw new BizException(ERR_USER_NOT_FOUND, "User not found");
        }

        String scope = user.getAuthUserScope() != null ? user.getAuthUserScope() : "FULL";

        return AuthFlowResult.builder()
                .user(user)
                .scope(scope)
                .logType("WEBAUTHN")
                .logAction("LOGIN")
                .build();
    }
}
