package com.zhiyu.common.sentinel;

import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.config.SentinelWebMvcConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Sentinel rate limiting and circuit breaking.
 *
 * <p>Activates only when:
 * <ul>
 *   <li>{@code SphU} (Sentinel core) is on the classpath</li>
 *   <li>{@code zhiyu.sentinel.enabled=true} (default)</li>
 * </ul>
 *
 * <p>Provides:
 * <ul>
 *   <li>Custom {@link SentinelBlockExceptionHandler} — returns consistent
 *       JSON error responses for blocked requests</li>
 *   <li>{@link SentinelRulesInitializer} — loads default flow control
 *       rules at startup</li>
 * </ul>
 *
 * <p>Integrates with the {@link SentinelWebMvcConfig} created by Spring
 * Cloud Alibaba's {@code SentinelWebAutoConfiguration} by setting the
 * custom block handler on the shared configuration bean.</p>
 */
@AutoConfiguration
@ConditionalOnClass(SphU.class)
@ConditionalOnProperty(prefix = "zhiyu.sentinel", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SentinelProperties.class)
public class SentinelAutoConfiguration {

    /**
     * Creates the custom block exception handler and registers it on the
     * shared {@link SentinelWebMvcConfig} bean (created by Spring Cloud
     * Alibaba). This replaces the default text/plain error page with a
     * consistent JSON response matching the standard API envelope format.
     */
    @Bean
    public SentinelBlockExceptionHandler sentinelBlockExceptionHandler(
            final ObjectMapper objectMapper,
            final SentinelWebMvcConfig sentinelWebMvcConfig) {
        SentinelBlockExceptionHandler handler = new SentinelBlockExceptionHandler(objectMapper);
        sentinelWebMvcConfig.setBlockExceptionHandler(handler);
        sentinelWebMvcConfig.setHttpMethodSpecify(true);
        return handler;
    }

    /**
     * Initializes Sentinel flow control rules from configuration or
     * built-in defaults. Fires on {@code ApplicationReadyEvent} to
     * allow Nacos datasource to load its rules first.
     */
    @Bean
    public SentinelRulesInitializer sentinelRulesInitializer(final SentinelProperties properties) {
        return new SentinelRulesInitializer(properties);
    }
}
