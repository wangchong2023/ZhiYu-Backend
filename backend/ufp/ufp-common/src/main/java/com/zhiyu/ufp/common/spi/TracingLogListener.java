package com.zhiyu.ufp.common.spi;

import java.util.Map;

/**
 * SPI for persisting tracing/audit logs.
 * Implement and register as a Spring bean to capture
 * controller invocation logs produced by {@code ControllerLogAspect}.
 */
@FunctionalInterface
public interface TracingLogListener {

    /**
     * Called when a controller invocation completes.
     * @param logEntry structured log entry with keys:
     *                 method, duration, status (OK/FAILED),
     *                 error (message if failed), timestamp
     */
    void onLog(Map<String, Object> logEntry);
}
