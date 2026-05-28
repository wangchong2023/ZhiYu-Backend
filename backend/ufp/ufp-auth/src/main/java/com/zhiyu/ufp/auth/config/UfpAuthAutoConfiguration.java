/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: UfpAuthAutoConfiguration.java
 * 创建时间: 2026-05-28
 * 描述: UFP 统一平台认证服务 Starter 自动配置类。作为标准 Spring Boot Starter 的入口，
 * 负责扫描认证相关的 Bean，加载 JWT 属性，并自动配置多数据源动态路由切面。
 */
package com.zhiyu.ufp.auth.config;

import com.zhiyu.ufp.auth.jwt.JwtProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 类名: UfpAuthAutoConfiguration
 * 描述: 自动装配类，由 Spring Boot 3.x SPI 自动发现。
 * 对应引入配置文件: META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * 内置多数据源自动组配和 MyBatis-Plus Mapper 扫描定义。
 */
@Configuration
@ConditionalOnClass(DataSource.class)
@EnableConfigurationProperties(JwtProperties.class)
@ComponentScan(basePackages = "com.zhiyu.ufp.auth")
@MapperScan(basePackages = "com.zhiyu.ufp.auth.mapper")
public class UfpAuthAutoConfiguration {

    /**
     * 认证数据库的专属数据源 Key 标示
     */
    private static final String DS_KEY_UFP_AUTH = "ufp_auth";

    /**
     * 描述: 注册多数据源路由数据源。使用 @Primary 覆盖默认数据源，提供基于 AOP 切面的动态数据源切换拦截能力。
     * @param dataSource 外部传入的主数据源实例 (通常为业务数据源)
     * @return 代理包装后的 UfpRoutingDataSource 动态路由数据源实例
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(UfpRoutingDataSource.class)
    public UfpRoutingDataSource ufpRoutingDataSource(final DataSource dataSource) {
        // 关键步骤：在并发 Map 中绑定默认数据源和平台认证专属数据源。
        // 目前 Phase 1 共用单一物理库，在此注册路由条目以匹配切面数据源路由指令。
        return new UfpRoutingDataSource(dataSource,
                Map.of(DS_KEY_UFP_AUTH, dataSource));
    }
}

