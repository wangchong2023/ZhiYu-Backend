package com.zhiyu.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.zhiyu")
@MapperScan({"com.zhiyu.admin.mapper", "com.zhiyu.auth.mapper",
        "com.zhiyu.notification.mapper", "com.zhiyu.subscription.mapper"})
public final class ZhiYuApplication {

    private ZhiYuApplication() {
    }

    public static void main(final String[] args) {
        SpringApplication.run(ZhiYuApplication.class, args);
    }
}
