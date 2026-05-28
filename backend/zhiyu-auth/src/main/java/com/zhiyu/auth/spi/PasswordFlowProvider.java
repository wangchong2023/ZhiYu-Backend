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
        String account = context.get("account");
        String username = context.get("username");
        String password = context.get("password");

        // 统一账号：优先使用 account（支持用户名/邮箱/手机号），回退到 username
        String loginName = (account != null && !account.isBlank()) ? account : username;
        if (loginName == null || loginName.isBlank()
                || password == null || password.isBlank()) {
            throw new BizException(BizErrorCode.VALIDATION_FAILED);
        }

        loginAttemptService.checkLocked(loginName);

        final Object privacyConsent = context.get("privacyConsent");
        if (!Boolean.TRUE.equals(privacyConsent)) {
            throw new BizException(BizErrorCode.PRIVACY_CONSENT_REQUIRED);
        }

        boolean captchaRequired = false;
        try {
            loginAttemptService.checkCaptchaRequired(loginName);
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

        AuthUser user = findUserByAccount(loginName);

        if (user == null || !passwordService.verify(password, user.getAuthUserPassword())) {
            loginAttemptService.recordFailure(loginName);
            throw new BizException(BizErrorCode.INCORRECT_PASSWORD);
        }

        if (user.getAuthUserEnable() == null || user.getAuthUserEnable() != 1) {
            throw new BizException(BizErrorCode.ACCOUNT_DISABLED);
        }
        if (user.getAuthUserDeleted() != null && user.getAuthUserDeleted() == 1) {
            throw new BizException(BizErrorCode.ACCOUNT_DELETED);
        }

        loginAttemptService.clearAttempts(loginName);

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

    /**
     * 按统一账号查找用户 — 支持用户名、邮箱、手机号。
     */
    private AuthUser findUserByAccount(final String account) {
        AuthUser user = authUserService.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getAuthUserUsername, account));
        if (user == null && account.contains("@")) {
            user = authUserService.selectOne(new LambdaQueryWrapper<AuthUser>()
                    .eq(AuthUser::getAuthUserMail, account));
        }
        if (user == null && account.matches("^1[3-9]\\d{9}$")) {
            user = authUserService.selectOne(new LambdaQueryWrapper<AuthUser>()
                    .eq(AuthUser::getAuthUserMobile, account));
        }
        return user;
    }
}
