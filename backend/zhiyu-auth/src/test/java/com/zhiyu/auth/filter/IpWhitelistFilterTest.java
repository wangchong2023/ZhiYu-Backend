package com.zhiyu.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IpWhitelistFilterTest {

    @Mock
    private FilterChain chain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    // ── Non-admin paths bypass whitelist ──────────────────────

    @Test
    void shouldPassNonAdminPath() throws ServletException, IOException {
        IpWhitelistFilter filter = new IpWhitelistFilter("127.0.0.1");
        request.setRequestURI("/api/v1/auth/login");
        request.setRemoteAddr("10.0.0.1");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPassUserProfilePath() throws ServletException, IOException {
        IpWhitelistFilter filter = new IpWhitelistFilter("127.0.0.1");
        request.setRequestURI("/api/v1/user/profile");
        request.setRemoteAddr("10.0.0.99");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ── Admin path with whitelisted IP ────────────────────────

    @Test
    void shouldPassAdminPathWithWhitelistedIp() throws ServletException, IOException {
        IpWhitelistFilter filter = new IpWhitelistFilter("127.0.0.1");
        request.setRequestURI("/api/v1/admin/users");
        request.setRemoteAddr("127.0.0.1");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldPassAdminPathWithMultipleWhitelistedIps() throws ServletException, IOException {
        IpWhitelistFilter filter = new IpWhitelistFilter("127.0.0.1, 192.168.1.1, 10.0.0.1");
        request.setRequestURI("/api/v1/admin/settings");
        request.setRemoteAddr("192.168.1.1");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ── Admin path with non-whitelisted IP ────────────────────

    @Test
    void shouldRejectAdminPathWithNonWhitelistedIp() throws ServletException, IOException {
        IpWhitelistFilter filter = new IpWhitelistFilter("127.0.0.1");
        request.setRequestURI("/api/v1/admin/users");
        request.setRemoteAddr("10.0.0.99");

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("40301");
        assertThat(response.getContentAsString()).contains("IP not in whitelist");
    }

    @Test
    void shouldRejectAdminSubPathWithNonWhitelistedIp() throws ServletException, IOException {
        IpWhitelistFilter filter = new IpWhitelistFilter("127.0.0.1");
        request.setRequestURI("/api/v1/admin/users/123/permissions");
        request.setRemoteAddr("10.0.0.99");

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    // ── Default whitelist value ───────────────────────────────

    @Test
    void shouldConstructWithDefaultWhitelist() throws ServletException, IOException {
        // Default: "127.0.0.1" - using constructor with the default
        IpWhitelistFilter filter = new IpWhitelistFilter("127.0.0.1");
        request.setRequestURI("/api/v1/admin/roles");
        request.setRemoteAddr("127.0.0.1");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ── CSV parsing with whitespace ───────────────────────────

    @Test
    void shouldParseCsvWithSpacesCorrectly() throws ServletException, IOException {
        IpWhitelistFilter filter = new IpWhitelistFilter(" 10.0.0.1 , 10.0.0.2 ,  10.0.0.3 ");
        request.setRequestURI("/api/v1/admin/audit");
        request.setRemoteAddr("10.0.0.2");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldRejectWhenCsvContainsSpacesButIpDoesNotMatch() throws ServletException, IOException {
        IpWhitelistFilter filter = new IpWhitelistFilter(" 10.0.0.1 , 10.0.0.2 ");
        request.setRequestURI("/api/v1/admin/audit");
        request.setRemoteAddr("10.0.0.3");

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    // ── Empty whitelist CSV ───────────────────────────────────

    @Test
    void shouldRejectAllWhenWhitelistIsEmpty() throws ServletException, IOException {
        IpWhitelistFilter filter = new IpWhitelistFilter("");
        request.setRequestURI("/api/v1/admin/users");
        request.setRemoteAddr("127.0.0.1");

        filter.doFilterInternal(request, response, chain);

        // Empty string split produces [""], not empty set
        // "127.0.0.1" is not in the set → rejected
        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
    }

    // ── Admin path exactly matches prefix ─────────────────────

    @Test
    void shouldMatchAdminPathExactly() throws ServletException, IOException {
        IpWhitelistFilter filter = new IpWhitelistFilter("127.0.0.1");
        request.setRequestURI("/api/v1/admin/");
        request.setRemoteAddr("127.0.0.1");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
