/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: ApplePlatformService.java
 * 创建时间: 2026-05-28
 * 描述: Apple 多平台行为适配策略接口。使用策略模式抹平 iOS、macOS、watchOS 等平台的物理差异。
 */
package com.zhiyu.auth.platform;

/**
 * 接口名: ApplePlatformService
 * 描述: Apple 专属平台的业务执行策略接口。针对推送、凭证校验等跨平台差异行为提供多态支持。
 */
public interface ApplePlatformService {

    /**
     * 描述: 获取当前策略实现的平台名称标识
     * @return 平台标示，如 "IOS", "MACOS" 等
     */
    String getPlatform();

    /**
     * 描述: 模拟执行特定平台的行为逻辑，例如定制化推送或免二次校验初始化。
     * @param deviceName 设备物理名称
     * @param payload 携带的动态行为数据负载
     * @return 执行后的响应状态
     */
    String processPlatformBehavior(String deviceName, String payload);
}
