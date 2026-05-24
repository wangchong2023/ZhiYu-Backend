package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "用户设备信息")
public class DeviceDto {

    @Schema(description = "设备记录ID", example = "1001")
    private Long id;

    @Schema(description = "设备UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String deviceId;

    @Schema(description = "设备名称", example = "iPhone 15 Pro")
    private String deviceName;

    @Schema(description = "平台", example = "IOS")
    private String platform;

    @Schema(description = "是否信任（免 TOTP 验证）")
    private boolean trusted;

    @Schema(description = "最后活跃时间")
    private LocalDateTime lastActiveAt;

    @Schema(description = "是否为当前设备")
    private boolean current;
}
