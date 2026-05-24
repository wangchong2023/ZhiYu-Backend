package com.zhiyu.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.LoginLogDto;
import com.zhiyu.admin.service.AdminLogService;
import com.zhiyu.common.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminLogControllerTest {

    @Mock
    private AdminLogService adminLogService;

    @InjectMocks
    private AdminLogController adminLogController;

    // ─── list (login logs) ───

    @Test
    void shouldListLoginLogsWithDefaultPagination() {
        LoginLogDto log = LoginLogDto.builder()
                .id(1L).username("testuser").action("LOGIN").type("PASSWORD")
                .result("SUCCESS").ip("192.168.1.1")
                .time(LocalDateTime.of(2026, 5, 23, 10, 0)).build();
        Page<LoginLogDto> mockPage = new Page<>(1, 20, 1);
        mockPage.setRecords(List.of(log));

        when(adminLogService.listLogs(eq(1), eq(20), isNull(), isNull(), isNull(),
                isNull(), isNull())).thenReturn(mockPage);

        ApiResponse<Page<LoginLogDto>> response =
                adminLogController.list(1, 20, null, null, null, null, null);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData().getRecords()).hasSize(1);
        assertThat(response.getData().getRecords().get(0).getUsername()).isEqualTo("testuser");
        assertThat(response.getData().getRecords().get(0).getType()).isEqualTo("PASSWORD");
        assertThat(response.getData().getTotal()).isEqualTo(1);

        verify(adminLogService).listLogs(1, 20, null, null, null, null, null);
    }

    @Test
    void shouldListLoginLogsWithUsernameFilter() {
        LoginLogDto log = LoginLogDto.builder()
                .id(2L).username("admin").action("LOGIN").type("PASSWORD")
                .result("SUCCESS").ip("10.0.0.1")
                .time(LocalDateTime.of(2026, 5, 23, 9, 0)).build();
        Page<LoginLogDto> mockPage = new Page<>(1, 20, 1);
        mockPage.setRecords(List.of(log));

        when(adminLogService.listLogs(eq(1), eq(20), eq("admin"), isNull(), isNull(),
                isNull(), isNull())).thenReturn(mockPage);

        ApiResponse<Page<LoginLogDto>> response =
                adminLogController.list(1, 20, "admin", null, null, null, null);

        assertThat(response.getData().getRecords().get(0).getUsername()).isEqualTo("admin");
        verify(adminLogService).listLogs(1, 20, "admin", null, null, null, null);
    }

    @Test
    void shouldListLoginLogsWithTypeAndResultFilters() {
        Page<LoginLogDto> mockPage = new Page<>(1, 20, 0);

        when(adminLogService.listLogs(eq(1), eq(20), isNull(), eq("SMS"), eq("FAILURE"),
                isNull(), isNull())).thenReturn(mockPage);

        ApiResponse<Page<LoginLogDto>> response =
                adminLogController.list(1, 20, null, "SMS", "FAILURE", null, null);

        assertThat(response.getData().getRecords()).isEmpty();
        verify(adminLogService).listLogs(1, 20, null, "SMS", "FAILURE", null, null);
    }

    @Test
    void shouldListLoginLogsWithTimeRange() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 22, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 23, 23, 59);
        Page<LoginLogDto> mockPage = new Page<>(1, 20, 5);
        mockPage.setRecords(List.of(
                LoginLogDto.builder().id(1L).username("user1").time(start.plusHours(1)).build(),
                LoginLogDto.builder().id(2L).username("user2").time(start.plusHours(2)).build()));

        when(adminLogService.listLogs(1, 20, null, null, null, start, end))
                .thenReturn(mockPage);

        ApiResponse<Page<LoginLogDto>> response =
                adminLogController.list(1, 20, null, null, null, start, end);

        assertThat(response.getData().getRecords()).hasSize(2);
        assertThat(response.getData().getTotal()).isEqualTo(5);
        verify(adminLogService).listLogs(1, 20, null, null, null, start, end);
    }

    @Test
    void shouldListLoginLogsWithAllFilters() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 23, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 23, 12, 0);
        Page<LoginLogDto> mockPage = new Page<>(2, 10, 0);

        when(adminLogService.listLogs(2, 10, "testuser", "GITHUB", "SUCCESS", start, end))
                .thenReturn(mockPage);

        ApiResponse<Page<LoginLogDto>> response =
                adminLogController.list(2, 10, "testuser", "GITHUB", "SUCCESS", start, end);

        assertThat(response.getData().getCurrent()).isEqualTo(2);
        assertThat(response.getData().getSize()).isEqualTo(10);
        assertThat(response.getData().getTotal()).isEqualTo(0);
        verify(adminLogService).listLogs(2, 10, "testuser", "GITHUB", "SUCCESS", start, end);
    }

    // ─── securityLogs ───

    @Test
    void shouldListSecurityLogsWithDefaultPagination() {
        LoginLogDto log = LoginLogDto.builder()
                .id(1L).username("admin").action("LOGIN").type("PASSWORD")
                .result("SUCCESS").ip("10.0.0.1").device("Chrome")
                .time(LocalDateTime.of(2026, 5, 23, 8, 0))
                .build();
        Page<LoginLogDto> mockPage = new Page<>(1, 20, 1);
        mockPage.setRecords(List.of(log));

        when(adminLogService.listSecurityLogs(eq(1), eq(20), isNull(), isNull(),
                isNull(), isNull())).thenReturn(mockPage);

        ApiResponse<Page<LoginLogDto>> response =
                adminLogController.securityLogs(1, 20, null, null, null, null);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData().getRecords()).hasSize(1);
        assertThat(response.getData().getRecords().get(0).getAction()).isEqualTo("LOGIN");

        verify(adminLogService).listSecurityLogs(1, 20, null, null, null, null);
    }

    @Test
    void shouldListSecurityLogsWithTypeFilter() {
        Page<LoginLogDto> mockPage = new Page<>(1, 20, 0);

        when(adminLogService.listSecurityLogs(eq(1), eq(20), eq("LOGOUT"), isNull(),
                isNull(), isNull())).thenReturn(mockPage);

        ApiResponse<Page<LoginLogDto>> response =
                adminLogController.securityLogs(1, 20, "LOGOUT", null, null, null);

        assertThat(response.getData().getRecords()).isEmpty();
        verify(adminLogService).listSecurityLogs(1, 20, "LOGOUT", null, null, null);
    }

    @Test
    void shouldListSecurityLogsWithIpFilter() {
        LoginLogDto log = LoginLogDto.builder()
                .id(3L).username("hacker").action("RATE_LIMIT").type(null)
                .result("BLOCKED").ip("203.0.113.1")
                .time(LocalDateTime.of(2026, 5, 23, 7, 0))
                .build();
        Page<LoginLogDto> mockPage = new Page<>(1, 20, 1);
        mockPage.setRecords(List.of(log));

        when(adminLogService.listSecurityLogs(eq(1), eq(20), isNull(), eq("203.0.113.1"),
                isNull(), isNull())).thenReturn(mockPage);

        ApiResponse<Page<LoginLogDto>> response =
                adminLogController.securityLogs(1, 20, null, "203.0.113.1", null, null);

        assertThat(response.getData().getRecords().get(0).getResult()).isEqualTo("BLOCKED");
        verify(adminLogService).listSecurityLogs(1, 20, null, "203.0.113.1", null, null);
    }

    @Test
    void shouldListSecurityLogsWithTimeRange() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 22, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 23, 23, 59);
        Page<LoginLogDto> mockPage = new Page<>(1, 20, 3);
        mockPage.setRecords(List.of(
                LoginLogDto.builder().id(1L).username("user1").action("LOGIN").time(start.plusHours(1)).build(),
                LoginLogDto.builder().id(2L).username("user2").action("LOGOUT").time(start.plusHours(3)).build(),
                LoginLogDto.builder().id(3L).username("user3").action("CAPTCHA").time(start.plusHours(5)).build()));

        when(adminLogService.listSecurityLogs(1, 20, null, null, start, end))
                .thenReturn(mockPage);

        ApiResponse<Page<LoginLogDto>> response =
                adminLogController.securityLogs(1, 20, null, null, start, end);

        assertThat(response.getData().getRecords()).hasSize(3);
        assertThat(response.getData().getTotal()).isEqualTo(3);
        verify(adminLogService).listSecurityLogs(1, 20, null, null, start, end);
    }

    @Test
    void shouldListSecurityLogsWithTypeAndIpAndTime() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 23, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 23, 12, 0);
        Page<LoginLogDto> mockPage = new Page<>(1, 50, 0);

        when(adminLogService.listSecurityLogs(1, 50, "CAPTCHA", "192.168.1.100", start, end))
                .thenReturn(mockPage);

        ApiResponse<Page<LoginLogDto>> response =
                adminLogController.securityLogs(1, 50, "CAPTCHA", "192.168.1.100", start, end);

        assertThat(response.getData().getRecords()).isEmpty();
        verify(adminLogService).listSecurityLogs(1, 50, "CAPTCHA", "192.168.1.100", start, end);
    }

    @Test
    void shouldHandleCustomPaginationForSecurityLogs() {
        Page<LoginLogDto> mockPage = new Page<>(3, 5, 25);
        mockPage.setRecords(List.of());

        when(adminLogService.listSecurityLogs(eq(3), eq(5), isNull(), isNull(),
                isNull(), isNull())).thenReturn(mockPage);

        ApiResponse<Page<LoginLogDto>> response =
                adminLogController.securityLogs(3, 5, null, null, null, null);

        assertThat(response.getData().getCurrent()).isEqualTo(3);
        assertThat(response.getData().getSize()).isEqualTo(5);
        assertThat(response.getData().getTotal()).isEqualTo(25);
        verify(adminLogService).listSecurityLogs(3, 5, null, null, null, null);
    }

    @Test
    void shouldVerifyNoMoreInteractionsAfterServiceCall() {
        Page<LoginLogDto> mockPage = new Page<>(1, 20, 0);

        when(adminLogService.listLogs(1, 20, null, null, null, null, null))
                .thenReturn(mockPage);

        adminLogController.list(1, 20, null, null, null, null, null);

        verify(adminLogService).listLogs(1, 20, null, null, null, null, null);
        verifyNoMoreInteractions(adminLogService);
    }
}
