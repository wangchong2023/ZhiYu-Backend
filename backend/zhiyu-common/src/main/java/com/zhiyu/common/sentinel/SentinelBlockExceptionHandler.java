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
 * Custom Sentinel block exception handler that returns consistent JSON responses.
 *
 * <p>Differentiates between block types:
 * <ul>
 *   <li>Flow / ParamFlow — {@code TOO_MANY_REQUESTS} (429)</li>
 *   <li>Degrade / SystemBlock — {@code SERVICE_UNAVAILABLE} (503)</li>
 *   <li>Authority — {@code ACCESS_DENIED} (403)</li>
 * </ul>
 * Each response includes requestId and timestamp for traceability.
 */
@Slf4j
@RequiredArgsConstructor
public class SentinelBlockExceptionHandler implements BlockExceptionHandler {

    private final ObjectMapper objectMapper;

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
