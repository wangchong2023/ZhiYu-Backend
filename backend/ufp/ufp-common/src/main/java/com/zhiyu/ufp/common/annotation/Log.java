package com.zhiyu.ufp.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level operation logging.
 * Intercepted by {@code LogAspect}, which builds a {@code TracingLog}
 * and publishes it via the asynchronous EventBus.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {

    /** Human-readable description of the operation. */
    String value() default "";

    /** Operation category (INSERT, UPDATE, DELETE, QUERY, LOGIN, EXPORT, etc.). */
    String operation() default "";

    /** Whether to capture and include method parameters in the log. */
    boolean logParams() default true;

    /** Whether to capture and include the return value in the log. */
    boolean logResult() default false;
}
