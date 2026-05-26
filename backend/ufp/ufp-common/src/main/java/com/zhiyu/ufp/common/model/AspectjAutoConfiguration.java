package com.zhiyu.ufp.common.model;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Enable AspectJ auto-proxy support for {@link TrimAdvice}.
 */
@Configuration
@EnableAspectJAutoProxy
public class AspectjAutoConfiguration {
}
