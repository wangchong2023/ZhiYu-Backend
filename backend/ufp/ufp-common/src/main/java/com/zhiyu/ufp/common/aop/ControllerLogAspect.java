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

/**
 * Controller 请求生命周期日志切面。
 *
 * <p>拦截所有 RestController 控制器请求，记录请求进入、执行耗时与返回完成日志，
 * 并触发已注册的日志监听器回调。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
public class ControllerLogAspect {

    /** 追踪日志的监听器列表 */
    private final List<TracingLogListener> listeners;

    /**
     * 构造并注入日志监听器列表。
     *
     * @param listeners 监听器列表
     */
    public ControllerLogAspect(final List<TracingLogListener> listeners) {
        this.listeners = listeners;
    }

    /**
     * 环绕通知：拦截标记了 @RestController 的控制器类方法执行。
     *
     * <p>此处必须捕获 {@code Throwable} 以确保无论是正常执行还是异常执行，
     * 切面均能拦截耗时并记录相应的审计日志状态。
     * 异常被拦截记录后会通过 {@code throw e;} 原样抛出，不会产生“异常吞没”问题。
     * 故使用 {@code @SuppressWarnings("PMD.AvoidCatchingThrowable")} 屏蔽该规则警告。</p>
     *
     * @param joinPoint 连接点
     * @return 方法执行的返回值
     * @throws Throwable 执行中产生的异常，原样抛出
     */
    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logController(final ProceedingJoinPoint joinPoint) throws Throwable {
        final String method = joinPoint.getSignature().toShortString();
        final long start = System.currentTimeMillis();
        try {
            if (log.isInfoEnabled()) {
                log.info("{} - 请求开始", method);
            }
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;
            if (log.isInfoEnabled()) {
                log.info("{} - 请求完成，耗时 {}ms", method, elapsed);
            }
            notifyListeners(method, elapsed, null);
            return result;
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - start;
            if (log.isErrorEnabled()) {
                log.error("{} - 请求失败，耗时 {}ms: {}", method, elapsed, e.getMessage(), e);
            }
            notifyListeners(method, elapsed, e.getMessage());
            throw e;
        }
    }

    /**
     * 触发监听器通知回调。
     *
     * @param method 拦截的方法签名
     * @param elapsed 方法耗时（毫秒）
     * @param error 异常信息（正常返回为 null）
     */
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
