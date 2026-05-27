/**
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: SentinelBlockExceptionHandler.java
 * 创建时间: 2026-05-27
 * 描述: 自定义 Sentinel 限流熔断异常处理器。用于在请求被限流、降级、系统保护或授权拦截时，返回系统统一格式的 JSON 报错响应。
 */
package com.zhiyu.common.sentinel;

import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.web.ApiResponse;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * 类名: SentinelBlockExceptionHandler
 * 描述: 实现 Sentinel WebMvc 适配器提供的 BlockExceptionHandler 接口。
 *      根据不同的阻断异常类型返回不同的错误码及响应状态：
 *      - 流量限流 / 热点参数限流：返回 429 TOO_MANY_REQUESTS
 *      - 服务降级熔断 / 系统过载保护：返回 503 SERVICE_UNAVAILABLE
 *      - 授权控制拦截：返回 403 ACCESS_DENIED
 */
@Slf4j
@RequiredArgsConstructor
public class SentinelBlockExceptionHandler implements BlockExceptionHandler {

    private final ObjectMapper objectMapper;

    /**
     * 描述: 重写 Sentinel 的异常阻断处理逻辑。当发生阻断时，直接拦截请求，构造对应的多语言报错 JSON 包输出。
     * @param request Servlet 请求对象
     * @param response Servlet 响应对象
     * @param resourceName 触发 Sentinel 阻断的资源名称
     * @param e 具体的阻断异常实例
     * @throws IOException 当输出 JSON 数据产生 I/O 异常时抛出
     */
    @Override
    public void handle(final HttpServletRequest request,
                       final HttpServletResponse response,
                       final String resourceName,
                       final BlockException e) throws IOException {
        int code;
        String message;

        if (e instanceof FlowException) {
            if (log.isWarnEnabled()) {
                log.warn("Sentinel flow limited: resource={}, uri={}, ip={}",
                        resourceName, request.getRequestURI(), request.getRemoteAddr());
            }
            code = BizErrorCode.TOO_MANY_REQUESTS.getCode();
            message = BizErrorCode.TOO_MANY_REQUESTS.getMessage();
        } else if (e instanceof DegradeException) {
            if (log.isWarnEnabled()) {
                log.warn("Sentinel circuit breaker open: resource={}, uri={}",
                        resourceName, request.getRequestURI());
            }
            code = BizErrorCode.SERVICE_UNAVAILABLE.getCode();
            message = BizErrorCode.SERVICE_UNAVAILABLE.getMessage();
        } else if (e instanceof ParamFlowException) {
            if (log.isWarnEnabled()) {
                log.warn("Sentinel param flow limited: resource={}, uri={}, ip={}",
                        resourceName, request.getRequestURI(), request.getRemoteAddr());
            }
            code = BizErrorCode.TOO_MANY_REQUESTS.getCode();
            message = BizErrorCode.TOO_MANY_REQUESTS.getMessage();
        } else if (e instanceof SystemBlockException) {
            if (log.isWarnEnabled()) {
                log.warn("Sentinel system blocked: resource={}, uri={}",
                        resourceName, request.getRequestURI());
            }
            code = BizErrorCode.SERVICE_UNAVAILABLE.getCode();
            message = BizErrorCode.SERVICE_UNAVAILABLE.getMessage();
        } else if (e instanceof AuthorityException) {
            if (log.isWarnEnabled()) {
                log.warn("Sentinel authority denied: resource={}, uri={}",
                        resourceName, request.getRequestURI());
            }
            code = BizErrorCode.ACCESS_DENIED.getCode();
            message = BizErrorCode.ACCESS_DENIED.getMessage();
        } else {
            if (log.isWarnEnabled()) {
                log.warn("Sentinel blocked: resource={}, uri={}, type={}",
                        resourceName, request.getRequestURI(), e.getClass().getSimpleName());
            }
            code = BizErrorCode.TOO_MANY_REQUESTS.getCode();
            message = BizErrorCode.TOO_MANY_REQUESTS.getMessage();
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> apiResponse = ApiResponse.fail(code, message);

        PrintWriter writer = response.getWriter();
        try {
            writer.write(objectMapper.writeValueAsString(apiResponse));
            writer.flush();
        } finally {
            writer.close();
        }
    }
}
