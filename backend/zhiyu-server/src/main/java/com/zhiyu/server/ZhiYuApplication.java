/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: ZhiYuApplication.java
 * 创建时间: 2026-05-27
 * 描述: ZhiYu 聚合业务系统微服务入口启动类，负责装配 Spring Boot 容器与持久层 Mapper 扫描。
 */
package com.zhiyu.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 类名: ZhiYuApplication
 * 描述: 应用主启动类。使用 scanBasePackages 扫描业务层组件，配置 Feign 客户端支持以及 MyBatis 关联 Mapper 扫描。
 */
@SpringBootApplication(scanBasePackages = "com.zhiyu")
@EnableFeignClients(basePackages = "com.zhiyu")
@MapperScan({"com.zhiyu.admin.mapper", "com.zhiyu.auth.mapper",
        "com.zhiyu.notification.mapper", "com.zhiyu.subscription.mapper"})
public final class ZhiYuApplication {

    /**
     * 描述: 私有构造方法，防止外部实例化此工具启动类
     */
    private ZhiYuApplication() {
    }

    /**
     * 描述: 主启动入口 main 函数
     * @param args 命令行输入参数
     */
    public static void main(final String[] args) {
        // 运行 Spring Boot 应用程序引导
        SpringApplication.run(ZhiYuApplication.class, args);
    }
}

