package com.zhiyu.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "WebAuthn 通行密钥")
public class WebAuthnCredentialDto {
    @Schema(description = "凭证ID") private String credentialId;
    @Schema(description = "设备名称") private String deviceName;
    @Schema(description = "注册时间") private LocalDateTime createdAt;
    @Schema(description = "最近使用时间") private LocalDateTime lastUsedTime;
}
