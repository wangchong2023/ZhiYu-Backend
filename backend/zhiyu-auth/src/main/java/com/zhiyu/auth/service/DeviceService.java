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

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final int MAX_DEVICES = 5;

    private final AuthUserDeviceMapper deviceMapper;

    public List<DeviceDto> listDevices(final Long userId, final String currentDeviceId) {
        List<AuthUserDevice> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<AuthUserDevice>()
                        .eq(AuthUserDevice::getAuthUserId, userId)
                        .orderByDesc(AuthUserDevice::getLastActiveAt));

        return devices.stream()
                .map(d -> toDto(d, currentDeviceId))
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void kickDevice(final Long userId, final Long deviceRecordId) {
        AuthUserDevice device = deviceMapper.selectById(deviceRecordId);
        if (device == null || !device.getAuthUserId().equals(userId)) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        deviceMapper.deleteById(deviceRecordId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void trustDevice(final Long userId, final Long deviceRecordId) {
        AuthUserDevice device = deviceMapper.selectById(deviceRecordId);
        if (device == null || !device.getAuthUserId().equals(userId)) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        device.setTrustedForTotp(device.getTrustedForTotp() != null
                && device.getTrustedForTotp() == 1 ? 0 : 1);
        deviceMapper.updateById(device);
    }

    private DeviceDto toDto(final AuthUserDevice d, final String currentDeviceId) {
        return DeviceDto.builder()
                .id(d.getAuthUserDeviceId())
                .deviceId(d.getDeviceId())
                .deviceName(d.getDeviceName())
                .platform(d.getPlatform())
                .trusted(d.getTrustedForTotp() != null && d.getTrustedForTotp() == 1)
                .lastActiveAt(d.getLastActiveAt())
                .current(currentDeviceId != null && currentDeviceId.equals(d.getDeviceId()))
                .build();
    }
}
