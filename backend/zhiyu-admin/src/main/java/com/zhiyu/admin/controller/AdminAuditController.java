package com.zhiyu.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.AdminOperationDto;
import com.zhiyu.admin.dto.IdentityChangeDto;
import com.zhiyu.admin.service.AdminAuditService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "管理后台-审计日志", description = "身份变更与管理操作审计")
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    @Operation(summary = "身份变更审计", description = "用户绑定/解绑第三方认证方式的操作记录")
    @GetMapping("/identity-changes")
    public ApiResponse<Page<IdentityChangeDto>> identityChanges(
            @RequestParam(defaultValue = "1") final int page,
            @RequestParam(defaultValue = "20") final int size,
            @RequestParam(required = false) final Long userId,
            @RequestParam(required = false) final String action,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime endTime) {
        return ApiResponse.success(adminAuditService.listIdentityChanges(
                page, size, userId, action, startTime, endTime));
    }

    @Operation(summary = "管理员操作审计", description = "管理员后台所有操作记录")
    @GetMapping("/admin-operations")
    public ApiResponse<Page<AdminOperationDto>> adminOperations(
            @RequestParam(defaultValue = "1") final int page,
            @RequestParam(defaultValue = "20") final int size,
            @RequestParam(required = false) final String username,
            @RequestParam(required = false) final String action,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime endTime) {
        return ApiResponse.success(adminAuditService.listAdminOperations(
                page, size, username, action, startTime, endTime));
    }
}
