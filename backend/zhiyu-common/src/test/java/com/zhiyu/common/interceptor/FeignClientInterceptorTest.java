/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: FeignClientInterceptorTest.java
 * 创建时间: 2026-05-28
 * 描述: FeignClientInterceptor 跨服务调用拦截器的单元测试类。
 */
package com.zhiyu.common.interceptor;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 类名: FeignClientInterceptorTest
 * 描述: 测试 Feign 请求拦截器是否能在拦截时，将当前请求中的 JWT、TraceId 以及 Locale 正确地透传传播到 Feign RequestTemplate 中。
 */
public class FeignClientInterceptorTest {

    private FeignClientInterceptor interceptor;
    private RequestTemplate template;

    @BeforeEach
    public void setUp() {
        interceptor = new FeignClientInterceptor();
        template = new RequestTemplate();
    }

    @AfterEach
    public void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    public void testContextPropagation() {
        // 1. 模拟一个标准的 HttpServletRequest 并填入 Header
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer mock-token-123");
        request.addHeader("X-Trace-Id", "mock-trace-456");
        
        // 2. 绑定到 Spring RequestContext 线程上下文
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        // 3. 设置 Locale 偏好为英文
        LocaleContextHolder.setLocale(Locale.US);

        // 4. 执行拦截
        interceptor.apply(template);

        // 5. 校验 Feign 模板是否已装配了相应的 Headers
        assertTrue(template.headers().containsKey("Authorization"));
        assertTrue(template.headers().containsKey("X-Trace-Id"));
        assertTrue(template.headers().containsKey("Accept-Language"));

        assertEquals("Bearer mock-token-123", template.headers().get("Authorization").iterator().next());
        assertEquals("mock-trace-456", template.headers().get("X-Trace-Id").iterator().next());
        assertEquals("en-US", template.headers().get("Accept-Language").iterator().next());
    }
}
