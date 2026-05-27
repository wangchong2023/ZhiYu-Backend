package com.zhiyu.auth.filter;

import com.zhiyu.ufp.auth.jwt.JwtClaims;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.token.TokenBlacklist;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private TokenBlacklist tokenBlacklist;

    @Mock
    private FilterChain chain;

    @InjectMocks
    private JwtAuthFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Permit URLs ──────────────────────────────────────────

    @Test
    void shouldPermitRegisterPath() throws ServletException, IOException {
        request.setRequestURI("/api/v1/auth/register");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(jwtService, never()).verify(anyString());
    }

    @Test
    void shouldPermitLoginPath() throws ServletException, IOException {
        request.setRequestURI("/api/v1/auth/login");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPermitCaptchaPath() throws ServletException, IOException {
        request.setRequestURI("/api/v1/auth/captcha");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPermitRefreshPath() throws ServletException, IOException {
        request.setRequestURI("/api/v1/auth/refresh");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPermitAdminLoginPath() throws ServletException, IOException {
        request.setRequestURI("/api/v1/admin/login");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPermitActuatorHealthPath() throws ServletException, IOException {
        request.setRequestURI("/actuator/health");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPermitSwaggerUIPath() throws ServletException, IOException {
        request.setRequestURI("/swagger-ui/index.html");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPermitApiDocsPath() throws ServletException, IOException {
        request.setRequestURI("/v3/api-docs");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ── Missing / Invalid Authorization Header ────────────────

    @Test
    void shouldPassThroughWhenNoAuthHeader() throws ServletException, IOException {
        request.setRequestURI("/api/v1/user/profile");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(jwtService, never()).verify(anyString());
    }

    @Test
    void shouldPassThroughWhenHeaderNotBearer() throws ServletException, IOException {
        request.setRequestURI("/api/v1/user/profile");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(jwtService, never()).verify(anyString());
    }

    @Test
    void shouldPassThroughWhenBearerTokenEmpty() throws ServletException, IOException {
        request.setRequestURI("/api/v1/user/profile");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer ");

        filter.doFilterInternal(request, response, chain);

        // Token 提取成功（空字符串）→ verify 应被调用
        verify(chain, never()).doFilter(request, response);
    }

    // ── Blacklisted Token ─────────────────────────────────────

    @Test
    void shouldRejectBlacklistedToken() throws ServletException, IOException {
        request.setRequestURI("/api/v1/user/profile");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer revoked-token");

        when(tokenBlacklist.isBlacklisted("revoked-token")).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains(String.valueOf(BizErrorCode.TOKEN_REUSE_DETECTED.getCode()));
        assertThat(response.getContentAsString()).contains("Token reuse detected");
    }

    // ── Valid Token ───────────────────────────────────────────

    @Test
    void shouldAuthenticateValidToken() throws ServletException, IOException {
        request.setRequestURI("/api/v1/user/profile");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");

        JwtClaims claims = new JwtClaims("1001", "issuer", "audience",
                System.currentTimeMillis() / 1000 + 900,
                System.currentTimeMillis() / 1000, "jti-001", "testuser", "FULL");

        when(tokenBlacklist.isBlacklisted("valid-token")).thenReturn(false);
        when(jwtService.verify("valid-token")).thenReturn(claims);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("1001");
    }

    @Test
    void shouldSetCorrectAuthorities() throws ServletException, IOException {
        request.setRequestURI("/api/v1/user/profile");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");

        JwtClaims claims = new JwtClaims("1001", "issuer", "audience",
                System.currentTimeMillis() / 1000 + 900,
                System.currentTimeMillis() / 1000, "jti-001", "testuser", "ADMIN");

        when(tokenBlacklist.isBlacklisted("valid-token")).thenReturn(false);
        when(jwtService.verify("valid-token")).thenReturn(claims);

        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).anyMatch(
                a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    // ── Invalid / Expired Token ───────────────────────────────

    @Test
    void shouldReturnErrorOnExpiredToken() throws ServletException, IOException {
        request.setRequestURI("/api/v1/user/profile");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer expired-token");

        when(tokenBlacklist.isBlacklisted("expired-token")).thenReturn(false);
        when(jwtService.verify("expired-token"))
                .thenThrow(new BizException(BizErrorCode.TOKEN_EXPIRED));

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getContentAsString()).contains(String.valueOf(BizErrorCode.INVALID_TOKEN.getCode()));
    }

    @Test
    void shouldReturnErrorOnInvalidToken() throws ServletException, IOException {
        request.setRequestURI("/api/v1/user/profile");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");

        when(tokenBlacklist.isBlacklisted("invalid-token")).thenReturn(false);
        when(jwtService.verify("invalid-token"))
                .thenThrow(new BizException(BizErrorCode.INVALID_TOKEN));

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getContentAsString()).contains(String.valueOf(BizErrorCode.INVALID_TOKEN.getCode()));
    }

    @Test
    void shouldReturnErrorOnGenericException() throws ServletException, IOException {
        request.setRequestURI("/api/v1/user/profile");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad-token");

        when(tokenBlacklist.isBlacklisted("bad-token")).thenReturn(false);
        when(jwtService.verify("bad-token"))
                .thenThrow(new RuntimeException("JWT parse error"));

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getContentAsString()).contains(String.valueOf(BizErrorCode.INVALID_TOKEN.getCode()));
        assertThat(response.getContentAsString()).contains("JWT parse error");
    }

    // ── Token Blacklist Not Called for Permitted Paths ────────

    @Test
    void shouldNotCheckBlacklistForPermittedPaths() throws ServletException, IOException {
        request.setRequestURI("/api/v1/auth/login");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer some-token");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(tokenBlacklist, never()).isBlacklisted(anyString());
        verify(jwtService, never()).verify(anyString());
    }

    // ── Content-Type and encoding checks ──────────────────────

    @Test
    void shouldSetJsonContentTypeOnError() throws ServletException, IOException {
        request.setRequestURI("/api/v1/user/profile");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer expired-token");

        when(tokenBlacklist.isBlacklisted("expired-token")).thenReturn(false);
        when(jwtService.verify("expired-token"))
                .thenThrow(new BizException(BizErrorCode.TOKEN_EXPIRED));

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getCharacterEncoding()).contains("UTF-8");
    }

    @Test
    void shouldSetJsonContentTypeOnBlacklisted() throws ServletException, IOException {
        request.setRequestURI("/api/v1/user/profile");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer revoked-token");

        when(tokenBlacklist.isBlacklisted("revoked-token")).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getCharacterEncoding()).contains("UTF-8");
    }
}
