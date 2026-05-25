package com.zhiyu.ufp.common.annotation;

import com.zhiyu.ufp.common.autoconfigure.UfpAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enable all UFP common infrastructure.
 * Place on any {@code @Configuration} or application class
 * to activate ufp-common auto-configuration.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(UfpAutoConfiguration.class)
public @interface EnableUfp {
}
