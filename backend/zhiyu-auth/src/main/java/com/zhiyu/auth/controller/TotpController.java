package com.zhiyu.auth.controller;

import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.auth.dto.TotpEnableRequest;
import com.zhiyu.auth.dto.TotpSetupResponse;
import com.zhiyu.auth.dto.TotpVerifyRequest;
import com.zhiyu.auth.service.AuthService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "TOTP 双因素认证", description = "TOTP 设置、启用、禁用与二次验证")
@RestController
@RequestMapping("/api/v1/auth/totp")
@RequiredArgsConstructor
public class TotpController {

    private final AuthService authService;

    @Operation(summary = "设置 TOTP", description = "生成 TOTP 密钥与二维码 URI，返回供用户扫码绑定")
    @SecurityRequirement(name = "Bearer")
    @PostMapping("/setup")
    public ApiResponse<TotpSetupResponse> setup() {
        return ApiResponse.success(authService.setupTotp(getCurrentUserId()));
    }

    @Operation(summary = "启用 TOTP", description = "验证 TOTP 验证码后启用双因素认证")
    @SecurityRequirement(name = "Bearer")
    @PostMapping("/enable")
    public ApiResponse<Void> enable(@Valid @RequestBody final TotpEnableRequest request) {
        authService.enableTotp(getCurrentUserId(), request.getCode());
        return ApiResponse.success(null);
    }

    @Operation(summary = "禁用 TOTP", description = "删除 TOTP 配置，关闭双因素认证")
    @SecurityRequirement(name = "Bearer")
    @DeleteMapping
    public ApiResponse<Void> disable() {
        authService.disableTotp(getCurrentUserId());
        return ApiResponse.success(null);
    }

    @Operation(summary = "TOTP 二次验证", description = "登录流程中提交 TOTP 验证码，换取完整 Token 对")
    @SecurityRequirement(name = "Bearer")
    @PostMapping("/verify")
    public ApiResponse<LoginResponse> verify(@Valid @RequestBody final TotpVerifyRequest request) {
        return ApiResponse.success(authService.verifyTotpLogin(getCurrentUserId(), request.getCode()));
    }

    private Long getCurrentUserId() {
        String sub = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return Long.parseLong(sub);
    }
}
