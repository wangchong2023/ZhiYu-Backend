package com.zhiyu.auth.service;

import com.zhiyu.auth.dto.*;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.UserTotp;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.jwt.JwtClaims;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.auth.password.PasswordService;
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

    @Mock private AuthUserMapper authUserMapper;
    @Mock private AuthUserLogMapper authUserLogMapper;
    @Mock private PasswordService passwordService;
    @Mock private JwtService jwtService;
    @Mock private TokenBlacklist tokenBlacklist;
    @Mock private CaptchaService captchaService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private TotpService totpService;
    @Mock private com.zhiyu.auth.validator.AuthValidator authValidator;
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

        when(authUserMapper.selectOne(any())).thenReturn(null);
        when(passwordService.hash("Abc12345")).thenReturn("$2a$12$hashed");

        var resp = authService.register(req);

        assertThat(resp.getUsername()).isEqualTo("testuser");
        verify(authUserMapper).insert(any(AuthUser.class));
    }

    @Test
    void shouldFailRegisterWithDuplicateUsername() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("existing");
        req.setPassword("Abc12345");
        req.setEmail("new@example.com");
        req.setCaptchaToken("tok");
        req.setCaptchaCode("A3x9");

        when(authUserMapper.selectOne(any()))
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

        when(authUserMapper.selectOne(any()))
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
                .authUserId(1001L).authUserUsername("testuser")
                .authUserPassword("$2a$12$hashed")
                .authUserEnable(1).authUserDeleted(0).build();

        when(authUserMapper.selectOne(any())).thenReturn(user);
        when(passwordService.verify("Abc12345", "$2a$12$hashed")).thenReturn(true);
        when(totpService.isTotpEnabled(1001L)).thenReturn(false);
        when(jwtService.issue(1001L, "testuser", "openid"))
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

        AuthUser user = AuthUser.builder()
                .authUserId(1001L).authUserUsername("testuser")
                .authUserPassword("$2a$12$hashed")
                .authUserEnable(1).authUserDeleted(0).build();

        when(authUserMapper.selectOne(any())).thenReturn(user);
        when(passwordService.verify("WrongPass1", "$2a$12$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .hasMessageContaining("Incorrect password");
    }

    @Test
    void shouldFailLoginWithDisabledAccount() {
        LoginRequest req = new LoginRequest();
        req.setUsername("disabled");
        req.setPassword("Abc12345");

        AuthUser user = AuthUser.builder()
                .authUserId(2001L).authUserUsername("disabled")
                .authUserPassword("$2a$12$hashed")
                .authUserEnable(0).authUserDeleted(0).build();

        when(authUserMapper.selectOne(any())).thenReturn(user);
        when(passwordService.verify("Abc12345", "$2a$12$hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void shouldFailLoginWithDeletedAccount() {
        LoginRequest req = new LoginRequest();
        req.setUsername("deleted");
        req.setPassword("Abc12345");

        AuthUser user = AuthUser.builder()
                .authUserId(3001L).authUserUsername("deleted")
                .authUserPassword("$2a$12$hashed")
                .authUserEnable(1).authUserDeleted(1).build();

        when(authUserMapper.selectOne(any())).thenReturn(user);
        when(passwordService.verify("Abc12345", "$2a$12$hashed")).thenReturn(true);

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
                .authUserId(4001L).authUserUsername("totpuser")
                .authUserPassword("$2a$12$hashed")
                .authUserEnable(1).authUserDeleted(0).build();

        when(authUserMapper.selectOne(any())).thenReturn(user);
        when(passwordService.verify("Abc12345", "$2a$12$hashed")).thenReturn(true);
        when(totpService.isTotpEnabled(4001L)).thenReturn(true);
        when(jwtService.issuePendingToken(4001L, "totpuser")).thenReturn("pending-token");

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

        when(authUserMapper.selectById(1001L)).thenReturn(user);
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

        when(totpService.verifyTotp(4001L, "654321")).thenReturn(true);
        when(authUserMapper.selectById(4001L)).thenReturn(user);
        when(jwtService.issue(4001L, "totpuser", "openid"))
                .thenReturn(new JwtPair("full-access", "full-refresh", 900));

        LoginResponse resp = authService.verifyTotpLogin(4001L, "654321");

        assertThat(resp.getAccessToken()).isEqualTo("full-access");
        assertThat(resp.getRefreshToken()).isEqualTo("full-refresh");
        assertThat(resp.getTotpRequired()).isFalse();
    }

    @Test
    void shouldFailVerifyTotpLoginWithWrongCode() {
        when(totpService.verifyTotp(4001L, "000000")).thenReturn(false);

        assertThatThrownBy(() -> authService.verifyTotpLogin(4001L, "000000"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldFailSetupTotpWhenUserNotFound() {
        when(authUserMapper.selectById(999L)).thenReturn(null);

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
                .authUserId(5001L).authUserUsername("captchauser")
                .authUserPassword("$2a$12$hashed")
                .authUserEnable(1).authUserDeleted(0).build();

        // checkCaptchaRequired throws BizException → captchaRequired = true
        doThrow(new BizException(BizErrorCode.CAPTCHA_FAILED))
                .when(loginAttemptService).checkCaptchaRequired("captchauser");

        when(authUserMapper.selectOne(any())).thenReturn(user);
        when(passwordService.verify("Abc12345", "$2a$12$hashed")).thenReturn(true);
        when(totpService.isTotpEnabled(5001L)).thenReturn(false);
        when(jwtService.issue(5001L, "captchauser", "openid"))
                .thenReturn(new JwtPair("access-captcha", "refresh-captcha", 900));

        var resp = authService.login(req);

        assertThat(resp.getAccessToken()).isEqualTo("access-captcha");
        verify(captchaService).verify("captcha-tok", "A3x9");
    }

    @Test
    void shouldFailLoginWhenCaptchaRequiredButNoCaptchaCode() {
        LoginRequest req = new LoginRequest();
        req.setUsername("captchauser2");
        req.setPassword("Abc12345");
        // No captchaToken or captchaCode set

        doThrow(new BizException(BizErrorCode.CAPTCHA_FAILED))
                .when(loginAttemptService).checkCaptchaRequired("captchauser2");

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

        doThrow(new BizException(BizErrorCode.CAPTCHA_FAILED))
                .when(loginAttemptService).checkCaptchaRequired("captchauser3");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("CAPTCHA verification failed");
    }

    @Test
    void shouldFailLoginWhenCaptchaVerifiedButUserNotFound() {
        LoginRequest req = new LoginRequest();
        req.setUsername("nouser");
        req.setPassword("Abc12345");

        when(authUserMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Incorrect password");
    }

    @Test
    void shouldFailLoginWhenEnableIsNull() {
        LoginRequest req = new LoginRequest();
        req.setUsername("nullenable");
        req.setPassword("Abc12345");

        AuthUser user = AuthUser.builder()
                .authUserId(6001L).authUserUsername("nullenable")
                .authUserPassword("$2a$12$hashed")
                .authUserEnable(null).authUserDeleted(0).build();

        when(authUserMapper.selectOne(any())).thenReturn(user);
        when(passwordService.verify("Abc12345", "$2a$12$hashed")).thenReturn(true);

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
                .authUserId(7001L).authUserUsername("nulldelete")
                .authUserPassword("$2a$12$hashed")
                .authUserEnable(1).authUserDeleted(null).build();

        when(authUserMapper.selectOne(any())).thenReturn(user);
        when(passwordService.verify("Abc12345", "$2a$12$hashed")).thenReturn(true);
        when(totpService.isTotpEnabled(7001L)).thenReturn(false);
        when(jwtService.issue(7001L, "nulldelete", "openid"))
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
                .authUserId(8001L).authUserUsername("nullscope")
                .authUserPassword("$2a$12$hashed")
                .authUserEnable(1).authUserDeleted(0)
                .authUserScope(null).build();

        when(authUserMapper.selectOne(any())).thenReturn(user);
        when(passwordService.verify("Abc12345", "$2a$12$hashed")).thenReturn(true);
        when(totpService.isTotpEnabled(8001L)).thenReturn(false);
        when(jwtService.issue(8001L, "nullscope", "openid"))
                .thenReturn(new JwtPair("at", "rt", 900));

        var resp = authService.login(req);

        assertThat(resp.getAccessToken()).isEqualTo("at");
        verify(jwtService).issue(8001L, "nullscope", "openid");
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

        // Token already expired (exp is in the past)
        JwtClaims claims = new JwtClaims("1001", "iss", "aud",
                System.currentTimeMillis() / 1000 - 100, // expired
                System.currentTimeMillis() / 1000 - 1000, "jti", "testuser", "openid");

        when(jwtService.verify("near-expiry-refresh")).thenReturn(claims);
        when(jwtService.getUserId("near-expiry-refresh")).thenReturn(1001L);
        when(jwtService.issue(1001L, "testuser", "openid"))
                .thenReturn(new JwtPair("new-at", "new-rt", 900));

        var resp = authService.refresh(req);

        assertThat(resp.getAccessToken()).isEqualTo("new-at");
        // TTL should be at least 1 second
        verify(tokenBlacklist).add(eq("near-expiry-refresh"), eq(1L));
    }

    // ── Logout edge cases ───────────────────────────────────────

    @Test
    void shouldLogoutWithBothNullTokensGracefully() {
        // Should not throw
        authService.logout(null, null);
    }

    @Test
    void shouldLogoutWithExpiredAccessTokenGracefully() {
        when(jwtService.verify("expired-access"))
                .thenThrow(new BizException(BizErrorCode.TOKEN_EXPIRED));

        // Should not throw - exception is caught
        authService.logout("expired-access", null);
    }

    @Test
    void shouldLogoutWithInvalidRefreshTokenGracefully() {
        when(jwtService.verify("invalid-refresh"))
                .thenThrow(new RuntimeException("JWT parse error"));

        // Should not throw - exception is caught
        authService.logout(null, "invalid-refresh");
    }

    @Test
    void shouldNotBlacklistIfTtlIsZeroOrNegative() {
        JwtClaims accessClaims = new JwtClaims("1001", "iss", "aud",
                System.currentTimeMillis() / 1000 - 1, // already expired
                System.currentTimeMillis() / 1000 - 1000, "jti", "testuser", "openid");

        when(jwtService.verify("expired-at")).thenReturn(accessClaims);

        authService.logout("expired-at", null);

        // Should NOT blacklist since ttl <= 0
        verify(tokenBlacklist, never()).add(eq("expired-at"), anyLong());
    }

    // ── verifyTotpLogin scope fallback ─────────────────────────

    @Test
    void shouldVerifyTotpLoginWithNullScopeFallback() {
        AuthUser user = AuthUser.builder()
                .authUserId(4001L).authUserUsername("totpuser")
                .authUserScope(null).build();

        when(totpService.verifyTotp(4001L, "123456")).thenReturn(true);
        when(authUserMapper.selectById(4001L)).thenReturn(user);
        when(jwtService.issue(4001L, "totpuser", "openid"))
                .thenReturn(new JwtPair("at", "rt", 900));

        LoginResponse resp = authService.verifyTotpLogin(4001L, "123456");

        assertThat(resp.getAccessToken()).isEqualTo("at");
        verify(jwtService).issue(4001L, "totpuser", "openid");
    }

    @Test
    void shouldFailVerifyTotpLoginWhenUserNotFound() {
        when(totpService.verifyTotp(4001L, "123456")).thenReturn(true);
        when(authUserMapper.selectById(4001L)).thenReturn(null);

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
                .authUserId(4001L).authUserUsername("totpuser")
                .authUserPassword("$2a$12$hashed")
                .authUserEnable(1).authUserDeleted(0).build();

        when(authUserMapper.selectOne(any())).thenReturn(user);
        when(passwordService.verify("Abc12345", "$2a$12$hashed")).thenReturn(true);
        when(totpService.isTotpEnabled(4001L)).thenReturn(true);
        when(jwtService.issuePendingToken(4001L, "totpuser")).thenReturn("pending-token");

        var resp = authService.login(req);

        assertThat(resp.getTotpRequired()).isTrue();
        // Verify login log was recorded with TOTP_PENDING
        verify(authUserLogMapper).insert(any(com.zhiyu.ufp.auth.entity.AuthUserLog.class));
    }
}
