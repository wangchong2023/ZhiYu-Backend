package com.zhiyu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @Schema(description = "统一账号（用户名/邮箱/手机号）", example = "zhangsan")
    private String account;

    @Schema(description = "用户名（密码登录）", example = "zhangsan")
    private String username;

    @Schema(description = "密码（密码登录）", example = "Abc12345")
    private String password;

    @Schema(description = "登录方式: password | sms_code", example = "password")
    private String grantType;

    @Schema(description = "手机号（短信登录）", example = "13800138000")
    private String phone;

    @Schema(description = "短信验证码（短信登录）")
    private String smsCode;

    @Schema(description = "验证码Token（连续3次失败后必填）")
    private String captchaToken;

    @Schema(description = "验证码类型", example = "ALIYUN_SLIDE")
    private String captchaType;

    @Schema(description = "验证码")
    private String captchaCode;

    @Schema(description = "隐私政策同意")
    private Boolean privacyConsent;
}
