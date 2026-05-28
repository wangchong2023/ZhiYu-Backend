package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "发送注册邮箱验证码请求")
public class SendRegisterCodeRequest {

    @NotBlank
    @Email
    @Schema(description = "邮箱", example = "user@example.com")
    private String email;

    @NotBlank
    @Schema(description = "验证码Token")
    private String captchaToken;

    @Schema(description = "验证码类型", example = "ALIYUN_SLIDE")
    private String captchaType;
}
