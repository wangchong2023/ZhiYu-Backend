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

    /**
     * 描述: 测试当 Spring RequestContextHolder 上下文为空（如在异步线程池、定时调度任务中发起 Feign 调用）时，
     *       拦截器仍然能够优雅防御 NullPointerException，并仅透传 Locale 上下文（若存在），满足 TSK-P1-003 边缘用例规范。
     */
    @Test
    public void testContextPropagationWhenRequestContextIsNull() {
        // 1. 确保 RequestContextHolder 显式为 null
        RequestContextHolder.resetRequestAttributes();

        // 2. 设置 Locale 上下文为中文
        LocaleContextHolder.setLocale(Locale.CHINA);

        // 3. 执行拦截动作
        interceptor.apply(template);

        // 4. 验证 Feign RequestTemplate 中不包含 Authorization 和 X-Trace-Id，但依然成功透传了 Accept-Language
        assertTrue(!template.headers().containsKey("Authorization"));
        assertTrue(!template.headers().containsKey("X-Trace-Id"));
        assertTrue(template.headers().containsKey("Accept-Language"));
        assertEquals("zh-CN", template.headers().get("Accept-Language").iterator().next());
    }

    /**
     * 描述: 测试当 Spring RequestContext 存在，但请求 Header 中不包含任何安全凭证或链路标识时，
     *       拦截器能够安全防御，不向 Feign 模板中注入空的或者为 null 的头部字段。
     */
    @Test
    public void testContextPropagationWhenHeadersAreMissing() {
        // 1. 模拟一个完全为空、不带任何 Header 的 HttpServletRequest
        MockHttpServletRequest request = new MockHttpServletRequest();

        // 2. 绑定上下文并设定多语言
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        LocaleContextHolder.setLocale(Locale.US);

        // 3. 执行拦截
        interceptor.apply(template);

        // 4. 验证空 Header 没有被透传，防止将 null/empty 写入请求模板
        assertTrue(!template.headers().containsKey("Authorization"));
        assertTrue(!template.headers().containsKey("X-Trace-Id"));
        assertTrue(template.headers().containsKey("Accept-Language"));
        assertEquals("en-US", template.headers().get("Accept-Language").iterator().next());
    }
}
