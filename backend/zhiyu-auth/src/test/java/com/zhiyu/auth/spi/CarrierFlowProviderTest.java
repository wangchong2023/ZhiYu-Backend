package com.zhiyu.auth.spi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.auth.config.OAuthProperties;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarrierFlowProviderTest {

    @Mock private IAuthUserService authUserService;
    @Mock private OAuthProperties oAuthProperties;

    @InjectMocks private CarrierFlowProvider provider;

    private static final String APP_KEY = "mock_app_key_456";
    private static final String TOKEN = "mock_token_123";

    // ── 隐私政策拦截 ──────────────────────────────────────────

    @Test
    void shouldRejectWhenPrivacyConsentIsMissing() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.CARRIER)
                .with("carrierToken", TOKEN)
                .with("appKey", APP_KEY);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.PRIVACY_CONSENT_REQUIRED.getCode());
    }

    @Test
    void shouldRejectWhenPrivacyConsentIsFalse() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.CARRIER)
                .with("carrierToken", TOKEN)
                .with("appKey", APP_KEY)
                .with("privacyConsent", false);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.PRIVACY_CONSENT_REQUIRED.getCode());
    }

    // ── 必填参数校验 ──────────────────────────────────────────

    @Test
    void shouldRejectWhenCarrierTokenIsMissing() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.CARRIER)
                .with("appKey", APP_KEY)
                .with("privacyConsent", true);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.VALIDATION_FAILED.getCode());
    }

    // ── 模拟一键登录认证成功 ──────────────────────────────────────

    @Test
    void shouldAuthenticateSuccessfullyWithMockToken() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.CARRIER)
                .with("carrierToken", TOKEN)
                .with("appKey", APP_KEY)
                .with("privacyConsent", true);

        // 设置为空 Carrier 配置，使触发退避生成 188 手机号
        when(oAuthProperties.getCarrier()).thenReturn(null);

        // 计算 "mock_token_123".hashCode() % 10000 保证能够查到相同手机号
        final int hash = Math.abs(TOKEN.hashCode() % 10000);
        final String mockPhone = "1880000" + String.format("%04d", hash);

        AuthUser user = AuthUser.builder()
                .authUserId(9001L)
                .authUserMobile(mockPhone)
                .authUserEnable(1)
                .authUserDeleted(0)
                .authUserScope("openid")
                .build();

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        AuthFlowResult result = provider.authenticate(ctx);

        assertThat(result.getLogType()).isEqualTo("CARRIER");
        assertThat(result.getLogAction()).isEqualTo("LOGIN");
        assertThat(result.isNewUser()).isFalse();
        assertThat(result.getUser().getAuthUserMobile()).isEqualTo(mockPhone);
    }

    // ── 一键登录注册新用户 ────────────────────────────────────────

    @Test
    void shouldAutoRegisterNewUserSuccessfully() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.CARRIER)
                .with("carrierToken", TOKEN)
                .with("appKey", APP_KEY)
                .with("privacyConsent", true);

        when(oAuthProperties.getCarrier()).thenReturn(null);
        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        final int hash = Math.abs(TOKEN.hashCode() % 10000);
        final String mockPhone = "1880000" + String.format("%04d", hash);

        AuthFlowResult result = provider.authenticate(ctx);

        assertThat(result.isNewUser()).isTrue();
        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserService).insert(captor.capture());
        assertThat(captor.getValue().getAuthUserMobile()).isEqualTo(mockPhone);
        assertThat(captor.getValue().getAuthUserEnable()).isEqualTo(1);
    }

    // ── 账号状态校验 ──────────────────────────────────────────

    @Test
    void shouldRejectWhenUserIsDisabled() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.CARRIER)
                .with("carrierToken", TOKEN)
                .with("appKey", APP_KEY)
                .with("privacyConsent", true);

        when(oAuthProperties.getCarrier()).thenReturn(null);

        AuthUser disabled = AuthUser.builder()
                .authUserId(9001L)
                .authUserEnable(0)
                .authUserDeleted(0)
                .build();
        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(disabled);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.ACCOUNT_DISABLED.getCode());
    }
}
