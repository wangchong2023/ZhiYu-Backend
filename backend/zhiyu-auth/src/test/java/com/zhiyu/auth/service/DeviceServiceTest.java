package com.zhiyu.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyu.auth.dto.DeviceDto;
import com.zhiyu.ufp.auth.entity.AuthUserDevice;
import com.zhiyu.ufp.auth.mapper.AuthUserDeviceMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private AuthUserDeviceMapper deviceMapper;

    @InjectMocks
    private DeviceService deviceService;

    // ── listDevices ───────────────────────────────────────────

    @Test
    void shouldReturnEmptyListWhenNoDevices() {
        when(deviceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        List<DeviceDto> devices = deviceService.listDevices(1001L, "device-1");

        assertThat(devices).isEmpty();
    }

    @Test
    void shouldReturnDeviceList() {
        AuthUserDevice device = new AuthUserDevice();
        device.setAuthUserDeviceId(1L);
        device.setAuthUserId(1001L);
        device.setDeviceId("device-uuid-1");
        device.setDeviceName("iPhone 15");
        device.setPlatform("IOS");
        device.setTrustedForTotp(1);
        device.setLastActiveAt(LocalDateTime.of(2025, 6, 1, 12, 0));

        when(deviceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(device));

        List<DeviceDto> devices = deviceService.listDevices(1001L, "other-device");

        assertThat(devices).hasSize(1);
        DeviceDto dto = devices.get(0);
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getDeviceId()).isEqualTo("device-uuid-1");
        assertThat(dto.getDeviceName()).isEqualTo("iPhone 15");
        assertThat(dto.getPlatform()).isEqualTo("IOS");
        assertThat(dto.isTrusted()).isTrue();
        assertThat(dto.getLastActiveAt()).isEqualTo(LocalDateTime.of(2025, 6, 1, 12, 0));
        assertThat(dto.isCurrent()).isFalse();
    }

    @Test
    void shouldMarkCurrentDevice() {
        AuthUserDevice device = new AuthUserDevice();
        device.setAuthUserDeviceId(1L);
        device.setAuthUserId(1001L);
        device.setDeviceId("current-device-id");
        device.setDeviceName("MacBook Pro");
        device.setPlatform("MACOS");
        device.setTrustedForTotp(0);
        device.setLastActiveAt(LocalDateTime.now());

        when(deviceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(device));

        List<DeviceDto> devices = deviceService.listDevices(1001L, "current-device-id");

        assertThat(devices).hasSize(1);
        assertThat(devices.get(0).isCurrent()).isTrue();
    }

    @Test
    void shouldHandleNullCurrentDeviceId() {
        AuthUserDevice device = new AuthUserDevice();
        device.setAuthUserDeviceId(1L);
        device.setAuthUserId(1001L);
        device.setDeviceId("device-1");
        device.setDeviceName("Test Device");
        device.setPlatform("WEB");
        device.setTrustedForTotp(null);
        device.setLastActiveAt(LocalDateTime.now());

        when(deviceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(device));

        List<DeviceDto> devices = deviceService.listDevices(1001L, null);

        assertThat(devices).hasSize(1);
        assertThat(devices.get(0).isCurrent()).isFalse();
        assertThat(devices.get(0).isTrusted()).isFalse();
    }

    @Test
    void shouldMapUntrustedDeviceCorrectly() {
        AuthUserDevice device = new AuthUserDevice();
        device.setAuthUserDeviceId(2L);
        device.setAuthUserId(1001L);
        device.setDeviceId("device-2");
        device.setDeviceName("Android Phone");
        device.setPlatform("ANDROID");
        device.setTrustedForTotp(0);
        device.setLastActiveAt(LocalDateTime.now());

        when(deviceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(device));

        List<DeviceDto> devices = deviceService.listDevices(1001L, "device-1");

        assertThat(devices).hasSize(1);
        assertThat(devices.get(0).isTrusted()).isFalse();
    }

    // ── kickDevice ────────────────────────────────────────────

    @Test
    void shouldKickDeviceSuccessfully() {
        AuthUserDevice device = new AuthUserDevice();
        device.setAuthUserDeviceId(10L);
        device.setAuthUserId(1001L);
        device.setDeviceId("kick-me");

        when(deviceMapper.selectById(10L)).thenReturn(device);

        deviceService.kickDevice(1001L, 10L);

        verify(deviceMapper).deleteById(10L);
    }

    @Test
    void shouldThrowWhenKickingNonexistentDevice() {
        when(deviceMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> deviceService.kickDevice(1001L, 999L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(BizErrorCode.RESOURCE_NOT_FOUND.getMessage());
    }

    @Test
    void shouldThrowWhenKickingOtherUsersDevice() {
        AuthUserDevice device = new AuthUserDevice();
        device.setAuthUserDeviceId(10L);
        device.setAuthUserId(2002L); // different user

        when(deviceMapper.selectById(10L)).thenReturn(device);

        assertThatThrownBy(() -> deviceService.kickDevice(1001L, 10L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(BizErrorCode.RESOURCE_NOT_FOUND.getMessage());

        verify(deviceMapper, never()).deleteById(10L);
    }

    // ── trustDevice ───────────────────────────────────────────

    @Test
    void shouldToggleTrustFromUntrustedToTrusted() {
        AuthUserDevice device = new AuthUserDevice();
        device.setAuthUserDeviceId(10L);
        device.setAuthUserId(1001L);
        device.setTrustedForTotp(0);

        when(deviceMapper.selectById(10L)).thenReturn(device);

        deviceService.trustDevice(1001L, 10L);

        assertThat(device.getTrustedForTotp()).isEqualTo(1);
        verify(deviceMapper).updateById(device);
    }

    @Test
    void shouldToggleTrustFromTrustedToUntrusted() {
        AuthUserDevice device = new AuthUserDevice();
        device.setAuthUserDeviceId(10L);
        device.setAuthUserId(1001L);
        device.setTrustedForTotp(1);

        when(deviceMapper.selectById(10L)).thenReturn(device);

        deviceService.trustDevice(1001L, 10L);

        assertThat(device.getTrustedForTotp()).isEqualTo(0);
        verify(deviceMapper).updateById(device);
    }

    @Test
    void shouldSetTrustedToOneWhenNull() {
        AuthUserDevice device = new AuthUserDevice();
        device.setAuthUserDeviceId(10L);
        device.setAuthUserId(1001L);
        device.setTrustedForTotp(null);

        when(deviceMapper.selectById(10L)).thenReturn(device);

        deviceService.trustDevice(1001L, 10L);

        // null != null is false, and null == 1 is false → goes to else: set to 1
        assertThat(device.getTrustedForTotp()).isEqualTo(1);
        verify(deviceMapper).updateById(device);
    }

    @Test
    void shouldThrowWhenTrustingNonexistentDevice() {
        when(deviceMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> deviceService.trustDevice(1001L, 999L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(BizErrorCode.RESOURCE_NOT_FOUND.getMessage());
    }

    @Test
    void shouldThrowWhenTrustingOtherUsersDevice() {
        AuthUserDevice device = new AuthUserDevice();
        device.setAuthUserDeviceId(10L);
        device.setAuthUserId(2002L);

        when(deviceMapper.selectById(10L)).thenReturn(device);

        assertThatThrownBy(() -> deviceService.trustDevice(1001L, 10L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(BizErrorCode.RESOURCE_NOT_FOUND.getMessage());

        verify(deviceMapper, never()).updateById(device);
    }
}
