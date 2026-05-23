package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.converter.AdminConverter;
import com.zhiyu.admin.dto.LoginLogDto;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminLogService {

    private final AuthUserLogMapper authUserLogMapper;

    public Page<LoginLogDto> listLogs(final int page, final int size,
                                       final String username,
                                       final String type, final String result,
                                       final LocalDateTime startTime,
                                       final LocalDateTime endTime) {
        var wrapper = new LambdaQueryWrapper<AuthUserLog>();
        if (username != null && !username.isBlank()) {
            wrapper.like(AuthUserLog::getAuthUserLogUserDisplay, username);
        }
        if (type != null && !type.isBlank()) {
            wrapper.eq(AuthUserLog::getAuthUserLogType, type);
        }
        if (result != null && !result.isBlank()) {
            wrapper.eq(AuthUserLog::getAuthUserLogResult, result);
        }
        if (startTime != null) {
            wrapper.ge(AuthUserLog::getCreatedTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(AuthUserLog::getCreatedTime, endTime);
        }
        wrapper.orderByDesc(AuthUserLog::getCreatedTime);

        Page<AuthUserLog> entityPage = authUserLogMapper.selectPage(
                new Page<>(page, size), wrapper);
        Page<LoginLogDto> dtoPage = new Page<>(page, size, entityPage.getTotal());
        dtoPage.setRecords(entityPage.getRecords().stream()
                .map(AdminConverter.INSTANCE::toLogDto)
                .collect(Collectors.toList()));
        return dtoPage;
    }
}
