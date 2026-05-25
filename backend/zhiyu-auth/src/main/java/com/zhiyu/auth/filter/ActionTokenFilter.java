package com.zhiyu.auth.filter;

import com.zhiyu.ufp.auth.jwt.JwtService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActionTokenFilter extends OncePerRequestFilter {

    private static final String ACTION_TOKEN_HEADER = "X-Action-Token";
    private static final String ACTION_TOKEN_CLAIM = "action_token";
    private static final Set<String> SENSITIVE_METHODS = Set.of("DELETE", "PUT", "PATCH");
    private static final Set<String> SENSITIVE_PATH_PREFIXES = Set.of(
            "/api/v1/user/account",
            "/api/v1/auth/password",
            "/api/v1/auth/totp"
    );
    private static final Set<String> EXEMPT_PREFIXES = Set.of(
            "/api/v1/auth/captcha",
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/oauth",
            "/api/v1/admin"
    );

    private final JwtService jwtService;
    private final ActionTokenService actionTokenService;

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        if (!isSensitiveOperation(request)) {
            chain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(ACTION_TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            reject(response, "Missing action verification token");
            return;
        }

        try {
            var claims = jwtService.verify(token);
            if (!ACTION_TOKEN_CLAIM.equals(claims.scope())) {
                reject(response, "Invalid action token type");
                return;
            }
            if (actionTokenService.isUsed(claims.jti())) {
                reject(response, "Action token has already been used");
                return;
            }
            actionTokenService.markUsed(claims.jti(), claims.exp());
        } catch (Exception e) {
            reject(response, "Action token is invalid or expired");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isSensitiveOperation(final HttpServletRequest request) {
        String method = request.getMethod();
        if (!SENSITIVE_METHODS.contains(method)) {
            return false;
        }
        String path = request.getRequestURI();
        return SENSITIVE_PATH_PREFIXES.stream().anyMatch(path::startsWith)
                && EXEMPT_PREFIXES.stream().noneMatch(path::startsWith);
    }

    private void reject(final HttpServletResponse response, final String message) throws IOException {
        log.warn("Action token rejected: {}", message);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format(
                "{\"code\":%d,\"message\":\"%s\"}", BizErrorCode.ACCESS_DENIED.getCode(), message));
    }
}
