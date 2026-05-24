package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.ConfigHistoryDto;
import com.zhiyu.admin.entity.ConfigHistory;
import com.zhiyu.admin.mapper.ConfigHistoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminConfigServiceTest {

    @Mock private ConfigHistoryMapper configHistoryMapper;
    @InjectMocks private AdminConfigService adminConfigService;

    @Test
    void shouldGetConfigHistory() {
        ConfigHistory history = ConfigHistory.builder()
                .id(1L).groupId("DEFAULT_GROUP").dataId("application.yml")
                .format("YAML").version(3).operatorType("ADMIN")
                .changeSummary("Updated rate limit").createdAt(LocalDateTime.now()).build();
        when(configHistoryMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<ConfigHistory>(1, 10, 1).setRecords(List.of(history)));

        Page<ConfigHistoryDto> result = adminConfigService.getConfigHistory(
                "DEFAULT_GROUP", null, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getGroupId()).isEqualTo("DEFAULT_GROUP");
        assertThat(result.getRecords().get(0).getVersion()).isEqualTo(3);
    }

    @Test
    void shouldFilterByGroupAndDataId() {
        when(configHistoryMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<ConfigHistory>(1, 10, 0));

        Page<ConfigHistoryDto> result = adminConfigService.getConfigHistory(
                "DEFAULT_GROUP", "application.yml", 1, 10);

        assertThat(result.getTotal()).isZero();
    }
}
