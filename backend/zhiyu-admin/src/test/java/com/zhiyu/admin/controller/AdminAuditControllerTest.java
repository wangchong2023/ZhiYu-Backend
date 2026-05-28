/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: AdminAuditControllerTest.java
 * 创建时间: 2026-05-28
 * 描述: 后台审计控制器层 AdminAuditController 单元测试类。
 */
package com.zhiyu.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.AdminOperationDto;
import com.zhiyu.admin.dto.IdentityChangeDto;
import com.zhiyu.admin.service.AdminAuditService;
import com.zhiyu.common.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 类名: AdminAuditControllerTest
 * 描述: 测试后台第三方登录身份变更审计与管理员后台操作日志的 HTTP 映射端点和翻页返回响应。
 */
@ExtendWith(MockitoExtension.class)
class AdminAuditControllerTest {

    @Mock
    private AdminAuditService adminAuditService;

    @InjectMocks
    private AdminAuditController adminAuditController;

    /**
     * 描述: 测试身份变更审计接口。
     */
    @Test
    void shouldReturnIdentityChangesSuccessfully() {
        Page<IdentityChangeDto> mockPage = new Page<>(1, 20);
        LocalDateTime now = LocalDateTime.now();
        
        when(adminAuditService.listIdentityChanges(1, 20, 1001L, "BIND", now, now))
                .thenReturn(mockPage);

        ApiResponse<Page<IdentityChangeDto>> response = adminAuditController.identityChanges(
                1, 20, 1001L, "BIND", now, now);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isZero();
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isEqualTo(mockPage);

        verify(adminAuditService).listIdentityChanges(1, 20, 1001L, "BIND", now, now);
    }

    /**
     * 描述: 测试管理员操作审计接口。
     */
    @Test
    void shouldReturnAdminOperationsSuccessfully() {
        Page<AdminOperationDto> mockPage = new Page<>(1, 20);
        LocalDateTime now = LocalDateTime.now();

        when(adminAuditService.listAdminOperations(1, 20, "admin", "UPDATE", now, now))
                .thenReturn(mockPage);

        ApiResponse<Page<AdminOperationDto>> response = adminAuditController.adminOperations(
                1, 20, "admin", "UPDATE", now, now);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isZero();
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isEqualTo(mockPage);

        verify(adminAuditService).listAdminOperations(1, 20, "admin", "UPDATE", now, now);
    }
}
