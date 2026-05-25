package com.zhiyu.ufp.common.aop;

import com.zhiyu.ufp.common.spi.TracingLogListener;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Aspect
@Component
public class ControllerLogAspect {

    private final List<TracingLogListener> listeners;

    public ControllerLogAspect(final List<TracingLogListener> listeners) {
        this.listeners = listeners;
    }

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
            notifyListeners(method, elapsed, null);
            return result;
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - start;
            if (log.isErrorEnabled()) {
                log.error("{} - failed in {}ms: {}", method, elapsed, e.getMessage(), e);
            }
            notifyListeners(method, elapsed, e.getMessage());
            throw e;
        }
    }

    private void notifyListeners(final String method, final long elapsed, final String error) {
        if (listeners.isEmpty()) {
            return;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("method", method);
        entry.put("duration", elapsed);
        entry.put("status", error == null ? "OK" : "FAILED");
        if (error != null) {
            entry.put("error", error);
        }
        entry.put("timestamp", System.currentTimeMillis());
        for (TracingLogListener listener : listeners) {
            listener.onLog(entry);
        }
    }
}
