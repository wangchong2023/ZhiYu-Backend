/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: AuthUserDevice.java
 * 创建时间: 2026-05-28
 * 描述: 平台用户绑定设备实体类，用于对用户登录的硬件设备（iOS, macOS, Android, Web）进行受信任状态管理和行为审计。
 */
package com.zhiyu.ufp.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 类名: AuthUserDevice
 * 描述: 用户认证关联设备实体，映射数据库表 `auth_user_device`。
 * 该实体主要用于多设备管理、可信二次验证（TOTP 免验证设备标识）以及审计日志分析。
 */
@Data
@TableName("auth_user_device")
public class AuthUserDevice {

    /**
     * 主键 ID，自增唯一标识
     */
    @TableId(type = IdType.AUTO)
    private Long authUserDeviceId;

    /**
     * 关联的认证用户 ID (对应 auth_user 表主键)
     */
    @TableField("auth_user_id")
    private Long authUserId;

    /**
     * 客户端硬件/浏览器生成的设备唯一标识字符串码
     */
    @TableField("device_id")
    private String deviceId;

    /**
     * 客户端设备名称（如 "iPhone 15 Pro", "Chrome Browser" 等）
     */
    @TableField("device_name")
    private String deviceName;

    /**
     * 运行平台分类，可选值: IOS, ANDROID, MACOS, WEB 等
     */
    @TableField("platform")
    private String platform;

    /**
     * 是否将此设备标记为 TOTP 二次验证的可信设备。0: 否, 1: 是。
     * 标记为 1 后，该设备登录时可跳过双因子验证。
     */
    @TableField("trusted_for_totp")
    private Integer trustedForTotp;

    /**
     * 设备最后一次活跃于系统的时间
     */
    @TableField("last_active_at")
    private LocalDateTime lastActiveAt;

    /**
     * 记录创建并绑定的时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;
}

