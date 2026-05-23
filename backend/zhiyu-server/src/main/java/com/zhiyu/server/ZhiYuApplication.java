package com.zhiyu.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.zhiyu")
public class ZhiYuApplication {
    public static void main(final String[] args) {
        SpringApplication.run(ZhiYuApplication.class, args);
    }
}
