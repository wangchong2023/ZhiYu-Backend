/**
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: ActionTokenFilter.java
 * 创建时间: 2026-05-27
 * 描述: 操作验证令牌（Action Token）拦截过滤器，主要用于对用户修改密码、启用密保等高敏感 API 的二次安全认证过滤。
 */
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

/**
 * 类名: ActionTokenFilter
 * 描述: 自定义的安全认证过滤器。当访问高风险敏感操作接口时，要求客户端必须提供合法的二次动作验证 Token (X-Action-Token)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionTokenFilter extends OncePerRequestFilter {

    // Action Token 请求头键名
    private static final String ACTION_TOKEN_HEADER = "X-Action-Token";
    // 动作验证 Token 所属的 Scope 声明值
    private static final String ACTION_TOKEN_CLAIM = "action_token";
    // 敏感的 HTTP 方法集合
    private static final Set<String> SENSITIVE_METHODS = Set.of("DELETE", "PUT", "PATCH");
    // 需要二次安全验证的敏感请求 API 路径前缀
    private static final Set<String> SENSITIVE_PATH_PREFIXES = Set.of(
            "/api/v1/user/account",
            "/api/v1/auth/password",
            "/api/v1/auth/totp"
    );
    // 无需安全验证即可放行的免密白名单前缀
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

    /**
     * 描述: 对请求进行拦截过滤，若为高风险敏感路径操作，则强制校验 X-Action-Token 的合法性。
     * @param request 客户端发起的 Servlet 请求对象
     * @param response 服务端响应对象
     * @param chain 过滤器链
     * @throws ServletException Servlet处理异常
     * @throws IOException I/O 读写异常
     */
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

    /**
     * 描述: 判断当前请求是否属于高风险敏感操作（根据 HTTP Method 和 API 路径过滤判定）。
     * @param request 请求对象
     * @return 属于敏感操作返回 true，否则返回 false
     */
    private boolean isSensitiveOperation(final HttpServletRequest request) {
        String method = request.getMethod();
        if (!SENSITIVE_METHODS.contains(method)) {
            return false;
        }
        String path = request.getRequestURI();
        return SENSITIVE_PATH_PREFIXES.stream().anyMatch(path::startsWith)
                && EXEMPT_PREFIXES.stream().noneMatch(path::startsWith);
    }

    /**
     * 描述: 构造 JSON 返回响应，直接中断过滤器链并向客户端报错返回 403 Forbidden 响应。
     * @param response 响应对象
     * @param message 失败细节描述消息
     * @throws IOException I/O 读写异常
     */
    private void reject(final HttpServletResponse response, final String message) throws IOException {
        log.warn("Action token rejected: {}", message);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format(
                "{\"code\":%d,\"message\":\"%s\"}", BizErrorCode.ACCESS_DENIED.getCode(), message));
    }
}
