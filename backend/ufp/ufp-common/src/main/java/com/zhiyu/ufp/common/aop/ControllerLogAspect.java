package com.zhiyu.ufp.common.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class ControllerLogAspect {

    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logController(final ProceedingJoinPoint joinPoint) throws Throwable {
        final String method = joinPoint.getSignature().toShortString();
        final long start = System.currentTimeMillis();
        try {
            if (log.isInfoEnabled()) {
                log.info("{} - request started", method);
            }
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;
            if (log.isInfoEnabled()) {
                log.info("{} - completed in {}ms", method, elapsed);
            }
            return result;
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - start;
            if (log.isErrorEnabled()) {
                log.error("{} - failed in {}ms: {}", method, elapsed, e.getMessage(), e);
            }
            throw e;
        }
    }
}
