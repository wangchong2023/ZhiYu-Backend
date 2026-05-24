package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.ConfigHistoryDto;
import com.zhiyu.admin.entity.ConfigHistory;
import com.zhiyu.admin.mapper.ConfigHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminConfigService {

    private final ConfigHistoryMapper configHistoryMapper;

    public Page<ConfigHistoryDto> getConfigHistory(final String groupId, final String dataId,
                                                    final int page, final int size) {
        var wrapper = new LambdaQueryWrapper<ConfigHistory>();
        if (groupId != null && !groupId.isBlank()) {
            wrapper.eq(ConfigHistory::getGroupId, groupId);
        }
        if (dataId != null && !dataId.isBlank()) {
            wrapper.eq(ConfigHistory::getDataId, dataId);
        }
        wrapper.orderByDesc(ConfigHistory::getVersion);

        Page<ConfigHistory> entityPage = configHistoryMapper.selectPage(new Page<>(page, size), wrapper);
        Page<ConfigHistoryDto> dtoPage = new Page<>(page, size, entityPage.getTotal());
        dtoPage.setRecords(entityPage.getRecords().stream()
                .map(h -> ConfigHistoryDto.builder()
                        .id(h.getId())
                        .groupId(h.getGroupId())
                        .dataId(h.getDataId())
                        .format(h.getFormat())
                        .version(h.getVersion())
                        .operatorId(h.getOperatorId())
                        .operatorType(h.getOperatorType())
                        .changeSummary(h.getChangeSummary())
                        .createdAt(h.getCreatedAt())
                        .build())
                .toList());
        return dtoPage;
    }
}
