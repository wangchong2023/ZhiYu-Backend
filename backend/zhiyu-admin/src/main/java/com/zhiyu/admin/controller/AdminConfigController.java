package com.zhiyu.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.ConfigHistoryDto;
import com.zhiyu.admin.service.AdminConfigService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台-系统配置", description = "Nacos配置变更历史")
@RestController
@RequestMapping("/api/v1/admin/config")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminConfigController {

    private final AdminConfigService adminConfigService;

    @Operation(summary = "配置变更历史")
    @GetMapping("/history")
    public ApiResponse<Page<ConfigHistoryDto>> getConfigHistory(
            @RequestParam(required = false) final String groupId,
            @RequestParam(required = false) final String dataId,
            @RequestParam(defaultValue = "1") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        return ApiResponse.success(adminConfigService.getConfigHistory(groupId, dataId, page, size));
    }
}
