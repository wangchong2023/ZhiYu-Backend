/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: AdminAuditServiceTest.java
 * 创建时间: 2026-05-28
 * 描述: 后台审计服务 AdminAuditService 单元测试类，深度覆盖身份变更与管理操作。
 */
package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.AdminOperationDto;
import com.zhiyu.admin.dto.IdentityChangeDto;
import com.zhiyu.ufp.auth.entity.AuthOperationLog;
import com.zhiyu.ufp.auth.entity.AuthUserIdentity;
import com.zhiyu.ufp.auth.service.AuthOperationLogService;
import com.zhiyu.ufp.auth.service.AuthUserIdentityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 类名: AdminAuditServiceTest
 * 描述: 对身份第三方绑定审计流水与管理员后台操作日志的条件查询及物理转换进行 Happy Path 深度校验。
 */
@ExtendWith(MockitoExtension.class)
class AdminAuditServiceTest {

    @Mock
    private AuthUserIdentityService authUserIdentityService;

    @Mock
    private AuthOperationLogService authOperationLogService;

    @InjectMocks
    private AdminAuditService adminAuditService;

    /**
     * 描述: 测试查询用户第三方身份绑定审计记录，并验证带全参数（userId、startTime、endTime）时的 wrapper 构造和分页映射成功。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnIdentityChangesSuccessfullyWithAllFilters() {
        // 1. 构造 Mock 数据
        AuthUserIdentity identity = new AuthUserIdentity();
        identity.setAuthUserIdentityId(88L);
        identity.setAuthUserId(2002L);
        identity.setProvider("WECHAT");
        identity.setOpenid("wx-unionid-xyz");
        identity.setCreatedTime(LocalDateTime.now());

        Page<AuthUserIdentity> mockPage = new Page<>(1, 20);
        mockPage.setRecords(List.of(identity));

        when(authUserIdentityService.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mockPage);

        // 2. 执行调用，传全参数以全面覆盖 wrapper 内部的 if 条件分支
        LocalDateTime now = LocalDateTime.now();
        Page<IdentityChangeDto> result = adminAuditService.listIdentityChanges(
                1, 20, 2002L, "BIND", now.minusDays(1), now);

        // 3. 校验分页 DTO 映射与属性重组
        assertThat(result).isNotNull();
        assertThat(result.getRecords()).hasSize(1);
        
        IdentityChangeDto dto = result.getRecords().get(0);
        assertThat(dto.getIdentityType()).isEqualTo("WECHAT");
        assertThat(dto.getAction()).isEqualTo("BIND"); // 验证 hardcode 的 BIND

        verify(authUserIdentityService).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    /**
     * 描述: 测试查询管理员后台操作审计日志，并验证过滤条件齐全时的正确行为。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnAdminOperationsSuccessfullyWithFilters() {
        // 1. 构造 Mock 数据
        AuthOperationLog log = new AuthOperationLog();
        log.setLogId("777");
        log.setUserName("admin");
        log.setOperationType("UPDATE_USER");
        log.setRemoteIp("192.168.1.100");
        log.setLogTime(LocalDateTime.now());

        Page<AuthOperationLog> mockPage = new Page<>(1, 20);
        mockPage.setRecords(List.of(log));

        when(authOperationLogService.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mockPage);

        // 2. 执行调用，传入全参数以完美触发 wrapper 内部 if 属性条件
        LocalDateTime now = LocalDateTime.now();
        Page<AdminOperationDto> result = adminAuditService.listAdminOperations(
                1, 20, "admin", "UPDATE_USER", now.minusDays(2), now);

        // 3. 结果校验
        assertThat(result).isNotNull();
        assertThat(result.getRecords()).hasSize(1);
        
        AdminOperationDto dto = result.getRecords().get(0);
        assertThat(dto.getUsername()).isEqualTo("admin");
        assertThat(dto.getAction()).isEqualTo("UPDATE_USER");

        verify(authOperationLogService).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }
}
