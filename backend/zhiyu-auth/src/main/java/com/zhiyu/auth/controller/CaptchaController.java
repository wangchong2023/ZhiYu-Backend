package com.zhiyu.auth.controller;

import com.zhiyu.auth.dto.CaptchaResponse;
import com.zhiyu.auth.service.CaptchaService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "验证码", description = "图片验证码接口")
@RestController
@RequestMapping("/api/v1/auth/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    @Operation(summary = "获取验证码图片", description = "返回验证码图片（Base64）和 Token，Token 用于注册/登录时校验")
    @GetMapping("/image")
    public ApiResponse<CaptchaResponse> getCaptcha(
            @RequestParam(defaultValue = "zhiyu_login") final String sceneId) {
        return ApiResponse.success(captchaService.generate(sceneId));
    }
}
