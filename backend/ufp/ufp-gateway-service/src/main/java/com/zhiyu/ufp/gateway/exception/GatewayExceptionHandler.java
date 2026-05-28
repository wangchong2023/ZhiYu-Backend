/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 统一网关异常处理器。
 *
 * <p>实现 {@link ErrorWebExceptionHandler} 并将其优先级排在最前（{@code @Order(-1)}）。
 * 当网关层发生路由找不到（404）、方法不允许（405）或者微服务连接超时等异常时，拦截该异常。
 * 按照系统规范，将其重置为 HTTP 200，并输出包含标准业务错误码与英文描述的 JSON 报文体，保证前端交互流畅性。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
@Slf4j
@Component
@Order(-1)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final int ERR_INTERNAL_ERROR = 50001;
    private static final int ERR_RESOURCE_NOT_FOUND = 40401;
    private static final int ERR_METHOD_NOT_ALLOWED = 40501;
    private static final int ERR_SERVICE_UNAVAILABLE = 50301;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_METHOD_NOT_ALLOWED = 405;
    private static final int HTTP_INTERNAL_SERVER_ERROR = 500;

    @Override
    public Mono<Void> handle(final ServerWebExchange exchange, final Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        final String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_HEADER);
        final String path = exchange.getRequest().getURI().getPath();

        if (log.isErrorEnabled()) {
            log.error("[GATEWAY-ERROR] TraceId: {}, Path: {}, Message: {}", traceId, path, ex.getMessage(), ex);
        }

        // 统一在网关层返回 HTTP 200，以统一包装的业务错误码告知前端
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        int errCode = ERR_INTERNAL_ERROR;
        String errMsg = "Internal server error, please try again later";

        if (ex instanceof ResponseStatusException rse) {
            final int status = rse.getStatusCode().value();
            if (status == HTTP_NOT_FOUND) {
                errCode = ERR_RESOURCE_NOT_FOUND;
                errMsg = "Resource not found";
            } else if (status == HTTP_METHOD_NOT_ALLOWED) {
                errCode = ERR_METHOD_NOT_ALLOWED;
                errMsg = "HTTP method not allowed";
            } else if (status >= HTTP_INTERNAL_SERVER_ERROR) {
                errCode = ERR_SERVICE_UNAVAILABLE;
                errMsg = "Service temporarily unavailable";
            }
        } else if (ex instanceof java.net.ConnectException
                || ex instanceof java.util.concurrent.TimeoutException) {
            errCode = ERR_SERVICE_UNAVAILABLE;
            errMsg = "Service temporarily unavailable";
        }

        final String jsonBody = String.format("{\"code\":%d,\"message\":\"%s\"}", errCode, errMsg);
        final byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        final DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        final DataBuffer buffer = bufferFactory.wrap(bytes);

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
