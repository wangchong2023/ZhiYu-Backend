package com.zhiyu.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.LoginLogDto;
import com.zhiyu.admin.service.AdminLogService;
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

@Tag(name = "管理后台-登录日志", description = "登录行为审计")
@RestController
@RequestMapping("/api/v1/admin/logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminLogController {

    private final AdminLogService adminLogService;

    @Operation(summary = "登录日志", description = "分页查询，支持多条件筛选")
    @GetMapping("/login")
    public ApiResponse<Page<LoginLogDto>> list(
            @RequestParam(defaultValue = "1") final int page,
            @RequestParam(defaultValue = "20") final int size,
            @RequestParam(required = false) final String username,
            @RequestParam(required = false) final String type,
            @RequestParam(required = false) final String result,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime endTime) {
        return ApiResponse.success(adminLogService.listLogs(
                page, size, username, type, result, startTime, endTime));
    }

    @Operation(summary = "安全日志", description = "登录/注销/验证码/限流事件")
    @GetMapping("/security")
    public ApiResponse<Page<LoginLogDto>> securityLogs(
            @RequestParam(defaultValue = "1") final int page,
            @RequestParam(defaultValue = "20") final int size,
            @RequestParam(required = false) final String type,
            @RequestParam(required = false) final String ip,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final LocalDateTime endTime) {
        return ApiResponse.success(adminLogService.listSecurityLogs(
                page, size, type, ip, startTime, endTime));
    }
}
