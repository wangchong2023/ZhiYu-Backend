/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.common.feign;

import feign.Feign;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 客户端可靠性策略自动配置类。
 *
 * <p>提供通用的 Feign 增强策略：
 * 1. 注册指数退避重试器 {@link Retryer}，在服务调用失败时进行自动重试。
 * 2. 注册统一的异常反解解码器 {@link FeignErrorDecoder}，反序列化服务间传递的异常错误码。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({Feign.class, ErrorDecoder.class})
public class FeignSpecAutoConfiguration {

    private static final long DEFAULT_PERIOD = 100L;
    private static final long DEFAULT_MAX_PERIOD = 1000L;
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    /**
     * 配置 Feign 指数退避重试器。
     *
     * <p>初次请求重试间隔 100 毫秒，最大重试退避间隔 1 秒（1000 毫秒），最多发起 3 次尝试（含主请求）。</p>
     *
     * @return 自定义的 Feign Retryer 实例
     */
    @Bean
    public Retryer feignRetryer() {
        return new Retryer.Default(DEFAULT_PERIOD, DEFAULT_MAX_PERIOD, DEFAULT_MAX_ATTEMPTS);
    }

    /**
     * 注册 Feign 服务间调用异常解析 Bean。
     *
     * @return 业务异常反解的 ErrorDecoder 实例
     */
    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return new FeignErrorDecoder();
    }
}
