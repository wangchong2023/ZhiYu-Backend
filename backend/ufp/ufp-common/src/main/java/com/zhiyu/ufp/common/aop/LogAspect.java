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

@Slf4j
@Aspect
@Component
public class LogAspect {

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
            // non-web context or request not available — HTTP fields are optional
            if (log.isDebugEnabled()) {
                log.debug("Cannot resolve HTTP context: {}", ex.getMessage());
            }
        }
    }
}
