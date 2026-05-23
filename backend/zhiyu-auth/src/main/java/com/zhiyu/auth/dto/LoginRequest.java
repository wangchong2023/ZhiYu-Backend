package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @NotBlank
    @Schema(description = "用户名", example = "zhangsan")
    private String username;

    @NotBlank
    @Schema(description = "密码", example = "Abc12345")
    private String password;

    @Schema(description = "验证码Token（连续3次失败后必填）")
    private String captchaToken;

    @Schema(description = "验证码")
    private String captchaCode;
}
