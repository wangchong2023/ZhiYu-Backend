/**
 * 文件名: UfpAuthApplication.java
 * 描述: 认证微服务的主入口类，负责启动 Spring Boot 应用程序并配置组件扫描和 Mapper 扫描范围
 */
package com.zhiyu.ufp.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 类名: UfpAuthApplication
 * 描述: 认证服务启动器。组件扫描包括 ufp-common, ufp-auth, zhiyu-common, zhiyu-auth 模块。
 */
@SpringBootApplication(scanBasePackages = {
    "com.zhiyu.ufp.common",
    "com.zhiyu.ufp.auth",
    "com.zhiyu.common",
    "com.zhiyu.auth"
})
@MapperScan(basePackages = {
    "com.zhiyu.ufp.auth.mapper",
    "com.zhiyu.auth.mapper"
})
public class UfpAuthApplication {

    /**
     * 描述: 应用程序的入口 main 方法，通过 SpringApplication 启动容器
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(UfpAuthApplication.class, args);
    }
}

