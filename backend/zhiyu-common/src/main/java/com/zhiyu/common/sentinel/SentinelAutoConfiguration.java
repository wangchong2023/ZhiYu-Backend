/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: SentinelAutoConfiguration.java
 * 创建时间: 2026-05-27
 * 描述: Sentinel 流控与降级自动配置类，配置限流拦截器并初始化限流规则。
 */
package com.zhiyu.common.sentinel;

import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.config.SentinelWebMvcConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

/**
 * 类名: SentinelAutoConfiguration
 * 描述: Sentinel 核心配置类。在 classpath 中存在 SphU 类且 zhiyu.sentinel.enabled=true 时激活。
 */
@AutoConfiguration
@ConditionalOnClass(SphU.class)
@ConditionalOnProperty(prefix = "zhiyu.sentinel", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SentinelProperties.class)
public class SentinelAutoConfiguration {

    /**
     * 描述: 在容器完全启动后注册自定义 Sentinel 限流异常处理器。
     *      使用事件监听器避免与 SentinelWebAutoConfiguration 之间的循环依赖。
     */
    @EventListener(ApplicationReadyEvent.class)
    @SuppressWarnings("PMD.CloseResource")
    // ctx 为 Spring ApplicationContext 容器本身，由框架负责管理其生命周期，
    // 此处仅读取 Bean，不持有其所有权，故不需要也不应在此关闭。
    public void registerBlockExceptionHandler(final ApplicationReadyEvent event) {
        var ctx = event.getApplicationContext();
        SentinelWebMvcConfig config = ctx.getBeanProvider(SentinelWebMvcConfig.class).getIfAvailable();
        if (config != null) {
            SentinelBlockExceptionHandler handler =
                    new SentinelBlockExceptionHandler(ctx.getBean(ObjectMapper.class));
            config.setBlockExceptionHandler(handler);
            config.setHttpMethodSpecify(true);
        }
    }

    /**
     * 描述: 初始化 Sentinel 限流及熔断降级规则，通过 ApplicationReadyEvent 监听器进行规则注册
     * @param properties 关联的外部配置属性
     * @return 规则初始化器 SentinelRulesInitializer
     */
    @Bean
    public SentinelRulesInitializer sentinelRulesInitializer(final SentinelProperties properties) {
        return new SentinelRulesInitializer(properties);
    }
}

