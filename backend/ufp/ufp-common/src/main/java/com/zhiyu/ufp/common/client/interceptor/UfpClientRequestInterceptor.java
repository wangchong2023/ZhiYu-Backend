/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.common.client.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Feign 客户端统一请求拦截器。
 *
 * <p>用于在微服务调用链间透传以下基础环境属性：
 * 1. 传播当前请求的分布式追踪 TraceId 标头 (X-Trace-Id)。
 * 2. 传播租户标识标头 (X-Tenant-Id)。
 * 保证下游服务日志在链路排障时与上游完全契合。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
public class UfpClientRequestInterceptor implements RequestInterceptor {

    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    public void apply(final RequestTemplate template) {
        final ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            final HttpServletRequest request = attributes.getRequest();
            
            // 传递 TraceId
            final String traceId = request.getHeader(TRACE_HEADER);
            if (traceId != null && !traceId.isEmpty()) {
                template.header(TRACE_HEADER, traceId);
            }
            
            // 传递 TenantId
            final String tenantId = request.getHeader(TENANT_HEADER);
            if (tenantId != null && !tenantId.isEmpty()) {
                template.header(TENANT_HEADER, tenantId);
            }
        }
    }
}
