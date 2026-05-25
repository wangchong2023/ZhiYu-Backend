package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.AdminUserDetailDto;
import com.zhiyu.admin.dto.AdminUserDto;
import com.zhiyu.ufp.auth.entity.AuthUser;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.service.AuthUserLogService;
import com.zhiyu.ufp.auth.service.IAuthUserService;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private IAuthUserService authUserService;
    @Mock private AuthUserLogService authUserLogService;
    @InjectMocks private AdminUserService adminUserService;

    // ---------- listUsers ----------

    @Test
    void shouldListUsersWithPagination() {
        AuthUser user = AuthUser.builder()
                .authUserId(1L).authUserUsername("testuser")
                .authUserMail("test@example.com")
                .authUserEnable(1).authUserDeleted(0)
                .createdTime(LocalDateTime.now())
                .build();

        when(authUserService.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<AuthUser>(1, 10, 1).setRecords(List.of(user)));

        Page<AdminUserDto> result = adminUserService.listUsers(1, 10, null, null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getUsername()).isEqualTo("testuser");
        assertThat(result.getRecords().get(0).getUserId()).isEqualTo(1L);
    }

    @Test
    void shouldListUsersWithKeywordFilter() {
        when(authUserService.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<AuthUser>(1, 10, 0));

        Page<AdminUserDto> result = adminUserService.listUsers(1, 10, "keyword", null);

        assertThat(result.getTotal()).isEqualTo(0);
    }

    @Test
    void shouldListUsersWithStatusFilter() {
        AuthUser disabledUser = AuthUser.builder()
                .authUserId(2L).authUserUsername("disabled")
                .authUserMail("disabled@example.com")
                .authUserEnable(0).authUserDeleted(0)
                .createdTime(LocalDateTime.now())
                .build();

        when(authUserService.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<AuthUser>(1, 10, 1).setRecords(List.of(disabledUser)));

        Page<AdminUserDto> result = adminUserService.listUsers(1, 10, null, "DISABLED");

        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void shouldListUsersWithDeletedStatus() {
        AuthUser deletedUser = AuthUser.builder()
                .authUserId(3L).authUserUsername("deleted")
                .authUserMail("deleted@example.com")
                .authUserEnable(0).authUserDeleted(1)
                .createdTime(LocalDateTime.now())
                .build();

        when(authUserService.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<AuthUser>(1, 10, 1).setRecords(List.of(deletedUser)));

        Page<AdminUserDto> result = adminUserService.listUsers(1, 10, null, "DELETED");

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getStatus()).isEqualTo("DELETED");
    }

    @Test
    void shouldListUsersWithEnabledStatus() {
        AuthUser enabledUser = AuthUser.builder()
                .authUserId(5L).authUserUsername("enabled")
                .authUserMail("enabled@example.com")
                .authUserEnable(1).authUserDeleted(0)
                .createdTime(LocalDateTime.now())
                .build();

        when(authUserService.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<AuthUser>(1, 10, 1).setRecords(List.of(enabledUser)));

        Page<AdminUserDto> result = adminUserService.listUsers(1, 10, null, "ENABLED");

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldListUsersWithNullEnable() {
        AuthUser nullEnableUser = AuthUser.builder()
                .authUserId(4L).authUserUsername("nullenable")
                .authUserMail("ne@example.com")
                .authUserEnable(null).authUserDeleted(0)
                .createdTime(LocalDateTime.now())
                .build();

        when(authUserService.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<AuthUser>(1, 10, 1).setRecords(List.of(nullEnableUser)));

        Page<AdminUserDto> result = adminUserService.listUsers(1, 10, null, null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void shouldListUsersWithBlankKeyword() {
        when(authUserService.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<AuthUser>(1, 10, 1)
                        .setRecords(List.of(AuthUser.builder()
                                .authUserId(1L).authUserUsername("test")
                                .authUserEnable(1).authUserDeleted(0)
                                .createdTime(LocalDateTime.now()).build())));

        Page<AdminUserDto> result = adminUserService.listUsers(1, 10, "   ", null);

        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void shouldListEmptyUsers() {
        when(authUserService.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<AuthUser>(1, 10, 0));

        Page<AdminUserDto> result = adminUserService.listUsers(1, 10, null, null);

        assertThat(result.getTotal()).isEqualTo(0);
        assertThat(result.getRecords()).isEmpty();
    }

    // ---------- getUserDetail ----------

    @Test
    void shouldGetUserDetail() {
        AuthUser user = AuthUser.builder()
                .authUserId(1L).authUserUsername("admin")
                .authUserMail("admin@zhiyu.local")
                .authUserMobile("13800138000")
                .authUserEnable(1).authUserDeleted(0)
                .authUserScope("ADMIN")
                .createdTime(LocalDateTime.of(2026, 5, 22, 15, 46))
                .build();

        when(authUserService.selectById(1L)).thenReturn(user);
        when(authUserLogService.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());

        AdminUserDetailDto detail = adminUserService.getUserDetail(1L);

        assertThat(detail.getUserId()).isEqualTo(1L);
        assertThat(detail.getUsername()).isEqualTo("admin");
        assertThat(detail.getEmail()).isEqualTo("admin@zhiyu.local");
        assertThat(detail.getScope()).isEqualTo("ADMIN");
        assertThat(detail.getStatus()).isEqualTo("ACTIVE");
        assertThat(detail.getRecentLogs()).isEmpty();
    }

    @Test
    void shouldGetUserDetailWithRecentLogs() {
        AuthUser user = AuthUser.builder()
                .authUserId(1L).authUserUsername("test")
                .authUserMail("test@test.com")
                .authUserEnable(1).authUserDeleted(0)
                .createdTime(LocalDateTime.now())
                .build();

        AuthUserLog log = new AuthUserLog();
        log.setAuthUserLogId(1L);
        log.setAuthUserLogAction("LOGIN");
        log.setAuthUserLogResult("SUCCESS");
        log.setAuthUserLogType("PASSWORD");
        log.setAuthUserLogUserDisplay("test");
        log.setAuthUserLogIp("192.168.1.1");
        log.setCreatedTime(LocalDateTime.now());

        when(authUserService.selectById(1L)).thenReturn(user);
        when(authUserLogService.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(log));

        AdminUserDetailDto detail = adminUserService.getUserDetail(1L);

        assertThat(detail.getRecentLogs()).hasSize(1);
        assertThat(detail.getRecentLogs().get(0).getAction()).isEqualTo("LOGIN");
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        when(authUserService.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> adminUserService.getUserDetail(999L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("User not found");
    }

    // ---------- enableUser ----------

    @Test
    void shouldEnableUser() {
        AuthUser user = AuthUser.builder()
                .authUserId(1L).authUserEnable(0).build();

        when(authUserService.selectById(1L)).thenReturn(user);

        adminUserService.enableUser(1L);

        assertThat(user.getAuthUserEnable()).isEqualTo(1);
        verify(authUserService).updateById(user);
    }

    @Test
    void shouldThrowWhenEnableNonexistentUser() {
        when(authUserService.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> adminUserService.enableUser(999L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("User not found");
    }

    // ---------- disableUser ----------

    @Test
    void shouldDisableUser() {
        AuthUser user = AuthUser.builder()
                .authUserId(1L).authUserEnable(1).build();

        when(authUserService.selectById(1L)).thenReturn(user);

        adminUserService.disableUser(1L);

        assertThat(user.getAuthUserEnable()).isEqualTo(0);
        verify(authUserService).updateById(user);
    }

    @Test
    void shouldThrowWhenDisableNonexistentUser() {
        when(authUserService.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> adminUserService.disableUser(999L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("User not found");
    }
}
