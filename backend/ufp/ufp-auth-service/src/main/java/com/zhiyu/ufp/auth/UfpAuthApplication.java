package com.zhiyu.ufp.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 类名: UfpAuthApplication
 * 描述: 认证服务启动器。专职处理核心平台认证逻辑，
 * 仅扫描平台级的 com.zhiyu.ufp.common 和 com.zhiyu.ufp.auth 依赖包，实现完全的去业务化纯净化部署。
 */
@SpringBootApplication(scanBasePackages = {
    "com.zhiyu.ufp.common",
    "com.zhiyu.ufp.auth"
})
@MapperScan(basePackages = {
    "com.zhiyu.ufp.auth.mapper"
})
public final class UfpAuthApplication {

    /**
     * 描述: 私有构造函数，防止被误实例化
     */
    private UfpAuthApplication() {
        // 防止实例化
    }

    /**
     * 描述: 应用程序的入口 main 方法，通过 SpringApplication 启动容器
     * @param args 命令行参数
     */
    public static void main(final String[] args) {
        SpringApplication.run(UfpAuthApplication.class, args);
    }
}

