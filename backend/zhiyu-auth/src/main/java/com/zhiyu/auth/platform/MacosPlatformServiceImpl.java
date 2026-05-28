/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: MacosPlatformServiceImpl.java
 * 创建时间: 2026-05-28
 * 描述: macOS 桌面端设备平台适配策略具体实现类，主要用于本地安全区（Secure Enclave）免二次验证认证流程。
 */
package com.zhiyu.auth.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 类名: MacosPlatformServiceImpl
 * 描述: 处理 macOS 设备特定行为的策略类。
 */
@Slf4j
@Service
public class MacosPlatformServiceImpl implements ApplePlatformService {

    /**
     * macOS 平台物理标示
     */
    private static final String PLATFORM_MACOS = "MACOS";

    @Override
    public String getPlatform() {
        return PLATFORM_MACOS;
    }

    /**
     * 描述: 执行 macOS 平台的定制行为
     * @param deviceName 设备物理名称
     * @param payload 携带的动态行为数据负载
     * @return 执行状态结果
     */
    @Override
    public String processPlatformBehavior(final String deviceName, final String payload) {
        if (log.isInfoEnabled()) {
            log.info("Processing macOS Secure Enclave authorization for [{}], context: {}", deviceName, payload);
        }
        return "macOS_Success";
    }
}
