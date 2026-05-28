package com.zhiyu.auth.spi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.enums.AuthGrantType;
import com.zhiyu.ufp.auth.oauth.OAuthField;
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
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestFlowProviderTest {

    @Mock private IAuthUserService authUserService;

    @InjectMocks private GuestFlowProvider provider;

    private static final String DEVICE_ID = "device_xyz_789";

    // ── 隐私政策拦截 ──────────────────────────────────────────

    @Test
    void shouldRejectWhenPrivacyConsentIsMissing() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.GUEST)
                .with("deviceId", DEVICE_ID);

        assertThatThrownBy(() -> provider.authenticate(ctx))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.PRIVACY_CONSENT_REQUIRED.getCode());
    }

    // ── 带设备 ID 游客登录成功（新建用户） ────────────────────────────

    @Test
    void shouldCreateNewGuestWhenDeviceFirstLogin() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.GUEST)
                .with("deviceId", DEVICE_ID)
                .with("privacyConsent", true);

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AuthFlowResult result = provider.authenticate(ctx);

        assertThat(result.isNewUser()).isTrue();
        assertThat(result.getScope()).isEqualTo(OAuthField.SCOPE_GUEST);
        assertThat(result.getLogType()).isEqualTo("GUEST");

        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserService).insert(captor.capture());

        final String expectedUsername = "guest_" + DigestUtils.md5DigestAsHex(
                DEVICE_ID.getBytes(StandardCharsets.UTF_8));
        assertThat(captor.getValue().getAuthUserUsername()).isEqualTo(expectedUsername);
    }

    // ── 带设备 ID 游客登录成功（查询已有用户，保证幂等） ──────────────────────

    @Test
    void shouldReturnExistingGuestWhenDeviceLoginsAgain() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.GUEST)
                .with("deviceId", DEVICE_ID)
                .with("privacyConsent", true);

        final String expectedUsername = "guest_" + DigestUtils.md5DigestAsHex(
                DEVICE_ID.getBytes(StandardCharsets.UTF_8));

        AuthUser existingUser = AuthUser.builder()
                .authUserId(5002L)
                .authUserUsername(expectedUsername)
                .authUserEnable(1)
                .authUserDeleted(0)
                .build();

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingUser);

        AuthFlowResult result = provider.authenticate(ctx);

        assertThat(result.isNewUser()).isFalse();
        assertThat(result.getUser().getAuthUserUsername()).isEqualTo(expectedUsername);
        assertThat(result.getScope()).isEqualTo(OAuthField.SCOPE_GUEST);
    }

    // ── 无设备 ID 时每次登录生成全新 UUID 游客账号 ──────────────────────

    @Test
    void shouldGenerateRandomGuestWhenNoDeviceId() {
        AuthFlowContext ctx = AuthFlowContext.of(AuthGrantType.GUEST)
                .with("privacyConsent", true);

        when(authUserService.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AuthFlowResult result = provider.authenticate(ctx);

        assertThat(result.isNewUser()).isTrue();
        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserService).insert(captor.capture());
        assertThat(captor.getValue().getAuthUserUsername()).startsWith("guest_");
    }
}
