package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "TOTP 二次验证请求（登录流程）")
public class TotpVerifyRequest {

    @NotBlank
    @Schema(description = "TOTP 验证码（6位数字）", example = "123456")
    private String code;
}
