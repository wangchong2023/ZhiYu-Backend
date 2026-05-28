/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: ApplePlatformStrategyTest.java
 * 创建时间: 2026-05-28
 * 描述: Apple 多平台适配策略及策略工厂单元测试类。
 */
package com.zhiyu.auth.platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 类名: ApplePlatformStrategyTest
 * 描述: 测试 iOS、macOS 具体策略的业务分发以及策略工厂的注入与路由逻辑。
 */
public class ApplePlatformStrategyTest {

    private ApplePlatformServiceFactory factory;
    private ApplePlatformService iosService;
    private ApplePlatformService macosService;

    @BeforeEach
    public void setUp() {
        iosService = new IosPlatformServiceImpl();
        macosService = new MacosPlatformServiceImpl();
        factory = new ApplePlatformServiceFactory(List.of(iosService, macosService));
    }

    @Test
    public void testStrategyRouting() {
        // 测试 iOS 路由
        ApplePlatformService ios = factory.getService("IOS");
        assertNotNull(ios);
        assertEquals("IOS", ios.getPlatform());
        assertEquals("iOS_Success", ios.processPlatformBehavior("My iPhone", "APNs_Payload"));

        // 测试 macOS 路由
        ApplePlatformService macos = factory.getService("MACOS");
        assertNotNull(macos);
        assertEquals("MACOS", macos.getPlatform());
        assertEquals("macOS_Success", macos.processPlatformBehavior("Constantine's MacBook", "Enclave_Key"));

        // 测试未知平台路由返回 null
        ApplePlatformService android = factory.getService("ANDROID");
        assertNull(android);
    }
}
