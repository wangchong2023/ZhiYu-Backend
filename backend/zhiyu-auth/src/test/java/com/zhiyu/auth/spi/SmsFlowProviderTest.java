package com.zhiyu.auth.spi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.enums.AuthGrantType;
import com.zhiyu.ufp.auth.service.IAuthUserService;
import com.zhiyu.ufp.auth.spi.AuthFlowContext;
import com.zhiyu.ufp.auth.spi.AuthFlowResult;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsFlowProviderTest {

    @Mock private IAuthUserService authUserService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks private SmsFlowProvider provider;

    private static final String PHONE = "13800138000";
    private static final String SMS_CODE = "123456";

    // ── 隐私政策强制校验 ──────────────────────────────────────

    @Test
    void shouldRejectWhenPrivacyConsentIsMissing() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.SMS)
                .with("phone", PHONE)
                .with("smsCode", SMS_CODE);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.PRIVACY_CONSENT_REQUIRED.getCode());
    }

    @Test
    void shouldRejectWhenPrivacyConsentIsFalse() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.SMS)
                .with("phone", PHONE)
                .with("smsCode", SMS_CODE)
                .with("privacyConsent", false);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.PRIVACY_CONSENT_REQUIRED.getCode());
    }

    // ── 短信码登录成功 ────────────────────────────────────────

    @Test
    void shouldAuthenticateWithValidSmsCode() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.SMS)
                .with("phone", PHONE)
                .with("smsCode", SMS_CODE)
                .with("privacyConsent", true);

        AuthUser user = AuthUser.builder()
                .authUserId(1001L).authUserUsername("smsuser")
                .authUserMobile(PHONE).authUserEnable(1).authUserDeleted(0)
                .authUserScope("openid").build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(SMS_CODE);
        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        AuthFlowResult result = provider.authenticate(ctx);

        assertThat(result.getLogAction()).isEqualTo("LOGIN");
        assertThat(result.getLogType()).isEqualTo("SMS");
        assertThat(result.isNewUser()).isFalse();
        assertThat(result.getUser().getAuthUserMobile()).isEqualTo(PHONE);
        verify(valueOperations).get(anyString());
    }

    // ── 自动注册新用户（登录即注册） ──────────────────────────────

    @Test
    void shouldAutoRegisterNewUserWhenPhoneNotFound() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.SMS)
                .with("phone", PHONE)
                .with("smsCode", SMS_CODE)
                .with("privacyConsent", true);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(SMS_CODE);
        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AuthFlowResult result = provider.authenticate(ctx);

        assertThat(result.isNewUser()).isTrue();
        assertThat(result.getLogAction()).isEqualTo("LOGIN");
        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserService).insert(captor.capture());
        assertThat(captor.getValue().getAuthUserMobile()).isEqualTo(PHONE);
        assertThat(captor.getValue().getAuthUserMobileVerified()).isEqualTo(1);
        assertThat(captor.getValue().getAuthUserEnable()).isEqualTo(1);
    }

    // ── SMS Code 为空必须拒绝（安全修复） ──────────────────────────

    @Test
    void shouldRejectWhenSmsCodeIsNull() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.SMS)
                .with("phone", PHONE)
                .with("smsCode", null)
                .with("privacyConsent", true);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.SMS_CODE_INCORRECT.getCode());
    }

    @Test
    void shouldRejectWhenSmsCodeIsBlank() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.SMS)
                .with("phone", PHONE)
                .with("smsCode", "   ")
                .with("privacyConsent", true);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.SMS_CODE_INCORRECT.getCode());
    }

    // ── 手机号为空 ────────────────────────────────────────────

    @Test
    void shouldRejectWhenPhoneIsBlank() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.SMS)
                .with("phone", "")
                .with("smsCode", SMS_CODE)
                .with("privacyConsent", true);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.VALIDATION_FAILED.getCode());
    }

    // ── 验证码错误 ────────────────────────────────────────────

    @Test
    void shouldRejectWhenSmsCodeDoesNotMatch() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.SMS)
                .with("phone", PHONE)
                .with("smsCode", "999999")
                .with("privacyConsent", true);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("123456");

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.SMS_CODE_INCORRECT.getCode());
    }

    // ── 账户禁用 ─────────────────────────────────────────────

    @Test
    void shouldRejectWhenSmsUserDisabled() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.SMS)
                .with("phone", PHONE)
                .with("smsCode", SMS_CODE)
                .with("privacyConsent", true);

        AuthUser disabled = AuthUser.builder()
                .authUserId(1001L).authUserMobile(PHONE)
                .authUserEnable(0).authUserDeleted(0).build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(SMS_CODE);
        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(disabled);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.ACCOUNT_DISABLED.getCode());
    }

    // ── 验证码已过期 ──────────────────────────────────────────

    @Test
    void shouldRejectWhenSmsCodeExpired() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.SMS)
                .with("phone", PHONE)
                .with("smsCode", SMS_CODE)
                .with("privacyConsent", true);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.SMS_CODE_INCORRECT.getCode());
    }
}
