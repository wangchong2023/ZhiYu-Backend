/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.common.client.interceptor;

import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feign 上下文传播拦截器测试类。
 *
 * <p>测试验证在包含 HTTP 请求上下文时，TraceId 及 TenantId 标头能否被自动提取并在远程 Feign 调用中透传。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
class UfpClientRequestInterceptorTest {

    private final UfpClientRequestInterceptor interceptor = new UfpClientRequestInterceptor();

    @AfterEach
    void cleanUp() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * 测试当 Servlet 请求上下文中存在 TraceId 和 TenantId 时，拦截器能将其透传注入。
     */
    @Test
    void shouldPropagateHeadersWhenRequestContextIsPresent() {
        HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        Mockito.when(mockRequest.getHeader("X-Trace-Id")).thenReturn("trace-999");
        Mockito.when(mockRequest.getHeader("X-Tenant-Id")).thenReturn("tenant-888");

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        Map<String, Collection<String>> headers = template.headers();
        assertThat(headers.get("X-Trace-Id")).containsExactly("trace-999");
        assertThat(headers.get("X-Tenant-Id")).containsExactly("tenant-888");
    }

    /**
     * 测试在非 Web 线程或缺少 HTTP 上下文的场景下，拦截器降级防御，不抛出异常。
     */
    @Test
    void shouldNotPropagateHeadersWhenRequestContextIsMissing() {
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).isEmpty();
    }
}
