package com.zhiyu.auth.service;

import com.zhiyu.auth.dto.LoginRequest;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.dto.RefreshRequest;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.enums.AuthGrantType;
import com.zhiyu.ufp.auth.jwt.JwtClaims;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.jwt.JwtService.JwtPair;
import com.zhiyu.ufp.auth.spi.AuthFlowContext;
import com.zhiyu.ufp.auth.spi.AuthFlowManager;
import com.zhiyu.ufp.auth.spi.AuthFlowResult;
import com.zhiyu.ufp.auth.token.TokenBlacklist;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock private JwtService jwtService;
    @Mock private TokenBlacklist tokenBlacklist;
    @Mock private AuthFlowManager authFlowManager;

    @InjectMocks private LoginService loginService;

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

        LoginResponse resp = loginService.login(req);

        assertThat(resp.getAccessToken()).isEqualTo("access");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh");
        assertThat(resp.getTotpRequired()).isFalse();
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

        LoginResponse resp = loginService.login(req);

        assertThat(resp.getAccessToken()).isEqualTo("pending-token");
        assertThat(resp.getTotpRequired()).isTrue();
        assertThat(resp.getRefreshToken()).isNull();
    }

    @Test
    void shouldBlockBlacklistedRefreshToken() {
        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("blacklisted-refresh");

        when(tokenBlacklist.isBlacklisted("blacklisted-refresh")).thenReturn(true);

        assertThatThrownBy(() -> loginService.refresh(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Token reuse detected");
    }

    @Test
    void shouldRefreshTokenSuccessfully() {
        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("old-refresh");

        JwtClaims claims = new JwtClaims("1001", "iss", "aud",
                System.currentTimeMillis() / 1000 + 3600,
                System.currentTimeMillis() / 1000, "jti", "testuser", "openid");
        when(jwtService.verify("old-refresh")).thenReturn(claims);
        when(jwtService.getUserId("old-refresh")).thenReturn(1001L);
        when(jwtService.issue(1001L, "testuser", "openid"))
                .thenReturn(new JwtPair("new-access", "new-refresh", 900));

        LoginResponse resp = loginService.refresh(req);

        assertThat(resp.getAccessToken()).isEqualTo("new-access");
        verify(tokenBlacklist).add(eq("old-refresh"), anyLong());
    }

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

        loginService.logout("access-token", "refresh-token");

        verify(tokenBlacklist).add(eq("access-token"), anyLong());
        verify(tokenBlacklist).add(eq("refresh-token"), anyLong());
    }

    @Test
    void shouldLogoutWithBothNullTokensGracefully() {
        loginService.logout(null, null);
    }

    @Test
    void shouldNotBlacklistIfTtlIsZeroOrNegative() {
        JwtClaims accessClaims = new JwtClaims("1001", "iss", "aud",
                System.currentTimeMillis() / 1000 - 1,
                System.currentTimeMillis() / 1000 - 1000, "jti", "testuser", "openid");
        when(jwtService.verify("expired-at")).thenReturn(accessClaims);

        loginService.logout("expired-at", null);

        verify(tokenBlacklist, never()).add(eq("expired-at"), anyLong());
    }
}