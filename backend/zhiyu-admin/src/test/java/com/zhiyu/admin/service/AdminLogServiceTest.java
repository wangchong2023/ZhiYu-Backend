package com.zhiyu.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyu.admin.dto.LoginLogDto;
import com.zhiyu.ufp.auth.entity.AuthUserLog;
import com.zhiyu.ufp.auth.mapper.AuthUserLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminLogServiceTest {

    @Mock private AuthUserLogMapper authUserLogMapper;
    @InjectMocks private AdminLogService adminLogService;

    private static AuthUserLog makeLog(Long id, String action, String result,
                                        String type, String username) {
        AuthUserLog log = new AuthUserLog();
        log.setAuthUserLogId(id);
        log.setAuthUserLogAction(action);
        log.setAuthUserLogResult(result);
        log.setAuthUserLogType(type);
        log.setAuthUserLogUserDisplay(username);
        log.setAuthUserLogIp("192.168.1.1");
        log.setCreatedTime(LocalDateTime.now());
        return log;
    }

    @Test
    void shouldListLogsWithPagination() {
        AuthUserLog log = makeLog(1L, "LOGIN", "SUCCESS", "PASSWORD", "testuser");

        Page<AuthUserLog> entityPage = new Page<>(1, 10, 1);
        entityPage.setRecords(List.of(log));

        when(authUserLogMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(entityPage);

        Page<LoginLogDto> result = adminLogService.listLogs(
                1, 10, null, null, null, null, null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getAction()).isEqualTo("LOGIN");
        assertThat(result.getRecords().get(0).getResult()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldFilterLogsByUsername() {
        AuthUserLog log = makeLog(1L, "LOGIN", "SUCCESS", "PASSWORD", "admin");

        Page<AuthUserLog> entityPage = new Page<>(1, 10, 1);
        entityPage.setRecords(List.of(log));

        when(authUserLogMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(entityPage);

        Page<LoginLogDto> result = adminLogService.listLogs(
                1, 10, "admin", null, null, null, null);

        assertThat(result.getRecords().get(0).getUsername()).isEqualTo("admin");
    }

    @Test
    void shouldFilterLogsByType() {
        Page<AuthUserLog> entityPage = new Page<>(1, 10, 1);
        entityPage.setRecords(List.of(makeLog(1L, "LOGIN", "SUCCESS", "WEBAUTHN", null)));

        when(authUserLogMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(entityPage);

        Page<LoginLogDto> result = adminLogService.listLogs(
                1, 10, null, "WEBAUTHN", null, null, null);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    void shouldFilterLogsByResult() {
        Page<AuthUserLog> entityPage = new Page<>(1, 10, 1);
        entityPage.setRecords(List.of(makeLog(1L, "LOGIN", "FAILURE", "PASSWORD", null)));

        when(authUserLogMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(entityPage);

        Page<LoginLogDto> result = adminLogService.listLogs(
                1, 10, null, null, "FAILURE", null, null);

        assertThat(result.getRecords().get(0).getResult()).isEqualTo("FAILURE");
    }

    @Test
    void shouldFilterLogsByDateRange() {
        Page<AuthUserLog> entityPage = new Page<>(1, 10, 0);

        when(authUserLogMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(entityPage);

        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 31, 23, 59);

        Page<LoginLogDto> result = adminLogService.listLogs(
                1, 10, null, null, null, start, end);

        assertThat(result.getTotal()).isEqualTo(0);
    }

    @Test
    void shouldReturnEmptyWhenNoLogs() {
        Page<AuthUserLog> entityPage = new Page<>(1, 10, 0);

        when(authUserLogMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(entityPage);

        Page<LoginLogDto> result = adminLogService.listLogs(
                1, 10, null, null, null, null, null);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(0);
    }

    // ── listSecurityLogs ───────────────────────────────────────

    @Test
    void shouldListSecurityLogsWithPagination() {
        AuthUserLog log = makeLog(1L, "LOGIN", "SUCCESS", "PASSWORD", "admin");

        Page<AuthUserLog> entityPage = new Page<>(1, 10, 1);
        entityPage.setRecords(List.of(log));

        when(authUserLogMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(entityPage);

        Page<LoginLogDto> result = adminLogService.listSecurityLogs(
                1, 10, null, null, null, null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getAction()).isEqualTo("LOGIN");
    }

    @Test
    void shouldFilterSecurityLogsByType() {
        AuthUserLog log = makeLog(2L, "LOGOUT", "SUCCESS", null, "user");

        Page<AuthUserLog> entityPage = new Page<>(1, 20, 1);
        entityPage.setRecords(List.of(log));

        when(authUserLogMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(entityPage);

        Page<LoginLogDto> result = adminLogService.listSecurityLogs(
                1, 20, "LOGOUT", null, null, null);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getAction()).isEqualTo("LOGOUT");
    }

    @Test
    void shouldFilterSecurityLogsByIp() {
        AuthUserLog log = makeLog(3L, "CAPTCHA", "BLOCKED", null, "attacker");
        log.setAuthUserLogIp("203.0.113.1");

        Page<AuthUserLog> entityPage = new Page<>(1, 20, 1);
        entityPage.setRecords(List.of(log));

        when(authUserLogMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(entityPage);

        Page<LoginLogDto> result = adminLogService.listSecurityLogs(
                1, 20, null, "203.0.113.1", null, null);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getAction()).isEqualTo("CAPTCHA");
    }

    @Test
    void shouldFilterSecurityLogsByTimeRange() {
        Page<AuthUserLog> entityPage = new Page<>(1, 10, 1);
        AuthUserLog log = makeLog(4L, "RATE_LIMIT", "BLOCKED", null, "spammer");
        log.setCreatedTime(LocalDateTime.of(2026, 5, 15, 14, 0));
        entityPage.setRecords(List.of(log));

        when(authUserLogMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(entityPage);

        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 31, 23, 59);

        Page<LoginLogDto> result = adminLogService.listSecurityLogs(
                1, 10, null, null, start, end);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    void shouldFilterSecurityLogsWithAllParams() {
        Page<AuthUserLog> entityPage = new Page<>(1, 5, 0);

        when(authUserLogMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(entityPage);

        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 23, 23, 59);

        Page<LoginLogDto> result = adminLogService.listSecurityLogs(
                1, 5, "LOGIN", "192.168.1.1", start, end);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(0);
    }

    @Test
    void shouldReturnEmptyWhenNoSecurityLogs() {
        Page<AuthUserLog> entityPage = new Page<>(1, 10, 0);

        when(authUserLogMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(entityPage);

        Page<LoginLogDto> result = adminLogService.listSecurityLogs(
                1, 10, null, null, null, null);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(0);
    }

    @Test
    void shouldFilterLogsWithBlankUsername() {
        Page<AuthUserLog> entityPage = new Page<>(1, 10, 1);
        entityPage.setRecords(List.of(makeLog(1L, "LOGIN", "SUCCESS", "PASSWORD", "test")));

        when(authUserLogMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(entityPage);

        Page<LoginLogDto> result = adminLogService.listLogs(
                1, 10, "   ", null, null, null, null);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    void shouldFilterSecurityLogsWithBlankType() {
        Page<AuthUserLog> entityPage = new Page<>(1, 10, 1);
        entityPage.setRecords(List.of(makeLog(1L, "LOGIN", "SUCCESS", "PASSWORD", "user")));

        when(authUserLogMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(entityPage);

        Page<LoginLogDto> result = adminLogService.listSecurityLogs(
                1, 10, "   ", null, null, null);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    void shouldFilterLogsWithBlankType() {
        Page<AuthUserLog> entityPage = new Page<>(1, 10, 1);
        entityPage.setRecords(List.of(makeLog(1L, "LOGIN", "SUCCESS", "PASSWORD", "test")));

        when(authUserLogMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(entityPage);

        Page<LoginLogDto> result = adminLogService.listLogs(
                1, 10, null, "   ", null, null, null);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    void shouldFilterLogsWithBlankResult() {
        Page<AuthUserLog> entityPage = new Page<>(1, 10, 1);
        entityPage.setRecords(List.of(makeLog(1L, "LOGIN", "SUCCESS", "PASSWORD", "test")));

        when(authUserLogMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(entityPage);

        Page<LoginLogDto> result = adminLogService.listLogs(
                1, 10, null, null, "   ", null, null);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    void shouldFilterSecurityLogsWithBlankIp() {
        Page<AuthUserLog> entityPage = new Page<>(1, 10, 1);
        entityPage.setRecords(List.of(makeLog(1L, "LOGIN", "SUCCESS", "PASSWORD", "user")));

        when(authUserLogMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenReturn(entityPage);

        Page<LoginLogDto> result = adminLogService.listSecurityLogs(
                1, 10, null, "   ", null, null);

        assertThat(result.getRecords()).hasSize(1);
    }
}