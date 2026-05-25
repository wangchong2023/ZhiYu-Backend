package com.zhiyu.ufp.common.exception;

import java.util.Map;

/**
 * SPI for customizing error conversion in {@code GlobalExceptionHandler}.
 * Implementations registered as Spring beans are automatically
 * collected and consulted in order.
 */
@FunctionalInterface
public interface ErrorConvertCustomize {

    /**
     * Convert a throwable to a structured error map.
     * @param e      the exception to convert
     * @return a map with {@code code} and {@code message} keys,
     *         or {@code null} if this converter does not handle the exception
     */
    Map<String, Object> convert(Throwable e);
}
