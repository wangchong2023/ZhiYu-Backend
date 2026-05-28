/*
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: AdminVersionControllerTest.java
 * 创建时间: 2026-05-28
 * 描述: 版本控制器层 AdminVersionController 单元测试类。
 */
package com.zhiyu.admin.controller;

import com.zhiyu.admin.dto.VersionDto;
import com.zhiyu.admin.service.VersionService;
import com.zhiyu.common.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 类名: AdminVersionControllerTest
 * 描述: 测试 /api/v1/admin/version 接口能够成功响应并返回后端构建版本明细。
 */
@ExtendWith(MockitoExtension.class)
class AdminVersionControllerTest {

    @Mock
    private VersionService versionService;

    @InjectMocks
    private AdminVersionController adminVersionController;

    /**
     * 描述: 测试成功请求获取后台版本清单，验证 DTO 包装和字段传播是否正确。
     */
    @Test
    void shouldReturnVersionInfoSuccessfully() {
        // 1. 构造 Mock 传输数据
        VersionDto mockDto = VersionDto.builder()
                .services(List.of(
                        VersionDto.ServiceVersion.builder()
                                .name("ufp-gateway")
                                .version("1.0.0")
                                .commitId("commit-1111")
                                .buildTime("2026-05-28 10:00:00")
                                .build()
                ))
                .build();
        
        when(versionService.getVersion()).thenReturn(mockDto);

        // 2. 执行端点请求调用
        ApiResponse<VersionDto> response = adminVersionController.version();

        // 3. 结果校验与 Mock 动作复核
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isZero();
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isEqualTo(mockDto);
        assertThat(response.getData().getServices().get(0).getName()).isEqualTo("ufp-gateway");

        verify(versionService).getVersion();
    }
}
