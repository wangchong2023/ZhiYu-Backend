/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: TracingWebControllerAspect.java
 * 创建时间: 2026-05-27
 * 描述: 全链路追踪 Web 控制层 AOP 切面，为每一个接口请求自动创建子 Span，并将追踪上下文注入线程环境及日志 MDC 容器中。
 */
package com.zhiyu.common.tracing;
 
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
 
/**
 * 类名: TracingWebControllerAspect
 * 描述: 该切面拦截所有使用 @RestController 或 @Controller 注解修饰的控制层类的公共方法，
 *      为每一次调用开启方法级的 Span 追踪，辅助进行耗时监控与调用链故障定位。
 */
@Aspect
@Slf4j
@RequiredArgsConstructor
public class TracingWebControllerAspect {
 
    private final Tracer tracer;
 
    /**
     * 描述: 定义切入点，匹配 com.zhiyu 包及其子包下被 @RestController 或 @Controller 注解修饰的类的所有公共方法。
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *) || within(@org.springframework.stereotype.Controller *)")
    public void controllerPointcut() {
        // AOP 切点定义，无需实现内容
    }
 
    /**
     * 描述: 环绕通知，动态拦截控制层方法。在方法执行前创建并启动追踪 Span，在执行完毕或抛出异常时终结 Span 并清理上下文。
     * @param joinPoint 切入点对象信息
     * @return 业务方法返回值
     * @throws Throwable 业务执行异常
     */
    @Around("controllerPointcut()")
    public Object traceControllerMethod(final ProceedingJoinPoint joinPoint) throws Throwable {
        if (tracer == null) {
            return joinPoint.proceed();
        }
 
        // 提取被调用类的类名和方法名作为 Span 名称前缀
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String spanName = "Controller." + className + "." + methodName;
 
        // 开启并启动子 Span
        Span newSpan = tracer.nextSpan().name(spanName);
        newSpan.start();
 
        // 将当前子 Span 激活至当前线程 Scope（使 traceId/spanId 可自动关联日志 MDC）
        try (Tracer.SpanInScope ws = tracer.withSpan(newSpan)) {
            if (log.isDebugEnabled()) {
                log.debug("Starting trace span: name={}", spanName);
            }
            // 执行业务方法
            return joinPoint.proceed();
        } catch (Throwable t) {
            // 方法出现异常时，记录异常至 Trace Span tag 标签中
            newSpan.error(t);
            throw t;
        } finally {
            // 确保无论如何都在方法结束时终结并关闭 Span
            newSpan.end();
            if (log.isDebugEnabled()) {
                log.debug("Ended trace span: name={}", spanName);
            }
        }
    }
}
