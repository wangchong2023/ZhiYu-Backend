package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "发送短信验证码请求")
public class SendSmsRequest {

    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{9}$")
    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @NotBlank
    @Schema(description = "场景", example = "admin_login")
    private String scene;
}
