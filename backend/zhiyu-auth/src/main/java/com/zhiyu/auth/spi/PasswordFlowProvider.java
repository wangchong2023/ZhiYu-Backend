package com.zhiyu.auth.spi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.auth.service.CaptchaService;
import com.zhiyu.auth.service.LoginAttemptService;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.enums.AuthGrantType;
import com.zhiyu.ufp.auth.oauth.OAuthField;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.auth.service.IAuthUserService;
import com.zhiyu.ufp.auth.spi.AuthFlowContext;
import com.zhiyu.ufp.auth.spi.AuthFlowProvider;
import com.zhiyu.ufp.auth.spi.AuthFlowResult;
import com.zhiyu.ufp.auth.totp.TotpService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordFlowProvider implements AuthFlowProvider {

    private final IAuthUserService authUserService;
    private final PasswordService passwordService;
    private final LoginAttemptService loginAttemptService;
    private final CaptchaService captchaService;
    private final TotpService totpService;

    @Override
    public AuthGrantType supportedGrantType() {
        return AuthGrantType.PASSWORD;
    }

    @Override
    public AuthFlowResult authenticate(final AuthFlowContext context) {
        String username = context.get("username");
        String password = context.get("password");

        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {
            throw new BizException(BizErrorCode.VALIDATION_FAILED);
        }

        loginAttemptService.checkLocked(username);

        boolean captchaRequired = false;
        try {
            loginAttemptService.checkCaptchaRequired(username);
        } catch (BizException e) {
            captchaRequired = true;
        }

        if (captchaRequired) {
            String captchaToken = context.get("captchaToken");
            String captchaCode = context.get("captchaCode");
            if (captchaToken == null || captchaCode == null) {
                throw new BizException(BizErrorCode.CAPTCHA_FAILED);
            }
            captchaService.verify(captchaToken, captchaCode);
        }

        AuthUser user = authUserService.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserUsername, username));

        if (user == null || !passwordService.verify(password, user.getAuthUserPassword())) {
            loginAttemptService.recordFailure(username);
            throw new BizException(BizErrorCode.INCORRECT_PASSWORD);
        }

        if (user.getAuthUserEnable() == null || user.getAuthUserEnable() != 1) {
            throw new BizException(BizErrorCode.ACCOUNT_DISABLED);
        }
        if (user.getAuthUserDeleted() != null && user.getAuthUserDeleted() == 1) {
            throw new BizException(BizErrorCode.ACCOUNT_DELETED);
        }

        loginAttemptService.clearAttempts(username);

        boolean totpPending = totpService.isTotpEnabled(user.getAuthUserId());
        String scope = user.getAuthUserScope() != null ? user.getAuthUserScope() : OAuthField.SCOPE_OPENID;

        return AuthFlowResult.builder()
                .user(user)
                .scope(scope)
                .logType("PASSWORD")
                .logAction(totpPending ? "LOGIN_TOTP_PENDING" : "LOGIN")
                .totpPending(totpPending)
                .build();
    }
}
