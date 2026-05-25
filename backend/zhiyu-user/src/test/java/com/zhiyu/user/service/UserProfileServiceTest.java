package com.zhiyu.user.service;

import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.service.AuthUserService;
import com.zhiyu.ufp.common.exception.BizErrorCode;
import com.zhiyu.ufp.common.exception.BizException;
import com.zhiyu.user.dto.UpdateProfileReq;
import com.zhiyu.user.dto.UserProfileResp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private AuthUserService authUserService;

    @InjectMocks
    private UserProfileService userProfileService;

    private static AuthUser buildUser(Long id, String username, String nick, String email,
                                       Integer emailVerified, String mobile, Integer mobileVerified,
                                       Integer deleted, Integer enabled) {
        return AuthUser.builder()
                .authUserId(id)
                .authUserUsername(username)
                .authUserNick(nick)
                .authUserMail(email)
                .authUserMailVerified(emailVerified)
                .authUserMobile(mobile)
                .authUserMobileVerified(mobileVerified)
                .authUserScope("openid")
                .authUserDeleted(deleted)
                .authUserEnable(enabled)
                .createdTime(LocalDateTime.of(2025, 1, 1, 0, 0))
                .build();
    }

    // ── getProfile() ─────────────────────────────────────────────

    @Test
    void shouldReturnProfileWhenUserExists() {
        AuthUser user = buildUser(1001L, "zhangsan", "张三", "zhangsan@example.com",
                1, "13800138000", 1, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        UserProfileResp result = userProfileService.getProfile(1001L);

        assertThat(result.getUserId()).isEqualTo(1001L);
        assertThat(result.getUsername()).isEqualTo("zhangsan");
        assertThat(result.getNick()).isEqualTo("张三");
        assertThat(result.getEmail()).isEqualTo("zhangsan@example.com");
        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getMobile()).isEqualTo("13800138000");
        assertThat(result.isMobileVerified()).isTrue();
        assertThat(result.getScope()).isEqualTo("openid");
        assertThat(result.getCreatedTime()).isNotNull();
        verify(authUserService).selectById(1001L);
    }

    @Test
    void shouldThrowBizExceptionWhenUserNotFoundForGetProfile() {
        when(authUserService.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> userProfileService.getProfile(9999L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.RESOURCE_NOT_FOUND.getCode());

        verify(authUserService).selectById(9999L);
    }

    @Test
    void shouldReturnProfileWithEmailNotVerifiedWhenNull() {
        AuthUser user = buildUser(1001L, "zhangsan", "张三", "zhangsan@example.com",
                null, null, null, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        UserProfileResp result = userProfileService.getProfile(1001L);

        assertThat(result.isEmailVerified()).isFalse();
        assertThat(result.isMobileVerified()).isFalse();
    }

    @Test
    void shouldReturnProfileWithEmailNotVerifiedWhenZero() {
        AuthUser user = buildUser(1001L, "zhangsan", "张三", "zhangsan@example.com",
                0, null, 0, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        UserProfileResp result = userProfileService.getProfile(1001L);

        assertThat(result.isEmailVerified()).isFalse();
        assertThat(result.isMobileVerified()).isFalse();
    }

    // ── updateProfile() ──────────────────────────────────────────

    @Test
    void shouldUpdateProfileWhenUserExists() {
        AuthUser user = buildUser(1001L, "zhangsan", "旧昵称", "zhangsan@example.com",
                1, null, null, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        UpdateProfileReq request = new UpdateProfileReq();
        request.setNick("新昵称");

        UserProfileResp result = userProfileService.updateProfile(1001L, request);

        assertThat(result.getNick()).isEqualTo("新昵称");
        verify(authUserService).updateById(user);
    }

    @Test
    void shouldThrowBizExceptionWhenUpdateUserNotFound() {
        when(authUserService.selectById(9999L)).thenReturn(null);

        UpdateProfileReq request = new UpdateProfileReq();
        request.setNick("新昵称");

        assertThatThrownBy(() -> userProfileService.updateProfile(9999L, request))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.RESOURCE_NOT_FOUND.getCode());

        verify(authUserService, never()).updateById(any(AuthUser.class));
    }

    @Test
    void shouldNotUpdateNickWhenNickIsNull() {
        AuthUser user = buildUser(1001L, "zhangsan", "旧昵称", "zhangsan@example.com",
                1, null, null, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        UpdateProfileReq request = new UpdateProfileReq();
        request.setNick(null);

        UserProfileResp result = userProfileService.updateProfile(1001L, request);

        assertThat(result.getNick()).isEqualTo("旧昵称");
        verify(authUserService).updateById(user);
    }

    @Test
    void shouldSetUpdatedTimeWhenUpdatingProfile() {
        AuthUser user = buildUser(1001L, "zhangsan", "旧昵称", "zhangsan@example.com",
                1, null, null, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        UpdateProfileReq request = new UpdateProfileReq();
        request.setNick("新昵称");

        userProfileService.updateProfile(1001L, request);

        assertThat(user.getUpdatedTime()).isNotNull();
    }

    @Test
    void shouldUpdateProfileWithoutNickChange() {
        AuthUser user = buildUser(1001L, "zhangsan", "不变昵称", "zhangsan@example.com",
                1, null, null, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        UpdateProfileReq request = new UpdateProfileReq();
        // No nick set (null) — should keep existing nick

        UserProfileResp result = userProfileService.updateProfile(1001L, request);

        assertThat(result.getNick()).isEqualTo("不变昵称");
        verify(authUserService).updateById(user);
    }

    // ── deleteAccount() ───────────────────────────────────────────

    @Test
    void shouldDeleteAccountSuccessfully() {
        AuthUser user = buildUser(1001L, "zhangsan", "张三", "zhangsan@example.com",
                1, null, null, null, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        userProfileService.deleteAccount(1001L);

        assertThat(user.getAuthUserDeleted()).isEqualTo(1);
        assertThat(user.getAuthUserEnable()).isZero();
        assertThat(user.getUpdatedTime()).isNotNull();
        verify(authUserService).updateById(user);
    }

    @Test
    void shouldThrowBizExceptionWhenDeleteUserNotFound() {
        when(authUserService.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> userProfileService.deleteAccount(9999L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.RESOURCE_NOT_FOUND.getCode());

        verify(authUserService, never()).updateById(any(AuthUser.class));
    }

    @Test
    void shouldThrowBizExceptionWhenAlreadyDeleted() {
        AuthUser user = buildUser(1001L, "zhangsan", "张三", "zhangsan@example.com",
                1, null, null, 1, 0);
        when(authUserService.selectById(1001L)).thenReturn(user);

        assertThatThrownBy(() -> userProfileService.deleteAccount(1001L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(BizErrorCode.DELETION_ALREADY_REQUESTED.getCode());

        verify(authUserService, never()).updateById(any(AuthUser.class));
    }

    @Test
    void shouldNotThrowWhenAlreadyDeletedIsZero() {
        AuthUser user = buildUser(1001L, "zhangsan", "张三", "zhangsan@example.com",
                1, null, null, 0, 1);
        when(authUserService.selectById(1001L)).thenReturn(user);

        userProfileService.deleteAccount(1001L);

        assertThat(user.getAuthUserDeleted()).isEqualTo(1);
        verify(authUserService).updateById(user);
    }

    // ── toResp() internal conversion ─────────────────────────────

    @Test
    void shouldConvertAuthUserToUserProfileRespWithAllFields() {
        AuthUser user = buildUser(1002L, "lisi", "李四", "lisi@example.com",
                1, "13900139000", 0, null, 1);
        user.setAuthUserScope("admin");
        when(authUserService.selectById(1002L)).thenReturn(user);

        UserProfileResp result = userProfileService.getProfile(1002L);

        assertThat(result.getUserId()).isEqualTo(1002L);
        assertThat(result.getUsername()).isEqualTo("lisi");
        assertThat(result.getNick()).isEqualTo("李四");
        assertThat(result.getEmail()).isEqualTo("lisi@example.com");
        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getMobile()).isEqualTo("13900139000");
        assertThat(result.isMobileVerified()).isFalse();
        assertThat(result.getScope()).isEqualTo("admin");
        assertThat(result.getCreatedTime()).isEqualTo(LocalDateTime.of(2025, 1, 1, 0, 0));
    }
}
