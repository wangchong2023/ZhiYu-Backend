package com.zhiyu.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScopeFilterTest {

    @Mock
    private FilterChain chain;

    @InjectMocks
    private ScopeFilter filter;

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

    // ── No Authentication ─────────────────────────────────────

    @Test
    void shouldPassWhenNoAuthentication() throws ServletException, IOException {
        request.setRequestURI("/api/v1/admin/users");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ── Authentication with null authorities ──────────────────

    @Test
    void shouldPassWhenAuthoritiesNull() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken("1001", null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setRequestURI("/api/v1/admin/users");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ── Full Scope (not limited) ──────────────────────────────

    @Test
    void shouldPassForFullScopeUser() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken("1001", null,
                List.of(new SimpleGrantedAuthority("ROLE_FULL")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setRequestURI("/api/v1/admin/users");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPassForAdminScopeUser() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken("1001", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setRequestURI("/api/v1/admin/users");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPassForOpenIdScopeUser() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken("1001", null,
                List.of(new SimpleGrantedAuthority("ROLE_openid")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setRequestURI("/api/v1/auth/devices");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ── Limited Scope on Allowed Paths ────────────────────────

    @Test
    void shouldPassLimitedUserOnProfilePath() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken("1001", null,
                List.of(new SimpleGrantedAuthority("ROLE_limited")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setRequestURI("/api/v1/user/profile");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPassLimitedUserOnProfileSubPath() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken("1001", null,
                List.of(new SimpleGrantedAuthority("ROLE_limited")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setRequestURI("/api/v1/user/profile/settings");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPassLimitedUserOnDevicesPath() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken("1001", null,
                List.of(new SimpleGrantedAuthority("ROLE_limited")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setRequestURI("/api/v1/auth/devices");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPassLimitedUserOnDevicesSubPath() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken("1001", null,
                List.of(new SimpleGrantedAuthority("ROLE_limited")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setRequestURI("/api/v1/auth/devices/123");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ── Limited Scope on Restricted Paths ─────────────────────

    @Test
    void shouldRejectLimitedUserOnAdminPath() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken("1001", null,
                List.of(new SimpleGrantedAuthority("ROLE_limited")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setRequestURI("/api/v1/admin/users");

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("40301");
        assertThat(response.getContentAsString()).contains("Account restricted, only email-related functions are allowed");
    }

    @Test
    void shouldRejectLimitedUserOnOtherAuthPath() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken("1001", null,
                List.of(new SimpleGrantedAuthority("ROLE_limited")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setRequestURI("/api/v1/auth/password/reset");

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void shouldRejectLimitedUserOnRandomPath() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken("1001", null,
                List.of(new SimpleGrantedAuthority("ROLE_limited")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setRequestURI("/api/v1/subscription/plans");

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    // ── Case-insensitive scope matching ───────────────────────

    @Test
    void shouldDetectLimitedScopeCaseInsensitively() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken("1001", null,
                List.of(new SimpleGrantedAuthority("ROLE_LIMITED")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setRequestURI("/api/v1/admin/users");

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void shouldDetectMixedCaseLimitedScope() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken("1001", null,
                List.of(new SimpleGrantedAuthority("RoLe_LiMiTeD")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setRequestURI("/api/v1/admin/users");

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    // ── Multiple authorities (one is limited) ─────────────────

    @Test
    void shouldDetectLimitedWhenMultipleAuthorities() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken("1001", null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_limited"),
                        new SimpleGrantedAuthority("ROLE_READER")
                ));
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setRequestURI("/api/v1/admin/users");

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    // ── Content type on rejection ─────────────────────────────

    @Test
    void shouldSetJsonContentTypeOnRejection() throws ServletException, IOException {
        var auth = new UsernamePasswordAuthenticationToken("1001", null,
                List.of(new SimpleGrantedAuthority("ROLE_limited")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setRequestURI("/api/v1/admin/users");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getCharacterEncoding()).contains("UTF-8");
    }
}
