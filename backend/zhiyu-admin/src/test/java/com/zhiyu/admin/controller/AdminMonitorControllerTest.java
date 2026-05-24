package com.zhiyu.admin.controller;

import com.zhiyu.admin.dto.AlertDto;
import com.zhiyu.admin.dto.HealthDto;
import com.zhiyu.admin.dto.LoggerDto;
import com.zhiyu.admin.dto.MetricsDto;
import com.zhiyu.admin.service.AdminMonitorService;
import com.zhiyu.common.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMonitorControllerTest {

    @Mock
    private AdminMonitorService adminMonitorService;

    @InjectMocks
    private AdminMonitorController adminMonitorController;

    // ─── health ───

    @Test
    void shouldReturnHealthList() {
        List<HealthDto> healthList = List.of(
                HealthDto.builder().component("应用实例").status("UP").instanceCount(1).build(),
                HealthDto.builder().component("数据库").status("UP").instanceCount(1)
                        .responseTimeMs(5L).build(),
                HealthDto.builder().component("Redis").status("UP").instanceCount(1).build());

        when(adminMonitorService.getHealth()).thenReturn(healthList);

        ApiResponse<List<HealthDto>> response = adminMonitorController.health();

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).hasSize(3);
        assertThat(response.getData().get(0).getComponent()).isEqualTo("应用实例");
        assertThat(response.getData().get(0).getStatus()).isEqualTo("UP");
        assertThat(response.getData().get(1).getResponseTimeMs()).isEqualTo(5L);

        verify(adminMonitorService).getHealth();
        verifyNoMoreInteractions(adminMonitorService);
    }

    @Test
    void shouldReturnEmptyHealthListWhenNoComponents() {
        when(adminMonitorService.getHealth()).thenReturn(List.of());

        ApiResponse<List<HealthDto>> response = adminMonitorController.health();

        assertThat(response.getData()).isEmpty();
        verify(adminMonitorService).getHealth();
    }

    @Test
    void shouldReturnDegradedHealth() {
        List<HealthDto> healthList = List.of(
                HealthDto.builder().component("应用实例").status("UP").instanceCount(1).build(),
                HealthDto.builder().component("数据库").status("DOWN").instanceCount(0).build());

        when(adminMonitorService.getHealth()).thenReturn(healthList);

        ApiResponse<List<HealthDto>> response = adminMonitorController.health();

        assertThat(response.getData().get(1).getStatus()).isEqualTo("DOWN");
        verify(adminMonitorService).getHealth();
    }

    // ─── metrics ───

    @Test
    void shouldReturnMetricsWithDefaultRange() {
        MetricsDto.MetricPoint point = MetricsDto.MetricPoint.builder()
                .timestamp(1716019200L).value(42.5).build();
        MetricsDto metricsDto = MetricsDto.builder()
                .qps(List.of(point)).latencyP50(List.of(point))
                .latencyP95(List.of()).latencyP99(List.of())
                .errorRate(List.of()).build();

        when(adminMonitorService.getMetrics("24h")).thenReturn(metricsDto);

        ApiResponse<MetricsDto> response = adminMonitorController.metrics("24h");

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData().getQps()).hasSize(1);
        assertThat(response.getData().getQps().get(0).getValue()).isEqualTo(42.5);
        assertThat(response.getData().getLatencyP95()).isEmpty();

        verify(adminMonitorService).getMetrics("24h");
    }

    @Test
    void shouldReturnMetricsFor1hRange() {
        MetricsDto metricsDto = MetricsDto.builder()
                .qps(List.of()).latencyP50(List.of())
                .latencyP95(List.of()).latencyP99(List.of())
                .errorRate(List.of()).build();

        when(adminMonitorService.getMetrics("1h")).thenReturn(metricsDto);

        ApiResponse<MetricsDto> response = adminMonitorController.metrics("1h");

        assertThat(response.getData()).isNotNull();
        verify(adminMonitorService).getMetrics("1h");
    }

    @Test
    void shouldReturnMetricsFor7dRange() {
        MetricsDto metricsDto = MetricsDto.builder()
                .qps(List.of()).latencyP50(List.of())
                .latencyP95(List.of()).latencyP99(List.of())
                .errorRate(List.of()).build();

        when(adminMonitorService.getMetrics("7d")).thenReturn(metricsDto);

        ApiResponse<MetricsDto> response = adminMonitorController.metrics("7d");

        assertThat(response.getData()).isNotNull();
        verify(adminMonitorService).getMetrics("7d");
    }

    // ─── alerts ───

    @Test
    void shouldReturnAlertsWithoutFilters() {
        List<AlertDto> alerts = List.of(
                AlertDto.builder().alertName("HighCPU").severity("P1")
                        .status("FIRING").firedAt("2026-05-23T08:00:00Z").build(),
                AlertDto.builder().alertName("HighMemory").severity("P2")
                        .status("RESOLVED").firedAt("2026-05-22T10:00:00Z").build());

        when(adminMonitorService.getAlerts(null, null, null, null)).thenReturn(alerts);

        ApiResponse<List<AlertDto>> response = adminMonitorController.alerts(null, null, null, null);

        assertThat(response.getData()).hasSize(2);
        assertThat(response.getData().get(0).getAlertName()).isEqualTo("HighCPU");
        assertThat(response.getData().get(1).getStatus()).isEqualTo("RESOLVED");

        verify(adminMonitorService).getAlerts(null, null, null, null);
    }

    @Test
    void shouldReturnAlertsFilteredByStatusAndSeverity() {
        List<AlertDto> alerts = List.of(
                AlertDto.builder().alertName("CriticalAlert").severity("P0")
                        .status("FIRING").firedAt("2026-05-23T08:00:00Z").build());

        when(adminMonitorService.getAlerts("FIRING", "P0", null, null)).thenReturn(alerts);

        ApiResponse<List<AlertDto>> response = adminMonitorController.alerts("FIRING", "P0", null, null);

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getSeverity()).isEqualTo("P0");

        verify(adminMonitorService).getAlerts("FIRING", "P0", null, null);
    }

    @Test
    void shouldReturnAlertsWithTimeRange() {
        List<AlertDto> alerts = List.of();
        when(adminMonitorService.getAlerts(null, null, "2026-05-22", "2026-05-23"))
                .thenReturn(alerts);

        ApiResponse<List<AlertDto>> response =
                adminMonitorController.alerts(null, null, "2026-05-22", "2026-05-23");

        assertThat(response.getData()).isEmpty();
        verify(adminMonitorService).getAlerts(null, null, "2026-05-22", "2026-05-23");
    }

    // ─── recentAlerts ───

    @Test
    void shouldReturnRecentAlerts() {
        List<AlertDto> recentAlerts = List.of(
                AlertDto.builder().alertName("HighCPU").severity("P1")
                        .status("FIRING").firedAt("2026-05-23T08:00:00Z").build());

        when(adminMonitorService.getAlerts("firing", null, null, null)).thenReturn(recentAlerts);

        ApiResponse<List<AlertDto>> response = adminMonitorController.recentAlerts();

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getStatus()).isEqualTo("FIRING");

        verify(adminMonitorService).getAlerts("firing", null, null, null);
        verifyNoMoreInteractions(adminMonitorService);
    }

    // ─── loggers ───

    @Test
    void shouldReturnLoggersList() {
        List<LoggerDto> loggers = List.of(
                LoggerDto.builder().name("com.zhiyu").configuredLevel("INFO")
                        .effectiveLevel("INFO").build(),
                LoggerDto.builder().name("org.springframework").configuredLevel("WARN")
                        .effectiveLevel("WARN").build());

        when(adminMonitorService.getLoggers()).thenReturn(loggers);

        ApiResponse<List<LoggerDto>> response = adminMonitorController.loggers();

        assertThat(response.getData()).hasSize(2);
        assertThat(response.getData().get(0).getName()).isEqualTo("com.zhiyu");
        assertThat(response.getData().get(1).getConfiguredLevel()).isEqualTo("WARN");

        verify(adminMonitorService).getLoggers();
        verifyNoMoreInteractions(adminMonitorService);
    }

    @Test
    void shouldReturnEmptyLoggersList() {
        when(adminMonitorService.getLoggers()).thenReturn(List.of());

        ApiResponse<List<LoggerDto>> response = adminMonitorController.loggers();

        assertThat(response.getData()).isEmpty();
        verify(adminMonitorService).getLoggers();
    }

    // ─── setLoggerLevel ───

    @Test
    void shouldSetLoggerLevelSuccessfully() {
        Map<String, String> body = Map.of("configuredLevel", "DEBUG");

        ApiResponse<Void> response = adminMonitorController.setLoggerLevel("com.zhiyu.admin", body);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isNull();

        verify(adminMonitorService).setLoggerLevel("com.zhiyu.admin", "DEBUG");
        verifyNoMoreInteractions(adminMonitorService);
    }

    @Test
    void shouldSetLoggerLevelToWarn() {
        Map<String, String> body = Map.of("configuredLevel", "WARN");

        ApiResponse<Void> response = adminMonitorController.setLoggerLevel("org.springframework", body);

        assertThat(response.getCode()).isEqualTo(0);
        verify(adminMonitorService).setLoggerLevel("org.springframework", "WARN");
    }

    @Test
    void shouldSetLoggerLevelToOff() {
        Map<String, String> body = Map.of("configuredLevel", "OFF");

        ApiResponse<Void> response = adminMonitorController.setLoggerLevel("com.zhiyu.noisy", body);

        assertThat(response.getCode()).isEqualTo(0);
        verify(adminMonitorService).setLoggerLevel("com.zhiyu.noisy", "OFF");
    }

    @Test
    void shouldPassNullLevelWhenKeyMissing() {
        Map<String, String> body = Map.of();

        ApiResponse<Void> response = adminMonitorController.setLoggerLevel("com.zhiyu.admin", body);

        assertThat(response.getCode()).isEqualTo(0);
        verify(adminMonitorService).setLoggerLevel(eq("com.zhiyu.admin"), isNull());
    }

    // ─── loggerHistory ───

    @Test
    void shouldReturnEmptyLoggerHistory() {
        // This endpoint does not use the service; it returns List.of() directly.
        ApiResponse<List<LoggerDto.LogLevelHistoryDto>> response =
                adminMonitorController.loggerHistory();

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isEmpty();
        verifyNoInteractions(adminMonitorService);
    }
}
