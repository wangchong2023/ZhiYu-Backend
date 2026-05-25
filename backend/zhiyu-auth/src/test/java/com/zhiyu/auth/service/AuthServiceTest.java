package com.zhiyu.auth.service;

import com.zhiyu.auth.dto.LoginRequest;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.dto.RefreshRequest;
import com.zhiyu.auth.dto.RegisterRequest;
import com.zhiyu.auth.dto.RegisterResponse;
import com.zhiyu.auth.dto.SendSmsRequest;
import com.zhiyu.auth.dto.TotpSetupResponse;
import com.zhiyu.ufp.common.cache.CacheKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private RegistrationService registrationService;
    @Mock private LoginService loginService;
    @Mock private TotpManagementService totpManagementService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private AuthService authService;

    // ── Registration ──────────────────────────────────────────

    @Test
    void shouldRegisterSuccessfully() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setPassword("Abc12345");
        req.setEmail("test@example.com");
        req.setCaptchaToken("tok");
        req.setCaptchaCode("A3x9");

        RegisterResponse expected = RegisterResponse.builder()
                .userId(1L).username("testuser").build();
        when(registrationService.register(any())).thenReturn(expected);

        RegisterResponse resp = authService.register(req);

        assertThat(resp.getUsername()).isEqualTo("testuser");
        verify(registrationService).register(req);
    }

    @Test
    void shouldDelegateSendSmsToRedis() {
        SendSmsRequest req = new SendSmsRequest();
        req.setPhone("13800138000");
        req.setScene("login");

        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        authService.sendSms(req);

        verify(valueOps).set(any(String.class), any(String.class), any(java.time.Duration.class));
    }

    // ── Login ─────────────────────────────────────────────────

    @Test
    void shouldLoginSuccessfully() {
        LoginRequest req = new LoginRequest();
        req.setUsername("testuser");
        req.setPassword("Abc12345");

        LoginResponse expected = LoginResponse.builder()
                .accessToken("access").refreshToken("refresh").expiresIn(900)
                .tokenType("Bearer").totpRequired(false).build();
        when(loginService.login(any())).thenReturn(expected);

        LoginResponse resp = authService.login(req);

        assertThat(resp.getAccessToken()).isEqualTo("access");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh");
        verify(loginService).login(req);
    }

    // ── Token Refresh ─────────────────────────────────────────

    @Test
    void shouldRefreshToken() {
        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("old-refresh");

        LoginResponse expected = LoginResponse.builder()
                .accessToken("new-access").refreshToken("new-refresh").expiresIn(900)
                .tokenType("Bearer").totpRequired(false).build();
        when(loginService.refresh(any())).thenReturn(expected);

        LoginResponse resp = authService.refresh(req);

        assertThat(resp.getAccessToken()).isEqualTo("new-access");
        verify(loginService).refresh(req);
    }

    // ── Logout ────────────────────────────────────────────────

    @Test
    void shouldLogoutAndBlacklistTokens() {
        authService.logout("access-token", "refresh-token");

        verify(loginService).logout("access-token", "refresh-token");
    }

    // ── TOTP Setup ────────────────────────────────────────────

    @Test
    void shouldSetupTotpSuccessfully() {
        TotpSetupResponse expected = TotpSetupResponse.builder()
                .secret("BASE32SECRET")
                .qrUri("otpauth://totp/ZhiYu:testuser?secret=BASE32SECRET&issuer=ZhiYu")
                .build();
        when(totpManagementService.setupTotp(1001L)).thenReturn(expected);

        TotpSetupResponse resp = authService.setupTotp(1001L);

        assertThat(resp.getSecret()).isEqualTo("BASE32SECRET");
        verify(totpManagementService).setupTotp(1001L);
    }

    @Test
    void shouldEnableTotpSuccessfully() {
        authService.enableTotp(1001L, "123456");

        verify(totpManagementService).enableTotp(1001L, "123456");
    }

    @Test
    void shouldDisableTotpSuccessfully() {
        authService.disableTotp(1001L);

        verify(totpManagementService).disableTotp(1001L);
    }

    // ── TOTP Login Verification ───────────────────────────────

    @Test
    void shouldVerifyTotpLoginSuccessfully() {
        LoginResponse expected = LoginResponse.builder()
                .accessToken("full-access").refreshToken("full-refresh").expiresIn(900)
                .tokenType("Bearer").totpRequired(false).build();
        when(totpManagementService.verifyTotpLogin(4001L, "654321")).thenReturn(expected);

        LoginResponse resp = authService.verifyTotpLogin(4001L, "654321");

        assertThat(resp.getAccessToken()).isEqualTo("full-access");
        verify(totpManagementService).verifyTotpLogin(4001L, "654321");
    }

    // ── Edge cases ────────────────────────────────────────────

    @Test
    void shouldLogoutWithBothNullTokensGracefully() {
        authService.logout(null, null);
        verify(loginService).logout(null, null);
    }
}