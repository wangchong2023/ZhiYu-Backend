/**
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: AdminUserService.java
 * 创建时间: 2026-05-27
 * 描述: 后台管理用户管理业务服务。提供用户列表的分页查询、状态筛选、信息详情及账号启用/禁用的事务控制。
 */
package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.converter.AdminConverter;
import com.zhiyu.admin.dto.AdminUserDetailDto;
import com.zhiyu.admin.dto.AdminUserDto;
import com.zhiyu.admin.dto.LoginLogDto;
import com.zhiyu.common.service.GenericService;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.service.AuthUserLogService;
import com.zhiyu.ufp.auth.service.IAuthUserService;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 类名: AdminUserService
 * 描述: 管理后台用户治理服务实现。包含对底层 AuthUser 账号状态启用、禁用状态控制，以及联动查询登录历史等核心方法。
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    // 默认获取的最近登录日志条数
    private static final int RECENT_LOGS_LIMIT = 10;
    // 用户账号启用状态标识值
    private static final int USER_STATUS_ENABLE = 1;
    // 用户账号禁用状态标识值
    private static final int USER_STATUS_DISABLE = 0;

    private static final int ERR_USER_NOT_FOUND = 40401;

    private final IAuthUserService authUserService;
    private final AuthUserLogService authUserLogService;

    /**
     * 描述: 根据关键字和状态条件，分页查询系统用户列表。
     * @param page 页码 (1-indexed)
     * @param size 每页条数
     * @param keyword 模糊检索关键字（匹配用户名或邮箱）
     * @param status 账号筛选状态（ENABLED/DISABLED/DELETED）
     * @return 分页载体对象 Page<AdminUserDto>
     */
    public Page<AdminUserDto> listUsers(final int page, final int size,
                                         final String keyword,
                                         final String status) {
        var wrapper = new LambdaQueryWrapper<AuthUser>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(AuthUser::getAuthUserUsername, keyword)
                    .or().like(AuthUser::getAuthUserMail, keyword));
        }
        if ("ENABLED".equals(status)) {
            wrapper.eq(AuthUser::getAuthUserEnable, USER_STATUS_ENABLE);
        } else if ("DISABLED".equals(status)) {
            wrapper.eq(AuthUser::getAuthUserEnable, USER_STATUS_DISABLE);
        } else if ("DELETED".equals(status)) {
            wrapper.eq(AuthUser::getAuthUserDeleted, USER_STATUS_ENABLE);
        }
        wrapper.orderByDesc(AuthUser::getCreatedTime);

        Page<AuthUser> entityPage = authUserService.selectPage(
                new Page<>(page, size), wrapper);
        return GenericService.pageDto(entityPage, AdminConverter.INSTANCE::toDto);
    }

    /**
     * 描述: 获取指定用户的详情信息，包括基本属性及最近的 10 条登录审计日志。
     * @param userId 用户 ID
     * @return 用户详细信息传输对象 AdminUserDetailDto
     * @throws BizException 当用户不存在时抛出 40401 业务异常
     */
    public AdminUserDetailDto getUserDetail(final Long userId) {
        AuthUser user = authUserService.selectById(userId);
        if (user == null) {
            throw new BizException(ERR_USER_NOT_FOUND, "User not found");
        }

        List<LoginLogDto> recentLogs = authUserLogService.selectList(
                new LambdaQueryWrapper<AuthUserLog>()
                         .eq(AuthUserLog::getAuthUserLogUserId, userId)
                         .orderByDesc(AuthUserLog::getCreatedTime)
                         .last("LIMIT " + RECENT_LOGS_LIMIT))
                .stream()
                .map(AdminConverter.INSTANCE::toLogDto)
                .toList();

        return AdminUserDetailDto.builder()
                .userId(user.getAuthUserId())
                .username(user.getAuthUserUsername())
                .email(user.getAuthUserMail())
                .mobile(user.getAuthUserMobile())
                .createdAt(user.getCreatedTime())
                .status(AdminConverter.INSTANCE.toStatus(
                        user.getAuthUserEnable(), user.getAuthUserDeleted()))
                .scope(user.getAuthUserScope())
                .recentLogs(recentLogs)
                .build();
    }

    /**
     * 描述: 启用指定的用户账号。
     * @param userId 用户 ID
     * @throws BizException 当用户不存在时抛出 40401 业务异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void enableUser(final Long userId) {
        AuthUser user = authUserService.selectById(userId);
        if (user == null) {
            throw new BizException(ERR_USER_NOT_FOUND, "User not found");
        }
        user.setAuthUserEnable(USER_STATUS_ENABLE);
        authUserService.updateById(user);
    }

    /**
     * 描述: 禁用指定的用户账号，使其无法通过系统进行登录或令牌刷新。
     * @param userId 用户 ID
     * @throws BizException 当用户不存在时抛出 40401 业务异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void disableUser(final Long userId) {
        AuthUser user = authUserService.selectById(userId);
        if (user == null) {
            throw new BizException(ERR_USER_NOT_FOUND, "User not found");
        }
        user.setAuthUserEnable(USER_STATUS_DISABLE);
        authUserService.updateById(user);
    }
}
