/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.common.feign;

import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feign 自动配置策略测试类。
 *
 * <p>验证 Feign Spec 自动配置类是否能在 Spring 容器中正确注入重试器和统一错误解码器。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
class FeignSpecAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FeignSpecAutoConfiguration.class);

    /**
     * 测试配置类能否成功加载并将 Feign Retryer 和 ErrorDecoder Bean 注入容器。
     */
    @Test
    void shouldConfigureRetryerAndDecoderBeansSuccessfully() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Retryer.class);
            assertThat(context).hasSingleBean(ErrorDecoder.class);
            assertThat(context.getBean(ErrorDecoder.class)).isInstanceOf(FeignErrorDecoder.class);
        });
    }
}
