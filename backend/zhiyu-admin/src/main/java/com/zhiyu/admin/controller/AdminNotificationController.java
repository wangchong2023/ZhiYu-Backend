package com.zhiyu.admin.controller;

import com.zhiyu.admin.dto.NotificationTemplateDto;
import com.zhiyu.admin.dto.UpdateTemplateRequest;
import com.zhiyu.admin.service.AdminNotificationService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理后台-通知模板", description = "通知模板管理")
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;

    @Operation(summary = "模板列表")
    @GetMapping
    public ApiResponse<List<NotificationTemplateDto>> listTemplates() {
        return ApiResponse.success(adminNotificationService.listTemplates());
    }

    @Operation(summary = "模板详情")
    @GetMapping("/{id}")
    public ApiResponse<NotificationTemplateDto> getTemplate(@PathVariable final Long id) {
        return ApiResponse.success(adminNotificationService.getTemplate(id));
    }

    @Operation(summary = "更新模板")
    @PutMapping("/{id}")
    public ApiResponse<Void> updateTemplate(@PathVariable final Long id,
                                             @Valid @RequestBody final UpdateTemplateRequest request) {
        adminNotificationService.updateTemplate(id, request);
        return ApiResponse.success(null);
    }
}
