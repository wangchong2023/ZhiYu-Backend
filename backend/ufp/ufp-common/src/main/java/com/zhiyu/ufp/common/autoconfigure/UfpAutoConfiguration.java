package com.zhiyu.ufp.common.autoconfigure;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Aggregated auto-configuration for ufp-common.
 * Activated by {@link com.zhiyu.ufp.common.annotation.EnableUfp}.
 */
@Configuration
@ComponentScan(basePackages = "com.zhiyu.ufp.common")
public class UfpAutoConfiguration {
}
