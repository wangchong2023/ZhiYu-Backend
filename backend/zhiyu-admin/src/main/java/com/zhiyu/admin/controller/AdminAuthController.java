package com.zhiyu.admin.controller;

import com.zhiyu.admin.service.AdminAuthService;
import com.zhiyu.auth.dto.LoginRequest;
import com.zhiyu.auth.dto.LoginResponse;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台-认证", description = "管理员登录")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @Operation(summary = "管理员登录", description = "仅 scope=ADMIN 的用户可登录")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody final LoginRequest request) {
        return ApiResponse.success(adminAuthService.login(request));
    }
}
