package com.zhiyu.admin.controller;

import com.zhiyu.admin.dto.VersionDto;
import com.zhiyu.admin.service.VersionService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台-版本", description = "前后端版本信息")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminVersionController {

    private final VersionService versionService;

    @Operation(summary = "版本信息", description = "获取后端构建版本、Git 提交、构建时间")
    @GetMapping("/version")
    public ApiResponse<VersionDto> version() {
        return ApiResponse.success(versionService.getVersion());
    }
}
