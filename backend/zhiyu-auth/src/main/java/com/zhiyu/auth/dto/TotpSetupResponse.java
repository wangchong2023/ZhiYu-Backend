package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "TOTP 设置响应")
public class TotpSetupResponse {

    @Schema(description = "TOTP 密钥（Base32 编码）", example = "JBSWY3DPEHPK3PXP")
    private String secret;

    @Schema(description = "二维码 URI（otpauth:// 格式，用于生成二维码）")
    private String qrUri;
}
