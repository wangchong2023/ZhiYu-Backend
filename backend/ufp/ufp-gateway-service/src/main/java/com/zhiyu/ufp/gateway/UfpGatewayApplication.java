package com.zhiyu.ufp.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 统一网关服务启动入口类。
 *
 * <p>基于 Spring Boot 和 Spring Cloud Discovery 实现统一路由网关服务。
 * 针对本类的唯一方法为静态 main 方法，PMD 工具可能误报并建议将其重构为 Utility 静态工具类。
 * 因其为核心 Spring 框架生命周期启动类，使用 {@code @SuppressWarnings("PMD.UseUtilityClass")} 消除该误报。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@SuppressWarnings("PMD.UseUtilityClass")
public class UfpGatewayApplication {

    public static void main(final String[] args) {
        SpringApplication.run(UfpGatewayApplication.class, args);
    }
}
