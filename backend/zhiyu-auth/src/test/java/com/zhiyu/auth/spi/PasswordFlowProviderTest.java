package com.zhiyu.auth.spi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.auth.service.CaptchaService;
import com.zhiyu.auth.service.LoginAttemptService;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.enums.AuthGrantType;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.auth.service.IAuthUserService;
import com.zhiyu.ufp.auth.spi.AuthFlowContext;
import com.zhiyu.ufp.auth.spi.AuthFlowResult;
import com.zhiyu.ufp.auth.totp.TotpService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordFlowProviderTest {

    @Mock private IAuthUserService authUserService;
    @Mock private PasswordService passwordService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private CaptchaService captchaService;
    @Mock private TotpService totpService;

    @InjectMocks private PasswordFlowProvider provider;

    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "Abc12345";
    private static final String HASH = "$2a$12$hashed";

    private AuthUser enabledUser() {
        return AuthUser.builder()
                .authUserId(1001L).authUserUsername(USERNAME)
                .authUserPassword(HASH).authUserEnable(1).authUserDeleted(0)
                .authUserScope("openid").build();
    }

    @BeforeEach
    void disableCaptchaAndTotp() {
        when(totpService.isTotpEnabled(any())).thenReturn(false);
    }

    // ── 密码登录成功 ─────────────────────────────────────────

    @Test
    void shouldAuthenticateWithUsernameAndPassword() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.PASSWORD)
                .with("username", USERNAME)
                .with("password", PASSWORD)
                .with("privacyConsent", Boolean.TRUE);

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(enabledUser());
        when(passwordService.verify(PASSWORD, HASH)).thenReturn(true);

        AuthFlowResult result = provider.authenticate(ctx);

        assertThat(result.getLogAction()).isEqualTo("LOGIN");
        assertThat(result.getLogType()).isEqualTo("PASSWORD");
        assertThat(result.getUser().getAuthUserUsername()).isEqualTo(USERNAME);
        assertThat(result.isTotpPending()).isFalse();
        verify(loginAttemptService).clearAttempts(USERNAME);
    }

    // ── 统一 account 字段（邮箱） ─────────────────────────────────

    @Test
    void shouldAuthenticateWithAccountAsEmail() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.PASSWORD)
                .with("account", "user@example.com")
                .with("password", PASSWORD)
                .with("privacyConsent", Boolean.TRUE);

        AuthUser user = AuthUser.builder()
                .authUserId(2001L).authUserUsername("emailuser")
                .authUserPassword(HASH).authUserMail("user@example.com")
                .authUserEnable(1).authUserDeleted(0)
                .authUserScope("openid").build();

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, user);
        when(passwordService.verify(PASSWORD, HASH)).thenReturn(true);

        AuthFlowResult result = provider.authenticate(ctx);

        assertThat(result.getUser().getAuthUserMail()).isEqualTo("user@example.com");
        verify(loginAttemptService).clearAttempts("user@example.com");
    }

    // ── 统一 account 字段（手机号） ─────────────────────────────────

    @Test
    void shouldAuthenticateWithAccountAsPhone() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.PASSWORD)
                .with("account", "13800138000")
                .with("password", PASSWORD)
                .with("privacyConsent", Boolean.TRUE);

        AuthUser user = AuthUser.builder()
                .authUserId(3001L).authUserUsername("phoneuser")
                .authUserPassword(HASH).authUserMobile("13800138000")
                .authUserEnable(1).authUserDeleted(0)
                .authUserScope("openid").build();

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, null, user);
        when(passwordService.verify(PASSWORD, HASH)).thenReturn(true);

        AuthFlowResult result = provider.authenticate(ctx);

        assertThat(result.getUser().getAuthUserMobile()).isEqualTo("13800138000");
        verify(loginAttemptService).clearAttempts("13800138000");
    }

    // ── privacyConsent 拒绝 ─────────────────────────────────────

    @Test
    void shouldRejectWhenPrivacyConsentIsMissing() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.PASSWORD)
                .with("username", USERNAME)
                .with("password", PASSWORD);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.VALIDATION_FAILED.getCode());
    }

    @Test
    void shouldRejectWhenPrivacyConsentIsFalse() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.PASSWORD)
                .with("username", USERNAME)
                .with("password", PASSWORD)
                .with("privacyConsent", Boolean.FALSE);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.VALIDATION_FAILED.getCode());
    }

    // ── 空凭证 ───────────────────────────────────────────────

    @Test
    void shouldRejectWhenUsernameIsBlank() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.PASSWORD)
                .with("username", "")
                .with("password", PASSWORD)
                .with("privacyConsent", Boolean.TRUE);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.VALIDATION_FAILED.getCode());
    }

    // ── 密码错误 ─────────────────────────────────────────────

    @Test
    void shouldRejectWithWrongPassword() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.PASSWORD)
                .with("username", USERNAME)
                .with("password", "WrongPass1")
                .with("privacyConsent", Boolean.TRUE);

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(enabledUser());
        when(passwordService.verify("WrongPass1", HASH)).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.INCORRECT_PASSWORD.getCode());

        verify(loginAttemptService).recordFailure(USERNAME);
        verify(loginAttemptService, never()).clearAttempts(USERNAME);
    }

    // ── 账户禁用 ─────────────────────────────────────────────

    @Test
    void shouldRejectWhenAccountDisabled() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.PASSWORD)
                .with("username", USERNAME)
                .with("password", PASSWORD)
                .with("privacyConsent", Boolean.TRUE);

        AuthUser disabled = AuthUser.builder()
                .authUserId(1001L).authUserUsername(USERNAME)
                .authUserPassword(HASH).authUserEnable(0).authUserDeleted(0).build();

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(disabled);
        when(passwordService.verify(PASSWORD, HASH)).thenReturn(true);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.ACCOUNT_DISABLED.getCode());
    }

    // ── 账户已删除 ────────────────────────────────────────────

    @Test
    void shouldRejectWhenAccountDeleted() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.PASSWORD)
                .with("username", USERNAME)
                .with("password", PASSWORD)
                .with("privacyConsent", Boolean.TRUE);

        AuthUser deleted = AuthUser.builder()
                .authUserId(1001L).authUserUsername(USERNAME)
                .authUserPassword(HASH).authUserEnable(1).authUserDeleted(1).build();

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(deleted);
        when(passwordService.verify(PASSWORD, HASH)).thenReturn(true);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.ACCOUNT_DELETED.getCode());
    }

    // ── Captcha 触发后登录成功 ─────────────────────────────────

    @Test
    void shouldAuthenticateWhenCaptchaRequired() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.PASSWORD)
                .with("username", USERNAME)
                .with("password", PASSWORD)
                .with("privacyConsent", Boolean.TRUE)
                .with("captchaToken", "captcha-tok")
                .with("captchaCode", "A3x9");

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(enabledUser());
        when(passwordService.verify(PASSWORD, HASH)).thenReturn(true);
        doThrow(new BizException(BizErrorCode.CAPTCHA_FAILED))
                .when(loginAttemptService).checkCaptchaRequired(any());

        AuthFlowResult result = provider.authenticate(ctx);

        assertThat(result.getLogAction()).isEqualTo("LOGIN");
        verify(captchaService).verify("captcha-tok", "A3x9");
    }

    // ── TOTP pending ──────────────────────────────────────────

    @Test
    void shouldReturnTotpPendingWhenTotpEnabled() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.PASSWORD)
                .with("username", USERNAME)
                .with("password", PASSWORD)
                .with("privacyConsent", Boolean.TRUE);

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(enabledUser());
        when(passwordService.verify(PASSWORD, HASH)).thenReturn(true);
        when(totpService.isTotpEnabled(1001L)).thenReturn(true);

        AuthFlowResult result = provider.authenticate(ctx);

        assertThat(result.isTotpPending()).isTrue();
        assertThat(result.getLogAction()).isEqualTo("LOGIN_TOTP_PENDING");
    }

    // ── 用户名不存在 ──────────────────────────────────────────

    @Test
    void shouldRejectWhenUserNotFound() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.PASSWORD)
                .with("username", "nonexistent")
                .with("password", PASSWORD)
                .with("privacyConsent", Boolean.TRUE);

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.INCORRECT_PASSWORD.getCode());

        verify(loginAttemptService).recordFailure("nonexistent");
    }
}
