package com.zhiyu.auth.config;

import com.zhiyu.auth.filter.ActionTokenFilter;
import com.zhiyu.auth.filter.IpWhitelistFilter;
import com.zhiyu.auth.filter.JwtAuthFilter;
import com.zhiyu.auth.filter.RateLimitFilter;
import com.zhiyu.auth.filter.ScopeFilter;
import com.zhiyu.common.web.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({OAuthProperties.class, SecurityProperties.class})
@RequiredArgsConstructor
public class AuthSecurityConfig {

    private final RateLimitFilter rateLimitFilter;
    private final IpWhitelistFilter ipWhitelistFilter;
    private final JwtAuthFilter jwtAuthFilter;
    private final ScopeFilter scopeFilter;
    private final ActionTokenFilter actionTokenFilter;
    private final ObjectMapper objectMapper;
    private final SecurityProperties securityProperties;

    @Bean
    @Order(0)
    public SecurityFilterChain docsFilterChain(final HttpSecurity http) throws Exception {
        http
            .securityMatcher(new AntPathRequestMatcher("/api/v1/docs/**"))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        List<String> permitAll = new ArrayList<>(securityProperties.getPermitAllPaths());
        permitAll.add("/api/v1/docs/**");

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    objectMapper.writeValue(response.getWriter(),
                        ApiResponse.fail(securityProperties.getAuthErrorCode(),
                            securityProperties.getAuthErrorMessage()));
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(permitAll.stream()
                    .map(AntPathRequestMatcher::new)
                    .toArray(AntPathRequestMatcher[]::new))
                .permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(actionTokenFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(scopeFilter, ActionTokenFilter.class)
            .addFilterBefore(jwtAuthFilter, ScopeFilter.class)
            .addFilterBefore(ipWhitelistFilter, JwtAuthFilter.class)
            .addFilterBefore(rateLimitFilter, IpWhitelistFilter.class);

        return http.build();
    }
}
