package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.converter.AdminConverter;
import com.zhiyu.admin.dto.AccessLogDto;
import com.zhiyu.admin.dto.AppLogDto;
import com.zhiyu.admin.dto.LoginLogDto;
import com.zhiyu.admin.dto.SlowQueryDto;
import com.zhiyu.admin.entity.AppLog;
import com.zhiyu.admin.mapper.AppLogMapper;
import com.zhiyu.common.service.GenericService;
import com.zhiyu.ufp.auth.entity.AuthOperationLog;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.service.AuthOperationLogService;
import com.zhiyu.ufp.auth.service.AuthUserLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class AdminLogService {

    private final AuthUserLogService authUserLogService;
    private final AppLogMapper appLogMapper;
    private final AuthOperationLogService authOperationLogService;

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

        Page<AuthUserLog> entityPage = authUserLogService.selectPage(
                new Page<>(page, size), wrapper);
        return GenericService.pageDto(entityPage, AdminConverter.INSTANCE::toLogDto);
    }

    public Page<LoginLogDto> listSecurityLogs(final int page, final int size,
                                              final String type, final String ip,
                                              final LocalDateTime startTime,
                                              final LocalDateTime endTime) {
        var wrapper = new LambdaQueryWrapper<AuthUserLog>();
        wrapper.in(AuthUserLog::getAuthUserLogAction, "LOGIN", "LOGOUT", "CAPTCHA", "RATE_LIMIT");
        if (type != null && !type.isBlank()) {
            wrapper.eq(AuthUserLog::getAuthUserLogAction, type);
        }
        if (ip != null && !ip.isBlank()) {
            wrapper.eq(AuthUserLog::getAuthUserLogIp, ip);
        }
        if (startTime != null) {
            wrapper.ge(AuthUserLog::getCreatedTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(AuthUserLog::getCreatedTime, endTime);
        }
        wrapper.orderByDesc(AuthUserLog::getCreatedTime);

        Page<AuthUserLog> entityPage = authUserLogService.selectPage(
                new Page<>(page, size), wrapper);
        return GenericService.pageDto(entityPage, AdminConverter.INSTANCE::toLogDto);
    }

    public Page<AppLogDto> listAppLogs(final int page, final int size,
                                        final String level, final String module,
                                        final String keyword,
                                        final LocalDateTime startTime,
                                        final LocalDateTime endTime) {
        var wrapper = new LambdaQueryWrapper<AppLog>();
        if (level != null && !level.isBlank()) {
            wrapper.eq(AppLog::getLevel, level.toUpperCase());
        }
        if (module != null && !module.isBlank()) {
            wrapper.eq(AppLog::getModule, module);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(AppLog::getMessage, keyword);
        }
        if (startTime != null) {
            wrapper.ge(AppLog::getCreatedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(AppLog::getCreatedAt, endTime);
        }
        wrapper.orderByDesc(AppLog::getCreatedAt);

        Page<AppLog> entityPage = appLogMapper.selectPage(new Page<>(page, size), wrapper);
        return GenericService.pageDto(entityPage, AdminConverter.INSTANCE::toAppLogDto);
    }

    public Page<AccessLogDto> listAccessLogs(final int page, final int size,
                                              final String method,
                                              final String path, final String ip,
                                              final LocalDateTime startTime,
                                              final LocalDateTime endTime) {
        var wrapper = new LambdaQueryWrapper<AuthOperationLog>();
        if (method != null && !method.isBlank()) {
            wrapper.eq(AuthOperationLog::getMethod, method.toUpperCase());
        }
        if (path != null && !path.isBlank()) {
            wrapper.like(AuthOperationLog::getUri, path);
        }
        if (ip != null && !ip.isBlank()) {
            wrapper.eq(AuthOperationLog::getRemoteIp, ip);
        }
        if (startTime != null) {
            wrapper.ge(AuthOperationLog::getLogTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(AuthOperationLog::getLogTime, endTime);
        }
        wrapper.orderByDesc(AuthOperationLog::getLogTime);

        Page<AuthOperationLog> entityPage =
                authOperationLogService.selectPage(new Page<>(page, size), wrapper);
        return GenericService.pageDto(entityPage, AdminConverter.INSTANCE::toAccessLogDto);
    }

    public Page<SlowQueryDto> listSlowQueries(final int page, final int size) {
        Page<SlowQueryDto> dtoPage = new Page<>(page, size, 0);
        dtoPage.setRecords(Collections.emptyList());
        return dtoPage;
    }
}
