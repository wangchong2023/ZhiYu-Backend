package com.zhiyu.common.sentinel;

import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.config.SentinelWebMvcConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
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
 */
@AutoConfiguration
@ConditionalOnClass(SphU.class)
@ConditionalOnProperty(prefix = "zhiyu.sentinel", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SentinelProperties.class)
public class SentinelAutoConfiguration {

    /**
     * Creates the custom block exception handler. Uses {@link ObjectProvider} for
     * {@link SentinelWebMvcConfig} to break the circular dependency between this
     * configuration and Spring Cloud Alibaba's {@code SentinelWebAutoConfiguration}.
     */
    @Bean
    public SentinelBlockExceptionHandler sentinelBlockExceptionHandler(
            final ObjectMapper objectMapper,
            final ObjectProvider<SentinelWebMvcConfig> sentinelWebMvcConfigProvider) {
        SentinelBlockExceptionHandler handler = new SentinelBlockExceptionHandler(objectMapper);
        SentinelWebMvcConfig config = sentinelWebMvcConfigProvider.getObject();
        config.setBlockExceptionHandler(handler);
        config.setHttpMethodSpecify(true);
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
