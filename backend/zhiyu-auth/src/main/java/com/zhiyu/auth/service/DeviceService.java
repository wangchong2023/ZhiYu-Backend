/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: DeviceService.java
 * 创建时间: 2026-05-28
 * 描述: 设备管理业务服务类，负责用户多设备列表查询、踢出设备、切换设备TOTP二次验证免验证信任状态。
 */
package com.zhiyu.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.auth.dto.DeviceDto;
import com.zhiyu.ufp.auth.entity.AuthUserDevice;
import com.zhiyu.ufp.auth.mapper.AuthUserDeviceMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 类名: DeviceService
 * 描述: 用户设备绑定与多设备受信任状态管理的业务逻辑实现类。
 * 支持设备审计列举、单端强制踢出设备以及二次验证受信任标志切换。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    /**
     * 设备受信任状态常量：已受信任
     */
    public static final int DEVICE_TRUSTED = 1;

    /**
     * 设备受信任状态常量：未受信任
     */
    public static final int DEVICE_UNTRUSTED = 0;

    private final AuthUserDeviceMapper deviceMapper;

    /**
     * 描述: 列出指定用户绑定的所有硬件设备，并标识出哪一个是当前发起请求的活跃设备。
     * @param userId 用户的物理主键 ID
     * @param currentDeviceId 当前请求携带的设备标识码
     * @return 转换后的设备数据传输对象 (DeviceDto) 列表，按最后活跃时间降序排列
     */
    public List<DeviceDto> listDevices(final Long userId, final String currentDeviceId) {
        // 关键步骤 1：从数据库中查出该用户名下的所有绑定设备记录，按最近活跃时间倒序
        List<AuthUserDevice> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<AuthUserDevice>()
                        .eq(AuthUserDevice::getAuthUserId, userId)
                        .orderByDesc(AuthUserDevice::getLastActiveAt));

        // 关键步骤 2：使用 Stream 流将底层 Entity 转换为安全展示的 Resp DTO
        return devices.stream()
                .map(d -> toDto(d, currentDeviceId))
                .collect(Collectors.toList());
    }

    /**
     * 描述: 踢出（取消绑定）用户的指定设备，执行该操作后此设备将失去有效的会话标识并被迫退出。
     * @param userId 用户的物理主键 ID，用于越权校验
     * @param deviceRecordId 待踢出的设备物理记录主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void kickDevice(final Long userId, final Long deviceRecordId) {
        // 关键步骤 1：根据主键定位该设备记录
        AuthUserDevice device = deviceMapper.selectById(deviceRecordId);
        
        // 关键步骤 2：防越权防御性校验 — 若记录不存在或记录归属非当前用户，抛出资源未找到异常
        if (device == null || !device.getAuthUserId().equals(userId)) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        
        // 关键步骤 3：物理删除该设备关联，触发对应的强制下线流程
        deviceMapper.deleteById(deviceRecordId);
    }

    /**
     * 描述: 切换用户设备的免二次验证（TOTP）受信任状态。
     * @param userId 用户的物理主键 ID，用于越权校验
     * @param deviceRecordId 待修改的设备物理记录主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void trustDevice(final Long userId, final Long deviceRecordId) {
        // 关键步骤 1：查询指定设备实体
        AuthUserDevice device = deviceMapper.selectById(deviceRecordId);
        
        // 关键步骤 2：防越权防御性校验
        if (device == null || !device.getAuthUserId().equals(userId)) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        
        // 关键步骤 3：状态反转操作（若当前是信任则置为非信任，反之亦然）
        device.setTrustedForTotp(device.getTrustedForTotp() != null
                && device.getTrustedForTotp() == DEVICE_TRUSTED ? DEVICE_UNTRUSTED : DEVICE_TRUSTED);
        
        // 关键步骤 4：持久化更新至数据库
        deviceMapper.updateById(device);
    }

    /**
     * 描述: 将底层的领域数据库实体转换为暴露给外部视图的安全 DTO 传输对象。
     * @param d 底层设备实体数据对象
     * @param currentDeviceId 外部传入的当前请求活跃设备码，用于比对判定
     * @return 映射后的安全数据传输对象
     */
    private DeviceDto toDto(final AuthUserDevice d, final String currentDeviceId) {
        return DeviceDto.builder()
                .id(d.getAuthUserDeviceId())
                .deviceId(d.getDeviceId())
                .deviceName(d.getDeviceName())
                .platform(d.getPlatform())
                .trusted(d.getTrustedForTotp() != null && d.getTrustedForTotp() == DEVICE_TRUSTED)
                .lastActiveAt(d.getLastActiveAt())
                .current(currentDeviceId != null && currentDeviceId.equals(d.getDeviceId()))
                .build();
    }
}

