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
import org.springframework.web.bind.annotation.*;

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
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResponse.success(adminLogService.listLogs(page, size, username, type, result, startTime, endTime));
    }
}
