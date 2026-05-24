package com.zhiyu.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.ConfigHistoryDto;
import com.zhiyu.admin.service.AdminConfigService;
import com.zhiyu.common.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminConfigControllerTest {

    @Mock private AdminConfigService adminConfigService;
    @InjectMocks private AdminConfigController adminConfigController;

    @Test
    void shouldGetConfigHistory() {
        Page<ConfigHistoryDto> page = new Page<>(1, 20, 0);
        when(adminConfigService.getConfigHistory("DEFAULT_GROUP", null, 1, 20)).thenReturn(page);

        ApiResponse<Page<ConfigHistoryDto>> resp = adminConfigController.getConfigHistory(
                "DEFAULT_GROUP", null, 1, 20);

        assertThat(resp.getData()).isNotNull();
    }

    @Test
    void shouldGetConfigHistoryWithDefaults() {
        Page<ConfigHistoryDto> page = new Page<>(1, 20, 0);
        when(adminConfigService.getConfigHistory(null, null, 1, 20)).thenReturn(page);

        ApiResponse<Page<ConfigHistoryDto>> resp = adminConfigController.getConfigHistory(
                null, null, 1, 20);

        assertThat(resp.getData()).isNotNull();
    }
}
