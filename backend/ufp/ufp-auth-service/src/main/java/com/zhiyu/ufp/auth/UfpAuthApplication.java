package com.zhiyu.ufp.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ufp-auth-service — 认证微服务独立入口.
 * <p>
 * 组件扫描范围: ufp-common, ufp-auth, zhiyu-common, zhiyu-auth.
 * 不包含 zhiyu-admin / zhiyu-user / zhiyu-notification / zhiyu-subscription.
 * </p>
 */
@SpringBootApplication(scanBasePackages = {
    "com.zhiyu.ufp.common",
    "com.zhiyu.ufp.auth",
    "com.zhiyu.common",
    "com.zhiyu.auth"
})
public class UfpAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(UfpAuthApplication.class, args);
    }
}
