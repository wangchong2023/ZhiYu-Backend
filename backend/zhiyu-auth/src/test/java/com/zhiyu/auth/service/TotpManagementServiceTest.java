package com.zhiyu.auth.service;

import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.dto.TotpSetupResponse;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.UserTotp;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.service.IAuthUserService;
import com.zhiyu.ufp.auth.spi.AuthFlowContext;
import com.zhiyu.ufp.auth.spi.AuthFlowManager;
import com.zhiyu.ufp.auth.spi.AuthFlowResult;
import com.zhiyu.ufp.auth.totp.TotpService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TotpManagementServiceTest {

    @Mock private IAuthUserService authUserService;
    @Mock private TotpService totpService;
    @Mock private AuthFlowManager authFlowManager;

    @InjectMocks private TotpManagementService totpManagementService;

    @Test
    void shouldSetupTotpSuccessfully() {
        AuthUser user = AuthUser.builder()
                .authUserId(1001L).authUserUsername("testuser").build();
        when(authUserService.selectById(1001L)).thenReturn(user);
        when(totpService.getSecret(1001L)).thenReturn("BASE32SECRET");
        when(totpService.generateQrUri("testuser", "BASE32SECRET"))
                .thenReturn("otpauth://totp/ZhiYu:testuser?secret=BASE32SECRET&issuer=ZhiYu");

        TotpSetupResponse resp = totpManagementService.setupTotp(1001L);

        assertThat(resp.getSecret()).isEqualTo("BASE32SECRET");
        assertThat(resp.getQrUri()).contains("ZhiYu:testuser");
        verify(totpService).setupTotp(1001L, "testuser");
    }

    @Test
    void shouldFailSetupTotpWhenUserNotFound() {
        when(authUserService.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> totpManagementService.setupTotp(999L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(BizErrorCode.RESOURCE_NOT_FOUND.getCode());
    }

    @Test
    void shouldEnableTotpSuccessfully() {
        totpManagementService.enableTotp(1001L, "123456");

        verify(totpService).enableTotp(1001L, "123456");
    }

    @Test
    void shouldDisableTotpSuccessfully() {
        totpManagementService.disableTotp(1001L);

        verify(totpService).disableTotp(1001L);
    }

    @Test
    void shouldVerifyTotpLoginSuccessfully() {
        AuthUser user = AuthUser.builder()
                .authUserId(4001L).authUserUsername("totpuser")
                .authUserScope("openid").build();
        AuthFlowResult result = AuthFlowResult.builder()
                .user(user).scope("openid").logType("TOTP").logAction("LOGIN")
                .build();
        when(authFlowManager.authenticate(any(AuthFlowContext.class))).thenReturn(result);
        when(authFlowManager.finalizeLogin(result))
                .thenReturn(new JwtPair("full-access", "full-refresh", 900));

        LoginResponse resp = totpManagementService.verifyTotpLogin(4001L, "654321");

        assertThat(resp.getAccessToken()).isEqualTo("full-access");
        assertThat(resp.getRefreshToken()).isEqualTo("full-refresh");
        assertThat(resp.getTotpRequired()).isFalse();
    }
}