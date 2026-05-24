package com.zhiyu.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.auth.filter.ActionTokenFilter;
import com.zhiyu.auth.filter.IpWhitelistFilter;
import com.zhiyu.auth.filter.JwtAuthFilter;
import com.zhiyu.auth.filter.RateLimitFilter;
import com.zhiyu.auth.filter.ScopeFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AuthSecurityConfigTest {

    @Mock
    private RateLimitFilter rateLimitFilter;

    @Mock
    private IpWhitelistFilter ipWhitelistFilter;

    @Mock
    private JwtAuthFilter jwtAuthFilter;

    @Mock
    private ScopeFilter scopeFilter;

    @Mock
    private ActionTokenFilter actionTokenFilter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldInstantiateAuthSecurityConfig() {
        AuthSecurityConfig config = createConfig();
        assertThat(config).isNotNull();
    }

    @Test
    void shouldBeConfigurationClass() {
        AuthSecurityConfig config = createConfig();
        Configuration annotation = config.getClass().getAnnotation(Configuration.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    void shouldBeEnableWebSecurityClass() {
        AuthSecurityConfig config = createConfig();
        EnableWebSecurity enableWs = config.getClass().getAnnotation(EnableWebSecurity.class);
        assertThat(enableWs).isNotNull();
    }

    @Test
    void shouldHaveSecurityFilterChainBeanMethod() throws Exception {
        AuthSecurityConfig config = createConfig();
        var method = config.getClass().getMethod("securityFilterChain", HttpSecurity.class);
        assertThat(method).isNotNull();
        assertThat(method.getAnnotation(org.springframework.context.annotation.Bean.class)).isNotNull();
    }

    @Test
    void shouldCreateConfigWithAllFilters() {
        AuthSecurityConfig config = createConfig();
        assertThat(config).isNotNull();
    }

    private AuthSecurityConfig createConfig() {
        return new AuthSecurityConfig(
                rateLimitFilter, ipWhitelistFilter,
                jwtAuthFilter, scopeFilter,
                actionTokenFilter, objectMapper);
    }
}
