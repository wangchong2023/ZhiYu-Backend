/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: FeignClientInterceptor.java
 * 创建时间: 2026-05-28
 * 描述: Spring Cloud OpenFeign 跨服务 RPC 客户端拦截器。
 * 负责在进程级网络通信发生时，无损透传客户端认证凭证 (Authorization)、链路追踪 ID (X-Trace-Id) 以及语言偏好 (Accept-Language)。
 */
package com.zhiyu.common.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;

/**
 * 类名: FeignClientInterceptor
 * 描述: OpenFeign 拦截器实现类。当调用被 @FeignClient 标注的远程接口时，
 * 该拦截器自动拦截请求模板，并从当前 WebMVC 请求线程上下文中提取关键安全与国际化头部，实现微服务间的无感传播。
 */
@Component
public class FeignClientInterceptor implements RequestInterceptor {

    /**
     * 认证请求头字段名
     */
    private static final String HEADER_AUTHORIZATION = "Authorization";

    /**
     * 链路追踪请求头字段名
     */
    private static final String HEADER_TRACE_ID = "X-Trace-Id";

    /**
     * 国际化区域偏好请求头字段名
     */
    private static final String HEADER_ACCEPT_LANGUAGE = "Accept-Language";

    /**
     * 描述: 对 Feign 远程调用请求进行无损装配，透传上下文凭证及链路信息。
     * @param template Feign 声明式客户端请求参数模版
     */
    @Override
    public void apply(final RequestTemplate template) {
        // 关键步骤 1：从当前线程上下文中获取 Spring MVC 的 Web 请求属性
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            
            // 2. 提取并透传 OAuth2/JWT 认证凭证
            String authorization = request.getHeader(HEADER_AUTHORIZATION);
            if (authorization != null && !authorization.isEmpty()) {
                template.header(HEADER_AUTHORIZATION, authorization);
            }
            
            // 3. 提取并透传分布式链路 TraceId
            String traceId = request.getHeader(HEADER_TRACE_ID);
            if (traceId != null && !traceId.isEmpty()) {
                template.header(HEADER_TRACE_ID, traceId);
            }
        }

        // 关键步骤 2：提取并传播多语言/区域上下文 (Locale)。
        // 即使没有 Web MVC 上下文环境（例如异步线程池或调度器发起 RPC），也能通过 LocaleContextHolder 获取当前线程区域信息。
        Locale locale = LocaleContextHolder.getLocale();
        if (locale != null) {
            template.header(HEADER_ACCEPT_LANGUAGE, locale.toLanguageTag());
        }
    }
}
