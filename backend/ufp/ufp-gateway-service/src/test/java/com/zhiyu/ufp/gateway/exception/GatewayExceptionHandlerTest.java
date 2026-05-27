/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.gateway.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一网关异常处理器测试类。
 *
 * <p>用于验证对不同异常类别（404/405/运行时异常）的拦截、状态码重写及统一 JSON Body 响应输出的正确性。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
class GatewayExceptionHandlerTest {

    private final GatewayExceptionHandler handler = new GatewayExceptionHandler();

    /**
     * 测试 404 资源不存在异常的处理结果。
     */
    @Test
    void shouldReturn40401WhenResourceNotFoundExceptionOccurs() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/invalid-resource").build());
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found");

        Mono<Void> result = handler.handle(exchange, ex);
        result.block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"code\":40401").contains("\"message\":\"Resource not found\"");
    }

    /**
     * 测试 405 请求方法不允许异常的处理结果。
     */
    @Test
    void shouldReturn40501WhenMethodNotAllowedExceptionOccurs() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build());
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed");

        Mono<Void> result = handler.handle(exchange, ex);
        result.block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"code\":40501").contains("\"message\":\"HTTP method not allowed\"");
    }

    /**
     * 测试普通系统运行时异常的处理结果。
     */
    @Test
    void shouldReturn50001WhenGenericExceptionOccurs() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/info").build());
        RuntimeException ex = new RuntimeException("Unknown DB Timeout");

        Mono<Void> result = handler.handle(exchange, ex);
        result.block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"code\":50001").contains("\"message\":\"Internal server error, please try again later\"");
    }

    /**
     * 测试异常处理器的优先级 Order 应为 -1。
     */
    @Test
    void shouldReturnCorrectOrderAnnotationValue() {
        Order order = GatewayExceptionHandler.class.getAnnotation(Order.class);
        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(-1);
    }
}
