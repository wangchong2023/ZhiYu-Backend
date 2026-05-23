package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "注册请求")
public class RegisterRequest {

    @NotBlank
    @Size(min = 4, max = 32)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅支持字母、数字、下划线")
    @Schema(description = "用户名", example = "zhangsan", minLength = 4, maxLength = 32)
    private String username;

    @NotBlank
    @Size(min = 8, max = 128)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
             message = "密码需包含大小写字母和数字")
    @Schema(description = "密码（8-128位，需含大小写字母+数字）", example = "Abc12345")
    private String password;

    @NotBlank
    @Email
    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @NotBlank
    @Schema(description = "验证码Token")
    private String captchaToken;

    @NotBlank
    @Schema(description = "验证码（4位字母数字）", example = "A3x9")
    private String captchaCode;
}
