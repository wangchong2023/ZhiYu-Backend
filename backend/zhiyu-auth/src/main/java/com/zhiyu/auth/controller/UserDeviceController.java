package com.zhiyu.auth.controller;

import com.zhiyu.auth.dto.DeviceDto;
import com.zhiyu.auth.service.DeviceService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "设备管理", description = "查看/踢出/信任已登录设备")
@RestController
@RequestMapping("/api/v1/auth/devices")
@SecurityRequirement(name = "Bearer")
@RequiredArgsConstructor
public class UserDeviceController {

    private static final String DEVICE_HEADER = "X-Device-Id";

    private final DeviceService deviceService;

    @Operation(summary = "设备列表", description = "返回当前用户所有已登录设备")
    @GetMapping
    public ApiResponse<List<DeviceDto>> list(
            @RequestHeader(value = DEVICE_HEADER, required = false) final String deviceId) {
        return ApiResponse.success(deviceService.listDevices(getCurrentUserId(), deviceId));
    }

    @Operation(summary = "踢出设备", description = "强制指定设备下线")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> kick(@PathVariable final Long id) {
        deviceService.kickDevice(getCurrentUserId(), id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "信任/取消信任设备", description = "切换设备信任状态（信任后该设备登录免 TOTP）")
    @PutMapping("/{id}/trust")
    public ApiResponse<Void> trust(@PathVariable final Long id) {
        deviceService.trustDevice(getCurrentUserId(), id);
        return ApiResponse.success(null);
    }

    private Long getCurrentUserId() {
        String sub = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return Long.parseLong(sub);
    }
}
