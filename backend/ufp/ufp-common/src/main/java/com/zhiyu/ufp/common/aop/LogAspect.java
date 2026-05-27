package com.zhiyu.ufp.common.aop;

import com.zhiyu.ufp.common.annotation.Log;
import com.zhiyu.ufp.common.event.EventUtils;
import com.zhiyu.ufp.common.model.TracingLog;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Optional;

/**
 * 核心操作日志审计切面类。
 *
 * <p>用于拦截被 {@link Log} 注解标记的方法，提取操作类型、描述、参数、耗时及执行结果等审计信息，
 * 并通过事件总线异步投递至日志消费端。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
public class LogAspect {

    /**
     * 环绕通知：拦截标记了 @Log 注解的方法执行。
     *
     * <p>此处必须捕获 {@code Throwable} 以确保无论是正常返回还是抛出异常，
     * 切面均能拦截耗时并记录相应的审计日志状态。
     * 异常被拦截记录后会通过 {@code throw e;} 原样抛出，不会产生“异常吞没”问题。
     * 故使用 {@code @SuppressWarnings("PMD.AvoidCatchingThrowable")} 屏蔽该规则警告。</p>
     *
     * @param joinPoint 连接点
     * @param logAnnotation 日志注解实例
     * @return 方法执行的返回值
     * @throws Throwable 执行中产生的异常，原样抛出
     */
    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    @Around("@annotation(logAnnotation)")
    public Object logMethod(final ProceedingJoinPoint joinPoint, final Log logAnnotation) throws Throwable {
        final long start = System.currentTimeMillis();
        final MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        final String className = signature.getDeclaringType().getSimpleName();
        final String methodName = signature.getName();
        String errorMessage = null;
        String status = "OK";

        TracingLog.TracingLogBuilder builder = TracingLog.builder()
                .className(className)
                .methodName(methodName)
                .operation(logAnnotation.operation())
                .description(logAnnotation.value())
                .timestamp(start);

        resolveHttpContext(builder);

        if (logAnnotation.logParams()) {
            builder.requestParams(Arrays.toString(joinPoint.getArgs()));
        }

        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;
            builder.duration(elapsed).status(status);
            if (logAnnotation.logResult() && result != null) {
                builder.response(String.valueOf(result));
            }
            return result;
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - start;
            errorMessage = e.getMessage();
            status = "FAILED";
            builder.duration(elapsed).status(status).errorMessage(errorMessage);
            throw e;
        } finally {
            EventUtils.asyncPost(builder.build());
        }
    }

    /**
     * 解析当前的 HTTP 请求上下文，并填充 IP 与请求 URI 信息。
     *
     * @param builder 操作日志构建器
     */
    private void resolveHttpContext(final TracingLog.TracingLogBuilder builder) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                builder.ipAddress(Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                        .orElse(request.getRemoteAddr()));
                builder.requestUri(request.getRequestURI());
            }
        } catch (Exception ex) {
            // 非 Web 上下文或请求不可用时安全忽略。
            if (log.isDebugEnabled()) {
                log.debug("Failed to resolve HTTP context: {}", ex.getMessage());
            }
        }
    }
}
