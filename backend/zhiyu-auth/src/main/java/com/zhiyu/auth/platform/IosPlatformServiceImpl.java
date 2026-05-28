/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: IosPlatformServiceImpl.java
 * 创建时间: 2026-05-28
 * 描述: iOS 移动端设备平台适配策略具体实现类，用于定制化 APNs 消息推送及免二次验证标识管理。
 */
package com.zhiyu.auth.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 类名: IosPlatformServiceImpl
 * 描述: 处理 iOS 设备特定行为的策略类。
 */
@Slf4j
@Service
public class IosPlatformServiceImpl implements ApplePlatformService {

    /**
     * iOS 平台物理标示
     */
    private static final String PLATFORM_IOS = "IOS";

    @Override
    public String getPlatform() {
        return PLATFORM_IOS;
    }

    /**
     * 描述: 执行 iOS 平台的定制行为（如基于 Apple Push Notification Service 封装推送）
     * @param deviceName 设备物理名称
     * @param payload 携带的动态行为数据负载
     * @return 执行状态结果
     */
    @Override
    public String processPlatformBehavior(final String deviceName, final String payload) {
        if (log.isInfoEnabled()) {
            log.info("Processing iOS platform behavior for [{}], sending push with payload: {}", deviceName, payload);
        }
        // 模拟调用 APNs 服务发送定制通知
        return "iOS_Success";
    }
}
