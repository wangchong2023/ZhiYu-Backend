package com.zhiyu.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.AdminUserDetailDto;
import com.zhiyu.admin.dto.AdminUserDto;
import com.zhiyu.admin.dto.LoginLogDto;
import com.zhiyu.admin.service.AdminUserService;
import com.zhiyu.common.web.ApiResponse;
import com.zhiyu.ufp.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock
    private AdminUserService adminUserService;

    @InjectMocks
    private AdminUserController adminUserController;

    private AdminUserDto sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = AdminUserDto.builder()
                .userId(1L)
                .username("testuser")
                .email("test@example.com")
                .mobile("13800138000")
                .createdAt(LocalDateTime.of(2026, 5, 20, 10, 0))
                .status("ENABLED")
                .lastLoginAt(LocalDateTime.of(2026, 5, 23, 8, 30))
                .lastLoginIp("192.168.1.1")
                .build();
    }

    // ─── list ───

    @Test
    void shouldListUsersWithDefaultPagination() {
        Page<AdminUserDto> mockPage = new Page<>(1, 20, 1);
        mockPage.setRecords(List.of(sampleUser));

        when(adminUserService.listUsers(1, 20, null, null)).thenReturn(mockPage);

        ApiResponse<Page<AdminUserDto>> response = adminUserController.list(1, 20, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getRecords()).hasSize(1);
        assertThat(response.getData().getRecords().get(0).getUsername()).isEqualTo("testuser");
        assertThat(response.getData().getTotal()).isEqualTo(1);

        verify(adminUserService).listUsers(1, 20, null, null);
    }

    @Test
    void shouldListUsersWithKeywordFilter() {
        Page<AdminUserDto> mockPage = new Page<>(1, 20, 0);
        mockPage.setRecords(List.of());

        when(adminUserService.listUsers(1, 20, "searchTerm", null)).thenReturn(mockPage);

        ApiResponse<Page<AdminUserDto>> response = adminUserController.list(1, 20, "searchTerm", null);

        assertThat(response.getData().getRecords()).isEmpty();
        verify(adminUserService).listUsers(1, 20, "searchTerm", null);
    }

    @Test
    void shouldListUsersWithStatusFilter() {
        Page<AdminUserDto> mockPage = new Page<>(1, 20, 3);
        mockPage.setRecords(List.of(sampleUser,
                AdminUserDto.builder().userId(2L).username("user2").status("ENABLED").build(),
                AdminUserDto.builder().userId(3L).username("user3").status("ENABLED").build()));

        when(adminUserService.listUsers(1, 20, null, "ENABLED")).thenReturn(mockPage);

        ApiResponse<Page<AdminUserDto>> response = adminUserController.list(1, 20, null, "ENABLED");

        assertThat(response.getData().getRecords()).hasSize(3);
        assertThat(response.getData().getTotal()).isEqualTo(3);
        verify(adminUserService).listUsers(1, 20, null, "ENABLED");
    }

    @Test
    void shouldListUsersWithPagination() {
        Page<AdminUserDto> mockPage = new Page<>(2, 10, 25);
        mockPage.setRecords(List.of(sampleUser));

        when(adminUserService.listUsers(2, 10, null, null)).thenReturn(mockPage);

        ApiResponse<Page<AdminUserDto>> response = adminUserController.list(2, 10, null, null);

        assertThat(response.getData().getCurrent()).isEqualTo(2);
        assertThat(response.getData().getSize()).isEqualTo(10);
        assertThat(response.getData().getTotal()).isEqualTo(25);
        verify(adminUserService).listUsers(2, 10, null, null);
    }

    @Test
    void shouldListUsersWithDisabledStatus() {
        AdminUserDto disabledUser = AdminUserDto.builder()
                .userId(2L).username("disabled_user").status("DISABLED").build();

        Page<AdminUserDto> mockPage = new Page<>(1, 20, 1);
        mockPage.setRecords(List.of(disabledUser));

        when(adminUserService.listUsers(1, 20, null, "DISABLED")).thenReturn(mockPage);

        ApiResponse<Page<AdminUserDto>> response = adminUserController.list(1, 20, null, "DISABLED");

        assertThat(response.getData().getRecords().get(0).getStatus()).isEqualTo("DISABLED");
        verify(adminUserService).listUsers(1, 20, null, "DISABLED");
    }

    @Test
    void shouldListUsersWithKeywordAndStatusFilter() {
        Page<AdminUserDto> mockPage = new Page<>(1, 20, 0);

        when(adminUserService.listUsers(1, 20, "admin", "ENABLED")).thenReturn(mockPage);

        ApiResponse<Page<AdminUserDto>> response = adminUserController.list(1, 20, "admin", "ENABLED");

        assertThat(response.getData().getRecords()).isEmpty();
        verify(adminUserService).listUsers(1, 20, "admin", "ENABLED");
    }

    // ─── detail ───

    @Test
    void shouldReturnUserDetailWhenUserExists() {
        List<LoginLogDto> logs = List.of(
                LoginLogDto.builder().id(1L).username("testuser")
                        .action("LOGIN").type("PASSWORD").result("SUCCESS")
                        .ip("192.168.1.1").time(LocalDateTime.of(2026, 5, 23, 8, 30)).build());

        AdminUserDetailDto detailDto = AdminUserDetailDto.builder()
                .userId(1L).username("testuser").email("test@example.com")
                .mobile("13800138000").createdAt(LocalDateTime.of(2026, 5, 20, 10, 0))
                .status("ENABLED").scope("ADMIN").recentLogs(logs).build();

        when(adminUserService.getUserDetail(1L)).thenReturn(detailDto);

        ApiResponse<AdminUserDetailDto> response = adminUserController.detail(1L);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData().getUserId()).isEqualTo(1L);
        assertThat(response.getData().getUsername()).isEqualTo("testuser");
        assertThat(response.getData().getStatus()).isEqualTo("ENABLED");
        assertThat(response.getData().getScope()).isEqualTo("ADMIN");
        assertThat(response.getData().getRecentLogs()).hasSize(1);

        verify(adminUserService).getUserDetail(1L);
        verifyNoMoreInteractions(adminUserService);
    }

    @Test
    void shouldReturnUserDetailWithEmptyLogs() {
        AdminUserDetailDto detailDto = AdminUserDetailDto.builder()
                .userId(2L).username("new_user").email("new@example.com")
                .createdAt(LocalDateTime.now())
                .status("ENABLED").scope("USER").recentLogs(List.of()).build();

        when(adminUserService.getUserDetail(2L)).thenReturn(detailDto);

        ApiResponse<AdminUserDetailDto> response = adminUserController.detail(2L);

        assertThat(response.getData().getRecentLogs()).isEmpty();
        verify(adminUserService).getUserDetail(2L);
    }

    @Test
    void shouldPropagateExceptionWhenUserNotFoundForDetail() {
        when(adminUserService.getUserDetail(999L))
                .thenThrow(new BizException(40401, "用户不存在"));

        assertThatThrownBy(() -> adminUserController.detail(999L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户不存在");

        verify(adminUserService).getUserDetail(999L);
    }

    // ─── enable ───

    @Test
    void shouldEnableUserSuccessfully() {
        ApiResponse<Void> response = adminUserController.enable(1L);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isNull();

        verify(adminUserService).enableUser(1L);
        verifyNoMoreInteractions(adminUserService);
    }

    @Test
    void shouldPropagateExceptionWhenEnableFails() {
        doThrow(new BizException(40401, "用户不存在"))
                .when(adminUserService).enableUser(999L);

        assertThatThrownBy(() -> adminUserController.enable(999L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户不存在");

        verify(adminUserService).enableUser(999L);
    }

    // ─── disable ───

    @Test
    void shouldDisableUserSuccessfully() {
        ApiResponse<Void> response = adminUserController.disable(1L);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isNull();

        verify(adminUserService).disableUser(1L);
        verifyNoMoreInteractions(adminUserService);
    }

    @Test
    void shouldPropagateExceptionWhenDisableFails() {
        doThrow(new BizException(40401, "用户不存在"))
                .when(adminUserService).disableUser(999L);

        assertThatThrownBy(() -> adminUserController.disable(999L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户不存在");

        verify(adminUserService).disableUser(999L);
    }
}
