package com.zhiyu.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.AdminUserDto;
import com.zhiyu.admin.dto.AssignRoleRequest;
import com.zhiyu.admin.dto.CreateAdminUserRequest;
import com.zhiyu.admin.dto.ResetPasswordRequest;
import com.zhiyu.admin.dto.RoleDto;
import com.zhiyu.admin.service.AdminRbacService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理后台-RBAC", description = "角色管理与管理员账号管理")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminRbacController {

    private final AdminRbacService adminRbacService;

    @Operation(summary = "角色列表")
    @GetMapping("/roles")
    public ApiResponse<List<RoleDto>> listRoles() {
        return ApiResponse.success(adminRbacService.listRoles());
    }

    @Operation(summary = "分配角色")
    @PostMapping("/admins/{userId}/roles")
    public ApiResponse<Void> assignRole(@PathVariable final Long userId,
                                         @Valid @RequestBody final AssignRoleRequest request) {
        adminRbacService.assignRole(userId, request.getRoleId());
        return ApiResponse.success(null);
    }

    @Operation(summary = "移除角色")
    @DeleteMapping("/admins/{userId}/roles/{roleId}")
    public ApiResponse<Void> removeRole(@PathVariable final Long userId,
                                         @PathVariable final Integer roleId) {
        adminRbacService.removeRole(userId, roleId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "管理员列表")
    @GetMapping("/admins")
    public ApiResponse<Page<AdminUserDto>> listAdmins(
            @RequestParam(defaultValue = "1") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        return ApiResponse.success(adminRbacService.listAdminUsers(page, size));
    }

    @Operation(summary = "创建管理员")
    @PostMapping("/admins")
    public ApiResponse<AdminUserDto> createAdmin(
            @Valid @RequestBody final CreateAdminUserRequest request) {
        return ApiResponse.success(adminRbacService.createAdminUser(request));
    }

    @Operation(summary = "重置管理员密码")
    @PostMapping("/admins/{userId}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable final Long userId,
                                            @Valid @RequestBody final ResetPasswordRequest request) {
        adminRbacService.resetAdminPassword(userId, request.getNewPassword());
        return ApiResponse.success(null);
    }
}
