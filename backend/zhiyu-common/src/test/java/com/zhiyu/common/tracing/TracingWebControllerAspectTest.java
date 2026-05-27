/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: TracingWebControllerAspectTest.java
 * 创建时间: 2026-05-27
 * 描述: TracingWebControllerAspect 全链路追踪 AOP 切面单元测试类，覆盖正常链路拦截及异常捕获时的 Span 状态断言。
 */
package com.zhiyu.common.tracing;
 
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
 
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
 
/**
 * 类名: TracingWebControllerAspectTest
 * 描述: 测试全链路追踪 AOP 切面。使用 Mockito 模拟切面所需的代理环境和 Micrometer 运行上下文，
 *      验证控制器调用前后 Span 是否被正确 start、end 以及是否处理了异常。
 */
@ExtendWith(MockitoExtension.class)
class TracingWebControllerAspectTest {
 
    @Mock
    private Tracer tracer;
 
    @Mock
    private Span span;
 
    @Mock
    private Tracer.SpanInScope spanInScope;
 
    @Mock
    private ProceedingJoinPoint joinPoint;
 
    private TracingWebControllerAspect aspect;
 
    @BeforeEach
    void setUp() {
        aspect = new TracingWebControllerAspect(tracer);
    }
 
    @Test
    void shouldProceedDirectlyWhenTracerIsNull() throws Throwable {
        TracingWebControllerAspect aspectWithNullTracer = new TracingWebControllerAspect(null);
        when(joinPoint.proceed()).thenReturn("success");
 
        Object result = aspectWithNullTracer.traceControllerMethod(joinPoint);
 
        assertThat(result).isEqualTo("success");
        verify(joinPoint).proceed();
    }
 
    @Test
    void shouldTraceSuccessfulControllerMethod() throws Throwable {
        // 模拟方法调用签名
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn("login");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new MockController());
 
        // 模拟 Tracer Span 的构建链
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(spanInScope);
 
        when(joinPoint.proceed()).thenReturn("login-success");
 
        // 执行切面拦截
        Object result = aspect.traceControllerMethod(joinPoint);
 
        // 校验结果与调用行为
        assertThat(result).isEqualTo("login-success");
        verify(span).name("Controller.MockController.login");
        verify(span).start();
        verify(tracer).withSpan(span);
        verify(joinPoint).proceed();
        verify(spanInScope).close();
        verify(span).end();
    }
 
    @Test
    void shouldTraceControllerMethodWithException() throws Throwable {
        // 模拟方法调用签名
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn("register");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new MockController());
 
        // 模拟 Tracer Span 的构建链
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(spanInScope);
 
        RuntimeException ex = new RuntimeException("DB Connection Fail");
        when(joinPoint.proceed()).thenThrow(ex);
 
        // 校验切面被拦截抛出异常，且标记错误状态
        assertThatThrownBy(() -> aspect.traceControllerMethod(joinPoint))
                .isEqualTo(ex);
 
        verify(span).name("Controller.MockController.register");
        verify(span).start();
        verify(tracer).withSpan(span);
        verify(span).error(ex);
        verify(spanInScope).close();
        verify(span).end();
    }
 
    /**
     * 私有静态测试控制器类，用于为 AOP 拦截提供具体的类名解析。
     */
    private static class MockController {
    }
}
