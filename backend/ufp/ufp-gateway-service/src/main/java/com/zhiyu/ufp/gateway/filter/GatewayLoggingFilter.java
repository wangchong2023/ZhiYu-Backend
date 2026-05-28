/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全局网关审计日志过滤器。
 *
 * <p>以最高优先级执行，用于拦截进出网关的全部流量，执行以下流程：
 * 1. 动态生成或传播分布式 TraceId (链路追踪 ID) 并置于请求头。
 * 2. 提取客户端真实 IP（支持反向代理透传的 X-Forwarded-For / X-Real-IP 检测）。
 * 3. 安全反解 Request Header 中的 JWT 负载（Payload），以非阻塞方式提取用户名/用户ID。
 * 4. 统计完整生命周期的请求总耗时，并在请求结束时（不论成功、异常还是取消）输出审计日志。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
@Slf4j
@Component
public class GatewayLoggingFilter implements GlobalFilter, Ordered {

    private static final int LOGGING_FILTER_ORDER = HIGHEST_PRECEDENCE;
    private static final String START_TIME_ATTR = "gateway_logging_start_time";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int DEFAULT_HTTP_STATUS = 200;
    private static final Pattern JWT_USER_PATTERN = Pattern.compile("\"(sub|username)\"\\s*:\\s*\"([^\"]+)\"");

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final GatewayFilterChain chain) {
        final long startTime = System.currentTimeMillis();
        exchange.getAttributes().put(START_TIME_ATTR, startTime);

        // 1. 提取或生成链路 TraceId
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        // 2. 向下游请求传递最新的 TraceId
        final ServerWebExchange mutatedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate().header(TRACE_ID_HEADER, traceId).build())
                .build();

        final String finalTraceId = traceId;

        // 3. 执行过滤链，并在终结时打印审计日志
        return chain.filter(mutatedExchange).doFinally(signalType -> {
            final Long start = mutatedExchange.getAttribute(START_TIME_ATTR);
            final long duration = start != null ? System.currentTimeMillis() - start : 0L;
            final var response = mutatedExchange.getResponse();
            final int statusCode = response.getStatusCode() != null
                    ? response.getStatusCode().value() : DEFAULT_HTTP_STATUS;

            final String clientIp = getClientIp(mutatedExchange);
            final String method = mutatedExchange.getRequest().getMethod().name();
            final String path = mutatedExchange.getRequest().getURI().getPath();
            final String user = getUsernameFromJwt(mutatedExchange);

            if (log.isInfoEnabled()) {
                log.info("[AUDIT] TraceId: {}, IP: {}, User: {}, Method: {}, Path: {},"
                                + " Status: {}, Duration: {}ms, Signal: {}",
                        finalTraceId, clientIp, user, method, path,
                        statusCode, duration, signalType);
            }
        });
    }

    /**
     * 获取客户端的真实 IP 地址。
     *
     * @param exchange 服务网关上下文
     * @return 客户端的 IP 字符串
     */
    private String getClientIp(final ServerWebExchange exchange) {
        final var headers = exchange.getRequest().getHeaders();
        String ip = headers.getFirst("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = headers.getFirst("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            final var remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                ip = remoteAddress.getAddress().getHostAddress();
            }
        }
        if (ip != null && ip.indexOf(',') != -1) {
            ip = ip.substring(0, ip.indexOf(',')).trim();
        }
        return ip != null ? ip : "unknown";
    }

    /**
     * 尝试以非阻塞、轻量化的 Base64 解码方式从 Authorization 标头中提取 JWT Payload 的 sub/username。
     *
     * @param exchange 服务网关上下文
     * @return 提取到的用户名，若提取失败则返回 anonymous
     */
    private String getUsernameFromJwt(final ServerWebExchange exchange) {
        final String authHeader = exchange.getRequest().getHeaders().getFirst(AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            final String token = authHeader.substring(BEARER_PREFIX.length());
            try {
                final String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    final byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
                    final String payload = new String(payloadBytes, StandardCharsets.UTF_8);
                    final Matcher matcher = JWT_USER_PATTERN.matcher(payload);
                    if (matcher.find()) {
                        return matcher.group(2);
                    }
                }
            } catch (Exception e) {
                // JWT 解码失败仅做防御降级处理，不阻断流程
                if (log.isDebugEnabled()) {
                    log.debug("无法反解 JWT 审计信息: {}", e.getMessage());
                }
            }
        }
        return "anonymous";
    }

    @Override
    public int getOrder() {
        return LOGGING_FILTER_ORDER;
    }
}
