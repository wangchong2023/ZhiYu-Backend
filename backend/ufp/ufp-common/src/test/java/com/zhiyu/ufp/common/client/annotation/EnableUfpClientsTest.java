/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.common.client.annotation;

import com.zhiyu.ufp.common.client.auth.AuthUserClient;
import com.zhiyu.ufp.common.client.auth.AuthManagementClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EnableUfpClients 注解测试类。
 *
 * <p>用于验证注解加载后是否能自动驱动包扫描，将 client 包下的二方 Feign 客户端 Bean 正确载入 Spring 容器。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
class EnableUfpClientsTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableUfpClients
    @ImportAutoConfiguration({FeignAutoConfiguration.class})
    static class TestConfig {
    }

    /**
     * 测试注解启动后，Feign 客户端 Proxy Bean 能够正常注册并在容器中可检索。
     */
    @Test
    void shouldLoadFeignClientsSuccessfully() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AuthUserClient.class);
            assertThat(context).hasSingleBean(AuthManagementClient.class);
        });
    }
}
