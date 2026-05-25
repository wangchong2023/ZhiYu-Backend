package com.zhiyu.ufp.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service interface as a remotely-callable UFP client.
 * When a Feign/RPC client bean exists for the annotated interface,
 * it takes precedence over the local implementation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UfpClient {

    /** Service name for service discovery. */
    String name() default "";

    /** Base path for HTTP transport. */
    String path() default "";
}
