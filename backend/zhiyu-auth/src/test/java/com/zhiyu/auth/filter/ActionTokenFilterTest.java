package com.zhiyu.auth.filter;

import com.zhiyu.ufp.auth.jwt.JwtClaims;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionTokenFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private ActionTokenService actionTokenService;

    @Mock
    private FilterChain chain;

    @InjectMocks
    private ActionTokenFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    // ── Non-sensitive operations pass through ─────────────────

    @Test
    void shouldPassGetRequest() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/v1/user/account");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(jwtService, never()).verify(anyString());
    }

    @Test
    void shouldPassPostRequest() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/v1/user/account");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPassDeleteOnNonSensitivePath() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/user/profile");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPassPutOnNonSensitivePath() throws ServletException, IOException {
        request.setMethod("PUT");
        request.setRequestURI("/api/v1/user/settings");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ── Exempt paths ──────────────────────────────────────────

    @Test
    void shouldPassExemptCaptchaPath() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/auth/captcha");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPassExemptLoginPath() throws ServletException, IOException {
        request.setMethod("PUT");
        request.setRequestURI("/api/v1/auth/login");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPassExemptRegisterPath() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/auth/register");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPassExemptRefreshPath() throws ServletException, IOException {
        request.setMethod("PUT");
        request.setRequestURI("/api/v1/auth/refresh");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPassExemptOAuthPath() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/auth/oauth/github");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPassExemptAdminPath() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/admin/users/123");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ── Sensitive operations with missing token ───────────────

    @Test
    void shouldRejectDeleteOnAccountPathWithoutToken() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/user/account");

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("缺少操作验证令牌");
    }

    @Test
    void shouldRejectPutOnPasswordPathWithoutToken() throws ServletException, IOException {
        request.setMethod("PUT");
        request.setRequestURI("/api/v1/auth/password/reset");

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void shouldRejectPatchOnTotpPathWithoutToken() throws ServletException, IOException {
        request.setMethod("PATCH");
        request.setRequestURI("/api/v1/auth/totp");

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    // ── Sensitive operations with blank token ─────────────────

    @Test
    void shouldRejectWithBlankToken() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/user/account");
        request.addHeader("X-Action-Token", "   ");

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getContentAsString()).contains("缺少操作验证令牌");
    }

    @Test
    void shouldRejectWithEmptyToken() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/user/account");
        request.addHeader("X-Action-Token", "");

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getContentAsString()).contains("缺少操作验证令牌");
    }

    // ── Invalid token scope ───────────────────────────────────

    @Test
    void shouldRejectTokenWithWrongScope() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/user/account");
        request.addHeader("X-Action-Token", "wrong-scope-token");

        JwtClaims claims = new JwtClaims("1001", "issuer", "audience",
                System.currentTimeMillis() / 1000 + 300,
                System.currentTimeMillis() / 1000, "jti-001", "testuser", "ACCESS");

        when(jwtService.verify("wrong-scope-token")).thenReturn(claims);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getContentAsString()).contains("操作验证令牌类型无效");
    }

    // ── Token already used ────────────────────────────────────

    @Test
    void shouldRejectAlreadyUsedToken() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/user/account");
        request.addHeader("X-Action-Token", "used-token");

        JwtClaims claims = new JwtClaims("1001", "issuer", "audience",
                System.currentTimeMillis() / 1000 + 300,
                System.currentTimeMillis() / 1000, "jti-used", "testuser", "action_token");

        when(jwtService.verify("used-token")).thenReturn(claims);
        when(actionTokenService.isUsed("jti-used")).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getContentAsString()).contains("操作验证令牌已被使用");
    }

    // ── Valid action token ────────────────────────────────────

    @Test
    void shouldPassValidActionToken() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/user/account");
        request.addHeader("X-Action-Token", "valid-action-token");

        long exp = System.currentTimeMillis() / 1000 + 300;
        JwtClaims claims = new JwtClaims("1001", "issuer", "audience",
                exp, System.currentTimeMillis() / 1000, "jti-valid", "testuser", "action_token");

        when(jwtService.verify("valid-action-token")).thenReturn(claims);
        when(actionTokenService.isUsed("jti-valid")).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(actionTokenService).markUsed("jti-valid", exp);
    }

    // ── Expired / invalid token exception ─────────────────────

    @Test
    void shouldRejectExpiredToken() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/user/account");
        request.addHeader("X-Action-Token", "expired-action-token");

        when(jwtService.verify("expired-action-token"))
                .thenThrow(new BizException(BizErrorCode.TOKEN_EXPIRED));

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getContentAsString()).contains("操作验证令牌无效或已过期");
    }

    @Test
    void shouldRejectInvalidToken() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/user/account");
        request.addHeader("X-Action-Token", "invalid-action-token");

        when(jwtService.verify("invalid-action-token"))
                .thenThrow(new BizException(BizErrorCode.INVALID_TOKEN));

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getContentAsString()).contains("操作验证令牌无效或已过期");
    }

    @Test
    void shouldRejectOnGenericException() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/user/account");
        request.addHeader("X-Action-Token", "bad-token");

        when(jwtService.verify("bad-token"))
                .thenThrow(new RuntimeException("parse error"));

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getContentAsString()).contains("操作验证令牌无效或已过期");
    }

    // ── Content type on rejection ─────────────────────────────

    @Test
    void shouldSetJsonContentTypeOnRejection() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/user/account");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getCharacterEncoding()).contains("UTF-8");
        assertThat(response.getStatus()).isEqualTo(403);
    }

    // ── BizErrorCode.ACCESS_DENIED in response ────────────────

    @Test
    void shouldIncludeAccessDeniedCodeInResponse() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/user/account");

        filter.doFilterInternal(request, response, chain);

        int accessDeniedCode = BizErrorCode.ACCESS_DENIED.getCode();
        assertThat(response.getContentAsString()).contains(String.valueOf(accessDeniedCode));
    }

    // ── PUT on account path with token ────────────────────────

    @Test
    void shouldPassValidTokenOnPutAccount() throws ServletException, IOException {
        request.setMethod("PUT");
        request.setRequestURI("/api/v1/user/account");
        request.addHeader("X-Action-Token", "valid-action-token");

        long exp = System.currentTimeMillis() / 1000 + 300;
        JwtClaims claims = new JwtClaims("1001", "issuer", "audience",
                exp, System.currentTimeMillis() / 1000, "jti-put", "testuser", "action_token");

        when(jwtService.verify("valid-action-token")).thenReturn(claims);
        when(actionTokenService.isUsed("jti-put")).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ── DELETE on password path ───────────────────────────────

    @Test
    void shouldRequireTokenForDeleteOnPasswordPath() throws ServletException, IOException {
        request.setMethod("DELETE");
        request.setRequestURI("/api/v1/auth/password");

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
    }
}
