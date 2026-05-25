package com.zhiyu.auth.filter;

import com.zhiyu.common.web.FilterResponseUtil;
import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.auth.token.TokenBlacklist;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final int BEARER_PREFIX_LENGTH = 7;
    private static final Set<String> PERMIT_URLS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/captcha",
            "/api/v1/auth/refresh",
            "/api/v1/admin/login",
            "/actuator/health",
            "/actuator/prometheus",
            "/api/v1/docs/swagger-ui",
            "/api/v1/docs/api-docs"
    );

    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isPermitted(path)) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX_LENGTH);
        try {
            if (tokenBlacklist.isBlacklisted(token)) {
                FilterResponseUtil.writeError(response, HttpServletResponse.SC_OK,
                        BizErrorCode.TOKEN_REUSE_DETECTED.getCode(),
                        BizErrorCode.TOKEN_REUSE_DETECTED.getMessage());
                return;
            }

            var claims = jwtService.verify(token);
            var auth = new UsernamePasswordAuthenticationToken(
                    claims.sub(), token,
                    List.of(new SimpleGrantedAuthority("ROLE_" + claims.scope()))
            );
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception e) {
            FilterResponseUtil.writeError(response, HttpServletResponse.SC_OK,
                    BizErrorCode.INVALID_TOKEN.getCode(), e.getMessage());
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isPermitted(final String path) {
        return PERMIT_URLS.stream().anyMatch(path::startsWith);
    }
}
