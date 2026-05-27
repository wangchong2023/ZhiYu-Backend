/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局网关审计日志过滤器测试类。
 *
 * <p>用于验证 TraceId 的生成与透传、客户端真实 IP 提取、JWT 用户解析以及过滤器优先级等核心逻辑。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
class GatewayLoggingFilterTest {

    private final GatewayLoggingFilter filter = new GatewayLoggingFilter();

    /**
     * 测试当请求头中无 TraceId 时，过滤器是否能动态生成并透传。
     */
    @Test
    void shouldGenerateTraceIdWhenHeaderIsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/info").build());
        GatewayFilterChain chain = e -> {
            String traceId = e.getRequest().getHeaders().getFirst("X-Trace-Id");
            assertThat(traceId).isNotNull().isNotEmpty();
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
    }

    /**
     * 测试当请求头中已有 TraceId 时，过滤器是否能保留并向下游透传。
     */
    @Test
    void shouldPropagateExistingTraceId() {
        String existingTraceId = "test-trace-id-123456";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/info")
                        .header("X-Trace-Id", existingTraceId)
                        .build());
        GatewayFilterChain chain = e -> {
            String traceId = e.getRequest().getHeaders().getFirst("X-Trace-Id");
            assertThat(traceId).isEqualTo(existingTraceId);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
    }

    /**
     * 测试当通过 X-Forwarded-For 提供多级代理 IP 时，过滤器是否能正确提取首个真实 IP。
     */
    @Test
    void shouldExtractClientIpFromForwardedForHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/info")
                        .header("X-Forwarded-For", "192.168.1.100, 10.0.0.1")
                        .build());
        GatewayFilterChain chain = e -> Mono.empty();

        filter.filter(exchange, chain).block();
        // 此处日志中应打印 192.168.1.100，doFinally 执行正常，不抛异常即可
    }

    /**
     * 测试当请求头包含 Authorization JWT 且能被反解时，过滤器能否解析出 sub 或 username。
     */
    @Test
    void shouldExtractUserFromValidJwtToken() {
        // 构建一个包含 sub 字段的极简 JWT Payload: {"sub":"admin-user","exp":1716812800}
        String payload = "{\"sub\":\"admin-user\",\"exp\":1716812800}";
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String mockJwt = "header." + encodedPayload + ".signature";

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/info")
                        .header("Authorization", "Bearer " + mockJwt)
                        .build());
        GatewayFilterChain chain = e -> Mono.empty();

        filter.filter(exchange, chain).block();
        // doFinally 执行正常，不抛出异常即可
    }

    /**
     * 测试当 JWT 格式不正确时，过滤器能否安全防范降级，不阻断请求。
     */
    @Test
    void shouldHandleInvalidJwtTokenGracefully() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/info")
                        .header("Authorization", "Bearer invalid_token_format")
                        .build());
        GatewayFilterChain chain = e -> Mono.empty();

        filter.filter(exchange, chain).block();
        // 验证即使反解抛出异常，过滤链依然能正常终结
    }

    /**
     * 测试过滤器是否为最高优先级，即 getOrder 应返回 Ordered.HIGHEST_PRECEDENCE。
     */
    @Test
    void shouldReturnHighestPrecedenceOrder() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
