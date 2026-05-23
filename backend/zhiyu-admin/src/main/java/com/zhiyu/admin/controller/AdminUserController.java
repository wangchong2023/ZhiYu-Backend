package com.zhiyu.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.AdminUserDetailDto;
import com.zhiyu.admin.dto.AdminUserDto;
import com.zhiyu.admin.service.AdminUserService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台-用户管理", description = "用户列表/详情/启停")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "用户列表", description = "分页查询，支持关键词搜索和状态筛选")
    @GetMapping
    public ApiResponse<Page<AdminUserDto>> list(
            @RequestParam(defaultValue = "1") final int page,
            @RequestParam(defaultValue = "20") final int size,
            @RequestParam(required = false) final String keyword,
            @RequestParam(required = false) final String status) {
        return ApiResponse.success(adminUserService.listUsers(page, size, keyword, status));
    }

    @Operation(summary = "用户详情", description = "含最近10条登录记录")
    @GetMapping("/{id}")
    public ApiResponse<AdminUserDetailDto> detail(@PathVariable final Long id) {
        return ApiResponse.success(adminUserService.getUserDetail(id));
    }

    @Operation(summary = "启用用户")
    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable final Long id) {
        adminUserService.enableUser(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "禁用用户")
    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable final Long id) {
        adminUserService.disableUser(id);
        return ApiResponse.success(null);
    }
}
