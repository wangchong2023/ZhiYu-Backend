/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: ApplePlatformServiceFactory.java
 * 创建时间: 2026-05-28
 * 描述: Apple 多平台行为适配策略工厂，利用 Spring 的 IoC 容器特性，自动发现所有平台策略实现类并进行动态注册装配。
 */
package com.zhiyu.auth.platform;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 类名: ApplePlatformServiceFactory
 * 描述: 平台适配器的策略工厂。作为统一调度入口，在运行时通过 platform 标识动态分发至对应的平台策略执行器。
 */
@Component
public class ApplePlatformServiceFactory {

    /**
     * 策略实例路由映射表，Key 为大写的平台名称标示，Value 为对应的策略实例
     */
    private final Map<String, ApplePlatformService> strategyMap = new ConcurrentHashMap<>();

    /**
     * 描述: 构造器注入所有声明的平台策略实现，并自动映射到 strategyMap 中
     * @param services 系统中所有实现了 ApplePlatformService 接口的 Bean 集合
     */
    public ApplePlatformServiceFactory(final List<ApplePlatformService> services) {
        if (services != null) {
            for (ApplePlatformService service : services) {
                strategyMap.put(service.getPlatform().toUpperCase(Locale.ENGLISH), service);
            }
        }
    }

    /**
     * 描述: 根据平台名称标识，获取对应的具体适配策略实例。
     * 若未找到特定的适配实例，则返回默认兜底行为（Null Object Pattern 或者是降级保护）
     * @param platform 平台标识，如 "IOS", "MACOS" 等
     * @return 匹配的 ApplePlatformService 实例，不存在返回 null
     */
    public ApplePlatformService getService(final String platform) {
        if (platform == null) {
            return null;
        }
        return strategyMap.get(platform.toUpperCase(Locale.ENGLISH));
    }
}
