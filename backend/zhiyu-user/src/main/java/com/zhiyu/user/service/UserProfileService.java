package com.zhiyu.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import com.zhiyu.ufp.auth.mapper.AuthUserMapper;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import com.zhiyu.user.dto.LoginHistoryDto;
import com.zhiyu.user.dto.UpdateProfileReq;
import com.zhiyu.user.dto.UserProfileResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final int DELETED_FLAG = 1;
    private static final int VERIFIED_FLAG = 1;

    private final AuthUserMapper authUserMapper;
    private final AuthUserLogMapper authUserLogMapper;

    public UserProfileResp getProfile(final Long userId) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }
        return toResp(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProfileResp updateProfile(final Long userId, final UpdateProfileReq request) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }

        if (request.getNick() != null) {
            user.setAuthUserNick(request.getNick());
        }
        user.setUpdatedTime(LocalDateTime.now());
        authUserMapper.updateById(user);

        return toResp(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(final Long userId) {
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.RESOURCE_NOT_FOUND);
        }

        if (user.getAuthUserDeleted() != null && user.getAuthUserDeleted() == DELETED_FLAG) {
            throw new BizException(BizErrorCode.DELETION_ALREADY_REQUESTED);
        }

        user.setAuthUserDeleted(DELETED_FLAG);
        user.setAuthUserEnable(0);
        user.setUpdatedTime(LocalDateTime.now());
        authUserMapper.updateById(user);

        log.info("Account deletion requested for userId={}", userId);
    }

    public Page<LoginHistoryDto> getLoginHistory(final Long userId, final int page, final int size) {
        var wrapper = new LambdaQueryWrapper<AuthUserLog>()
                .eq(AuthUserLog::getAuthUserLogUserId, userId)
                .orderByDesc(AuthUserLog::getCreatedTime);

        Page<AuthUserLog> entityPage = authUserLogMapper.selectPage(
                new Page<>(page, size), wrapper);
        Page<LoginHistoryDto> dtoPage = new Page<>(page, size, entityPage.getTotal());
        dtoPage.setRecords(entityPage.getRecords().stream()
                .map(e -> LoginHistoryDto.builder()
                        .id(e.getAuthUserLogId())
                        .username(e.getAuthUserLogUserDisplay())
                        .action(e.getAuthUserLogAction())
                        .type(e.getAuthUserLogType())
                        .result(e.getAuthUserLogResult())
                        .ip(e.getAuthUserLogIp())
                        .device(e.getAuthUserLogDevice())
                        .location(e.getAuthUserLogLocation())
                        .time(e.getCreatedTime())
                        .build())
                .collect(Collectors.toList()));
        return dtoPage;
    }

    private UserProfileResp toResp(final AuthUser user) {
        return UserProfileResp.builder()
                .userId(user.getAuthUserId())
                .username(user.getAuthUserUsername())
                .nick(user.getAuthUserNick())
                .email(user.getAuthUserMail())
                .emailVerified(user.getAuthUserMailVerified() != null
                        && user.getAuthUserMailVerified() == VERIFIED_FLAG)
                .mobile(user.getAuthUserMobile())
                .mobileVerified(user.getAuthUserMobileVerified() != null
                        && user.getAuthUserMobileVerified() == VERIFIED_FLAG)
                .scope(user.getAuthUserScope())
                .createdTime(user.getCreatedTime())
                .build();
    }
}
