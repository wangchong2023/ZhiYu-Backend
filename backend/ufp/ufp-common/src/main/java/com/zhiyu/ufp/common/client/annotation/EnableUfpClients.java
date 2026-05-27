/*
 * Copyright (c) 2026 ZhiYu Company. All rights reserved.
 */

package com.zhiyu.ufp.common.client.annotation;

import org.springframework.cloud.openfeign.EnableFeignClients;
import java.lang.annotation.*;

/**
 * 启用 UFP 微服务服务间 Feign 客户端的注解。
 *
 * <p>通过对 Spring Cloud OpenFeign 的包装，自动扫描并在容器中注册位于 {@code com.zhiyu.ufp.common.client} 包名下的二方 Feign 客户端。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableFeignClients(basePackages = "com.zhiyu.ufp.common.client")
public @interface EnableUfpClients {
}
