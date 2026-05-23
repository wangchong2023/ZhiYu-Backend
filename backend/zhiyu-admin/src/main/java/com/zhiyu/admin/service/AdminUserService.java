package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.converter.AdminConverter;
import com.zhiyu.admin.dto.AdminUserDetailDto;
import com.zhiyu.admin.dto.AdminUserDto;
import com.zhiyu.admin.dto.LoginLogDto;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int ERR_USER_NOT_FOUND = 40401;

    private final AuthUserMapper authUserMapper;
    private final AuthUserLogMapper authUserLogMapper;

    public Page<AdminUserDto> listUsers(final int page, final int size,
                                         final String keyword,
                                         final String status) {
        var wrapper = new LambdaQueryWrapper<AuthUser>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(AuthUser::getAuthUserUsername, keyword)
                    .or().like(AuthUser::getAuthUserMail, keyword));
        }
        if ("ENABLED".equals(status)) {
            wrapper.eq(AuthUser::getAuthUserEnable, 1);
        } else if ("DISABLED".equals(status)) {
            wrapper.eq(AuthUser::getAuthUserEnable, 0);
        } else if ("DELETED".equals(status)) {
            wrapper.eq(AuthUser::getAuthUserDeleted, 1);
        }
        wrapper.orderByDesc(AuthUser::getCreatedTime);

        Page<AuthUser> entityPage = authUserMapper.selectPage(
                new Page<>(page, size), wrapper);
        Page<AdminUserDto> dtoPage = new Page<>(page, size, entityPage.getTotal());
        dtoPage.setRecords(entityPage.getRecords().stream()
                .map(AdminConverter.INSTANCE::toDto)
                .collect(Collectors.toList()));
        return dtoPage;
    }

    public AdminUserDetailDto getUserDetail(final Long userId) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ERR_USER_NOT_FOUND, "用户不存在");
        }

        List<LoginLogDto> recentLogs = authUserLogMapper.selectList(
                new LambdaQueryWrapper<AuthUserLog>()
                        .eq(AuthUserLog::getAuthUserLogUserId, userId)
                        .orderByDesc(AuthUserLog::getCreatedTime)
                        .last("LIMIT 10"))
                .stream()
                .map(AdminConverter.INSTANCE::toLogDto)
                .collect(Collectors.toList());

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

    @Transactional(rollbackFor = Exception.class)
    public void enableUser(final Long userId) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ERR_USER_NOT_FOUND, "用户不存在");
        }
        user.setAuthUserEnable(1);
        authUserMapper.updateById(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void disableUser(final Long userId) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ERR_USER_NOT_FOUND, "用户不存在");
        }
        user.setAuthUserEnable(0);
        authUserMapper.updateById(user);
    }
}
