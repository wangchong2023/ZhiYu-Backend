/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.gateway.config;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.http.server.reactive.HttpHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Netty 副 HTTP 端口自动配置测试类。
 *
 * <p>用于验证在响应式 Web 上下文（REACTIVE Web Application Context）中，
 * 配置 server.http.port 后，多端口装配 Bean 是否能正确加载并工作。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
class NettyHttpPortAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
            .withUserConfiguration(NettyHttpPortAutoConfiguration.class);

    /**
     * 测试当未指定 server.http.port 配置时，自动配置不应该加载 Bean。
     */
    @Test
    void shouldNotLoadWhenHttpPortPropertyIsMissing() {
        this.contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(NettyHttpPortAutoConfiguration.NettyReactiveWeb.class);
        });
    }

    /**
     * 测试当指定了 server.http.port 配置时，自动配置能够成功加载 Bean。
     */
    @Test
    void shouldLoadBeanWhenHttpPortPropertyIsPresent() {
        HttpHandler mockHttpHandler = Mockito.mock(HttpHandler.class);
        this.contextRunner
                .withPropertyValues("server.http.port=0")
                .withBean(HttpHandler.class, () -> mockHttpHandler)
                .run(context -> {
                    assertThat(context).hasSingleBean(NettyHttpPortAutoConfiguration.NettyReactiveWeb.class);
                    NettyHttpPortAutoConfiguration.NettyReactiveWeb bean =
                            context.getBean(NettyHttpPortAutoConfiguration.NettyReactiveWeb.class);
                    assertThat(bean).isNotNull();
                });
    }
}
