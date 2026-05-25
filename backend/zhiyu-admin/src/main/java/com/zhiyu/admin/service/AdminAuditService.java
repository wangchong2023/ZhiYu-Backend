package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.converter.AdminConverter;
import com.zhiyu.admin.dto.AdminOperationDto;
import com.zhiyu.admin.dto.IdentityChangeDto;
import com.zhiyu.common.service.GenericService;
import com.zhiyu.ufp.auth.entity.AuthOperationLog;
import com.zhiyu.ufp.auth.entity.AuthUserIdentity;
import com.zhiyu.ufp.auth.service.AuthOperationLogService;
import com.zhiyu.ufp.auth.service.AuthUserIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AuthUserIdentityService authUserIdentityService;
    private final AuthOperationLogService authOperationLogService;

    public Page<IdentityChangeDto> listIdentityChanges(
            final int page, final int size,
            final Long userId, final String action,
            final LocalDateTime startTime, final LocalDateTime endTime) {
        var wrapper = new LambdaQueryWrapper<AuthUserIdentity>();
        if (userId != null) {
            wrapper.eq(AuthUserIdentity::getAuthUserId, userId);
        }
        if (startTime != null) {
            wrapper.ge(AuthUserIdentity::getCreatedTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(AuthUserIdentity::getCreatedTime, endTime);
        }
        wrapper.orderByDesc(AuthUserIdentity::getCreatedTime);

        Page<AuthUserIdentity> entityPage =
                authUserIdentityService.selectPage(new Page<>(page, size), wrapper);
        return GenericService.pageDto(entityPage, e -> {
            IdentityChangeDto dto = AdminConverter.INSTANCE.toIdentityChangeDto(e);
            dto.setAction("BIND");
            return dto;
        });
    }

    public Page<AdminOperationDto> listAdminOperations(
            final int page, final int size,
            final String username, final String action,
            final LocalDateTime startTime, final LocalDateTime endTime) {
        var wrapper = new LambdaQueryWrapper<AuthOperationLog>();
        if (username != null && !username.isBlank()) {
            wrapper.eq(AuthOperationLog::getUserName, username);
        }
        if (action != null && !action.isBlank()) {
            wrapper.eq(AuthOperationLog::getOperationType, action);
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
        return GenericService.pageDto(entityPage, AdminConverter.INSTANCE::toAdminOperationDto);
    }
}
