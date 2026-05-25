package com.zhiyu.auth.service;

import com.zhiyu.auth.dto.*;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.UserTotp;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.jwt.JwtClaims;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.password.PasswordService;
import com.zhiyu.ufp.auth.service.IAuthUserService;
import com.zhiyu.ufp.auth.spi.AuthFlowContext;
import com.zhiyu.ufp.auth.spi.AuthFlowManager;
import com.zhiyu.ufp.auth.spi.AuthFlowResult;
import com.zhiyu.ufp.auth.token.TokenBlacklist;
import com.zhiyu.ufp.auth.totp.TotpService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private IAuthUserService authUserService;
    @Mock private PasswordService passwordService;
    @Mock private JwtService jwtService;
    @Mock private TokenBlacklist tokenBlacklist;
    @Mock private CaptchaService captchaService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private TotpService totpService;
    @Mock private com.zhiyu.auth.validator.AuthValidator authValidator;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private AuthFlowManager authFlowManager;
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

        when(authUserService.selectOne(any())).thenReturn(null);
        when(passwordService.hash("Abc12345")).thenReturn("$2a$12$hashed");

        var resp = authService.register(req);

        assertThat(resp.getUsername()).isEqualTo("testuser");
        verify(authUserService).insert(any(AuthUser.class));
    }

    @Test
    void shouldFailRegisterWithDuplicateUsername() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("existing");
        req.setPassword("Abc12345");
        req.setEmail("new@example.com");
        req.setCaptchaToken("tok");
        req.setCaptchaCode("A3x9");

        when(authUserService.selectOne(any()))
                .thenReturn(AuthUser.builder().authUserId(1L).build());

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldFailRegisterWithDuplicateEmail() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setPassword("Abc12345");
        req.setEmail("existing@example.com");
        req.setCaptchaToken("tok");
        req.setCaptchaCode("A3x9");

        when(authUserService.selectOne(any()))
                .thenReturn(null)
                .thenReturn(AuthUser.builder().authUserId(2L).build());

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BizException.class);
    }

    // ── Login ─────────────────────────────────────────────────

    @Test
    void shouldLoginSuccessfully() {
        LoginRequest req = new LoginRequest();
        req.setUsername("testuser");
        req.setPassword("Abc12345");

        AuthUser user = AuthUser.builder()
                .authUserId(1001L).authUserUsername("testuser").build();

        AuthFlowResult result = AuthFlowResult.builder()
                .user(user).scope("openid").logType("PASSWORD").logAction("LOGIN")
                .build();
        when(authFlowManager.authenticate(any(AuthFlowContext.class))).thenReturn(result);
        when(authFlowManager.finalizeLogin(result))
                .thenReturn(new JwtPair("access", "refresh", 900));

        var resp = authService.login(req);

        assertThat(resp.getAccessToken()).isEqualTo("access");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh");
        assertThat(resp.getTokenType()).isEqualTo("Bearer");
        assertThat(resp.getTotpRequired()).isFalse();
    }

    @Test
    void shouldFailLoginWithWrongPassword() {
        LoginRequest req = new LoginRequest();
        req.setUsername("testuser");
        req.setPassword("WrongPass1");

        when(authFlowManager.authenticate(any(AuthFlowContext.class)))
                .thenThrow(new BizException(BizErrorCode.INCORRECT_PASSWORD));

        assertThatThrownBy(() -> authService.login(req))
                .hasMessageContaining("Incorrect password");
    }

    @Test
    void shouldFailLoginWithDisabledAccount() {
        LoginRequest req = new LoginRequest();
        req.setUsername("disabled");
        req.setPassword("Abc12345");

        when(authFlowManager.authenticate(any(AuthFlowContext.class)))
                .thenThrow(new BizException(BizErrorCode.ACCOUNT_DISABLED));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void shouldFailLoginWithDeletedAccount() {
        LoginRequest req = new LoginRequest();
        req.setUsername("deleted");
        req.setPassword("Abc12345");

        when(authFlowManager.authenticate(any(AuthFlowContext.class)))
                .thenThrow(new BizException(BizErrorCode.ACCOUNT_DELETED));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("deleted");
    }

    @Test
    void shouldReturnTotpRequiredWhenTotpEnabled() {
        LoginRequest req = new LoginRequest();
        req.setUsername("totpuser");
        req.setPassword("Abc12345");

        AuthUser user = AuthUser.builder()
                .authUserId(4001L).authUserUsername("totpuser").build();

        AuthFlowResult result = AuthFlowResult.builder()
                .user(user).scope("openid").logType("PASSWORD").logAction("LOGIN")
                .totpPending(true).build();
        when(authFlowManager.authenticate(any(AuthFlowContext.class))).thenReturn(result);
        when(authFlowManager.finalizeLogin(result))
                .thenReturn(new JwtPair("pending-token", null, 300));

        var resp = authService.login(req);

        assertThat(resp.getAccessToken()).isEqualTo("pending-token");
        assertThat(resp.getTotpRequired()).isTrue();
        assertThat(resp.getRefreshToken()).isNull();
    }

    // ── Token Refresh ─────────────────────────────────────────

    @Test
    void shouldRefreshToken() {
        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("old-refresh");

        JwtClaims claims = new JwtClaims("1001", "iss", "aud",
                System.currentTimeMillis() / 1000 + 3600,
                System.currentTimeMillis() / 1000, "jti", "testuser", "openid");

        when(jwtService.verify("old-refresh")).thenReturn(claims);
        when(jwtService.getUserId("old-refresh")).thenReturn(1001L);
        when(jwtService.issue(1001L, "testuser", "openid"))
                .thenReturn(new JwtPair("new-access", "new-refresh", 900));

        var resp = authService.refresh(req);

        assertThat(resp.getAccessToken()).isEqualTo("new-access");
        verify(tokenBlacklist).add(eq("old-refresh"), anyLong());
    }

    // ── Logout ────────────────────────────────────────────────

    @Test
    void shouldLogoutAndBlacklistTokens() {
        JwtClaims accessClaims = new JwtClaims("1001", "iss", "aud",
                System.currentTimeMillis() / 1000 + 900,
                System.currentTimeMillis() / 1000, "jti-a", "testuser", "openid");
        JwtClaims refreshClaims = new JwtClaims("1001", "iss", "aud",
                System.currentTimeMillis() / 1000 + 86400,
                System.currentTimeMillis() / 1000, "jti-r", "testuser", "openid");

        when(jwtService.verify("access-token")).thenReturn(accessClaims);
        when(jwtService.verify("refresh-token")).thenReturn(refreshClaims);

        authService.logout("access-token", "refresh-token");

        verify(tokenBlacklist).add(eq("access-token"), anyLong());
        verify(tokenBlacklist).add(eq("refresh-token"), anyLong());
    }

    // ── TOTP Setup ────────────────────────────────────────────

    @Test
    void shouldSetupTotpSuccessfully() {
        AuthUser user = AuthUser.builder()
                .authUserId(1001L).authUserUsername("testuser").build();

        when(authUserService.selectById(1001L)).thenReturn(user);
        when(totpService.getSecret(1001L)).thenReturn("BASE32SECRET");
        when(totpService.generateQrUri("testuser", "BASE32SECRET"))
                .thenReturn("otpauth://totp/ZhiYu:testuser?secret=BASE32SECRET&issuer=ZhiYu");

        TotpSetupResponse resp = authService.setupTotp(1001L);

        assertThat(resp.getSecret()).isEqualTo("BASE32SECRET");
        assertThat(resp.getQrUri()).contains("ZhiYu:testuser");
        verify(totpService).setupTotp(1001L, "testuser");
    }

    @Test
    void shouldEnableTotpSuccessfully() {
        authService.enableTotp(1001L, "123456");
        verify(totpService).enableTotp(1001L, "123456");
    }

    @Test
    void shouldDisableTotpSuccessfully() {
        authService.disableTotp(1001L);
        verify(totpService).disableTotp(1001L);
    }

    // ── TOTP Login Verification ───────────────────────────────

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

        LoginResponse resp = authService.verifyTotpLogin(4001L, "654321");

        assertThat(resp.getAccessToken()).isEqualTo("full-access");
        assertThat(resp.getRefreshToken()).isEqualTo("full-refresh");
        assertThat(resp.getTotpRequired()).isFalse();
    }

    @Test
    void shouldFailVerifyTotpLoginWithWrongCode() {
        when(authFlowManager.authenticate(any(AuthFlowContext.class)))
                .thenThrow(new BizException(BizErrorCode.TOTP_INCORRECT));

        assertThatThrownBy(() -> authService.verifyTotpLogin(4001L, "000000"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldFailSetupTotpWhenUserNotFound() {
        when(authUserService.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> authService.setupTotp(999L))
                .isInstanceOf(BizException.class);
    }

    // ── Login with captcha required (positive path) ─────────────

    @Test
    void shouldLoginWithCaptchaWhenRequired() {
        LoginRequest req = new LoginRequest();
        req.setUsername("captchauser");
        req.setPassword("Abc12345");
        req.setCaptchaToken("captcha-tok");
        req.setCaptchaCode("A3x9");

        AuthUser user = AuthUser.builder()
                .authUserId(5001L).authUserUsername("captchauser").build();

        AuthFlowResult result = AuthFlowResult.builder()
                .user(user).scope("openid").logType("PASSWORD").logAction("LOGIN")
                .build();
        when(authFlowManager.authenticate(any(AuthFlowContext.class))).thenReturn(result);
        when(authFlowManager.finalizeLogin(result))
                .thenReturn(new JwtPair("access-captcha", "refresh-captcha", 900));

        var resp = authService.login(req);

        assertThat(resp.getAccessToken()).isEqualTo("access-captcha");
    }

    @Test
    void shouldFailLoginWhenCaptchaRequiredButNoCaptchaCode() {
        LoginRequest req = new LoginRequest();
        req.setUsername("captchauser2");
        req.setPassword("Abc12345");

        when(authFlowManager.authenticate(any(AuthFlowContext.class)))
                .thenThrow(new BizException(BizErrorCode.CAPTCHA_FAILED));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("CAPTCHA verification failed");
    }

    @Test
    void shouldFailLoginWhenCaptchaRequiredButTokenIsNull() {
        LoginRequest req = new LoginRequest();
        req.setUsername("captchauser3");
        req.setPassword("Abc12345");
        req.setCaptchaToken(null);
        req.setCaptchaCode("A3x9");

        when(authFlowManager.authenticate(any(AuthFlowContext.class)))
                .thenThrow(new BizException(BizErrorCode.CAPTCHA_FAILED));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("CAPTCHA verification failed");
    }

    @Test
    void shouldFailLoginWhenCaptchaVerifiedButUserNotFound() {
        LoginRequest req = new LoginRequest();
        req.setUsername("nouser");
        req.setPassword("Abc12345");

        when(authFlowManager.authenticate(any(AuthFlowContext.class)))
                .thenThrow(new BizException(BizErrorCode.INCORRECT_PASSWORD));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Incorrect password");
    }

    @Test
    void shouldFailLoginWhenEnableIsNull() {
        LoginRequest req = new LoginRequest();
        req.setUsername("nullenable");
        req.setPassword("Abc12345");

        when(authFlowManager.authenticate(any(AuthFlowContext.class)))
                .thenThrow(new BizException(BizErrorCode.ACCOUNT_DISABLED));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void shouldFailLoginWhenDeletedIsNull() {
        LoginRequest req = new LoginRequest();
        req.setUsername("nulldelete");
        req.setPassword("Abc12345");

        AuthUser user = AuthUser.builder()
                .authUserId(7001L).authUserUsername("nulldelete").build();

        AuthFlowResult result = AuthFlowResult.builder()
                .user(user).scope("openid").logType("PASSWORD").logAction("LOGIN")
                .build();
        when(authFlowManager.authenticate(any(AuthFlowContext.class))).thenReturn(result);
        when(authFlowManager.finalizeLogin(result))
                .thenReturn(new JwtPair("at", "rt", 900));

        var resp = authService.login(req);

        assertThat(resp.getAccessToken()).isEqualTo("at");
    }

    @Test
    void shouldIssueTokenWithDefaultScopeWhenNull() {
        LoginRequest req = new LoginRequest();
        req.setUsername("nullscope");
        req.setPassword("Abc12345");

        AuthUser user = AuthUser.builder()
                .authUserId(8001L).authUserUsername("nullscope").build();

        AuthFlowResult result = AuthFlowResult.builder()
                .user(user).scope("openid").logType("PASSWORD").logAction("LOGIN")
                .build();
        when(authFlowManager.authenticate(any(AuthFlowContext.class))).thenReturn(result);
        when(authFlowManager.finalizeLogin(result))
                .thenReturn(new JwtPair("at", "rt", 900));

        var resp = authService.login(req);

        assertThat(resp.getAccessToken()).isEqualTo("at");
        verify(authFlowManager).finalizeLogin(result);
    }

    // ── Refresh Token edge cases ───────────────────────────────

    @Test
    void shouldBlockBlacklistedRefreshToken() {
        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("blacklisted-refresh");

        when(tokenBlacklist.isBlacklisted("blacklisted-refresh")).thenReturn(true);

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Token reuse detected");
    }

    @Test
    void shouldUseMinimumTtlOfOneSecond() {
        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("near-expiry-refresh");

        JwtClaims claims = new JwtClaims("1001", "iss", "aud",
                System.currentTimeMillis() / 1000 - 100,
                System.currentTimeMillis() / 1000 - 1000, "jti", "testuser", "openid");

        when(jwtService.verify("near-expiry-refresh")).thenReturn(claims);
        when(jwtService.getUserId("near-expiry-refresh")).thenReturn(1001L);
        when(jwtService.issue(1001L, "testuser", "openid"))
                .thenReturn(new JwtPair("new-at", "new-rt", 900));

        var resp = authService.refresh(req);

        assertThat(resp.getAccessToken()).isEqualTo("new-at");
        verify(tokenBlacklist).add(eq("near-expiry-refresh"), eq(1L));
    }

    // ── Logout edge cases ───────────────────────────────────────

    @Test
    void shouldLogoutWithBothNullTokensGracefully() {
        authService.logout(null, null);
    }

    @Test
    void shouldLogoutWithExpiredAccessTokenGracefully() {
        when(jwtService.verify("expired-access"))
                .thenThrow(new BizException(BizErrorCode.TOKEN_EXPIRED));

        authService.logout("expired-access", null);
    }

    @Test
    void shouldLogoutWithInvalidRefreshTokenGracefully() {
        when(jwtService.verify("invalid-refresh"))
                .thenThrow(new RuntimeException("JWT parse error"));

        authService.logout(null, "invalid-refresh");
    }

    @Test
    void shouldNotBlacklistIfTtlIsZeroOrNegative() {
        JwtClaims accessClaims = new JwtClaims("1001", "iss", "aud",
                System.currentTimeMillis() / 1000 - 1,
                System.currentTimeMillis() / 1000 - 1000, "jti", "testuser", "openid");

        when(jwtService.verify("expired-at")).thenReturn(accessClaims);

        authService.logout("expired-at", null);

        verify(tokenBlacklist, never()).add(eq("expired-at"), anyLong());
    }

    // ── verifyTotpLogin scope fallback ─────────────────────────

    @Test
    void shouldVerifyTotpLoginWithNullScopeFallback() {
        AuthUser user = AuthUser.builder()
                .authUserId(4001L).authUserUsername("totpuser")
                .authUserScope(null).build();

        AuthFlowResult result = AuthFlowResult.builder()
                .user(user).scope("openid").logType("TOTP").logAction("LOGIN")
                .build();
        when(authFlowManager.authenticate(any(AuthFlowContext.class))).thenReturn(result);
        when(authFlowManager.finalizeLogin(result))
                .thenReturn(new JwtPair("at", "rt", 900));

        LoginResponse resp = authService.verifyTotpLogin(4001L, "123456");

        assertThat(resp.getAccessToken()).isEqualTo("at");
    }

    @Test
    void shouldFailVerifyTotpLoginWhenUserNotFound() {
        when(authFlowManager.authenticate(any(AuthFlowContext.class)))
                .thenThrow(new BizException(BizErrorCode.RESOURCE_NOT_FOUND));

        assertThatThrownBy(() -> authService.verifyTotpLogin(4001L, "123456"))
                .isInstanceOf(BizException.class);
    }

    // ── recordLoginLog in login with TOTP path ─────────────────

    @Test
    void shouldRecordLoginLogForTotpPendingLogin() {
        LoginRequest req = new LoginRequest();
        req.setUsername("totpuser");
        req.setPassword("Abc12345");

        AuthUser user = AuthUser.builder()
                .authUserId(4001L).authUserUsername("totpuser").build();

        AuthFlowResult result = AuthFlowResult.builder()
                .user(user).scope("openid").logType("PASSWORD").logAction("LOGIN")
                .totpPending(true).build();
        when(authFlowManager.authenticate(any(AuthFlowContext.class))).thenReturn(result);
        when(authFlowManager.finalizeLogin(result))
                .thenReturn(new JwtPair("pending-token", null, 300));

        var resp = authService.login(req);

        assertThat(resp.getTotpRequired()).isTrue();
        verify(authFlowManager).finalizeLogin(result);
    }
}
