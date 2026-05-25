package com.zhiyu.admin.service;

import com.zhiyu.admin.dto.AlertDto;
import com.zhiyu.admin.dto.HealthDto;
import com.zhiyu.admin.dto.LoggerDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
import org.springframework.boot.actuate.logging.LoggersEndpoint.LoggerLevelsDescriptor;
import org.springframework.boot.actuate.logging.LoggersEndpoint.LoggersDescriptor;
import org.springframework.boot.actuate.logging.LoggersEndpoint.SingleLoggerLevelsDescriptor;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMonitorServiceTest {

    @Mock private HealthEndpoint healthEndpoint;
    @Mock private LoggersEndpoint loggersEndpoint;
    @Mock private JdbcTemplate jdbcTemplate;
    @InjectMocks private AdminMonitorService adminMonitorService;

    // ── getHealth ─────────────────────────────────────────────

    @Test
    void shouldReturnAppAndDbHealthWhenUp() {
        Health health = Health.up().build();
        when(healthEndpoint.health()).thenReturn(health);
        when(jdbcTemplate.queryForObject("SELECT 1", Long.class)).thenReturn(1L);

        List<HealthDto> result = adminMonitorService.getHealth();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getComponent()).isEqualTo("app");
        assertThat(result.get(0).getStatus()).isEqualTo("UP");
        assertThat(result.get(2).getComponent()).isEqualTo("db");
        assertThat(result.get(2).getStatus()).isEqualTo("UP");
    }

    @Test
    void shouldReportDbDownWhenQueryFails() {
        Health health = Health.up().build();
        when(healthEndpoint.health()).thenReturn(health);
        when(jdbcTemplate.queryForObject("SELECT 1", Long.class))
                .thenThrow(new RuntimeException("connection refused"));

        List<HealthDto> result = adminMonitorService.getHealth();

        HealthDto db = result.stream()
                .filter(h -> "db".equals(h.getComponent())).findFirst().orElseThrow();
        assertThat(db.getStatus()).isEqualTo("DOWN");
        assertThat(db.getInstanceCount()).isEqualTo(0);
    }

    @Test
    void shouldReportAppStatusDown() {
        Health health = Health.down().build();
        when(healthEndpoint.health()).thenReturn(health);
        when(jdbcTemplate.queryForObject("SELECT 1", Long.class)).thenReturn(1L);

        List<HealthDto> result = adminMonitorService.getHealth();

        assertThat(result.get(0).getStatus()).isEqualTo("DOWN");
    }

    @Test
    void shouldHandleUnknownComponentKey() {
        Health health = Health.up().build();
        when(healthEndpoint.health()).thenReturn(health);
        when(jdbcTemplate.queryForObject("SELECT 1", Long.class)).thenReturn(1L);

        List<HealthDto> result = adminMonitorService.getHealth();

        HealthDto app = result.stream()
                .filter(h -> "app".equals(h.getComponent())).findFirst().orElseThrow();
        assertThat(app.getStatus()).isEqualTo("UP");
    }

    // ── getLoggers ────────────────────────────────────────────

    @Test
    void shouldReturnLoggersFromActuator() {
        LoggerLevelsDescriptor rootDesc = new LoggerLevelsDescriptor(LogLevel.INFO);
        SingleLoggerLevelsDescriptor appDesc = new SingleLoggerLevelsDescriptor(
                new LoggerConfiguration("com.zhiyu", LogLevel.DEBUG, LogLevel.DEBUG));

        Map<String, LoggerLevelsDescriptor> loggerMap = new LinkedHashMap<>();
        loggerMap.put("ROOT", rootDesc);
        loggerMap.put("com.zhiyu", appDesc);

        LoggersDescriptor descriptor = new LoggersDescriptor(
                new TreeSet<>(), loggerMap, Map.of());

        when(loggersEndpoint.loggers()).thenReturn(descriptor);

        List<LoggerDto> result = adminMonitorService.getLoggers();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("ROOT");
        assertThat(result.get(0).getEffectiveLevel()).isEqualTo("INFO");
        assertThat(result.get(1).getName()).isEqualTo("com.zhiyu");
        assertThat(result.get(1).getEffectiveLevel()).isEqualTo("DEBUG");
    }

    @Test
    void shouldReturnEmptyListWhenNoLoggers() {
        when(loggersEndpoint.loggers()).thenReturn(null);

        List<LoggerDto> result = adminMonitorService.getLoggers();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenLoggerMapIsNull() {
        LoggersDescriptor descriptor = new LoggersDescriptor(
                new TreeSet<>(), null, Map.of());
        when(loggersEndpoint.loggers()).thenReturn(descriptor);

        List<LoggerDto> result = adminMonitorService.getLoggers();

        assertThat(result).isEmpty();
    }

    // ── setLoggerLevel ────────────────────────────────────────

    @Test
    void shouldSetLoggerLevel() {
        adminMonitorService.setLoggerLevel("com.zhiyu", "DEBUG");

        verify(loggersEndpoint).configureLogLevel("com.zhiyu", LogLevel.DEBUG);
    }

    // ── getHealth with CompositeHealth ─────────────────────────

    @Test
    void shouldExtractComponentsFromCompositeHealth() {
        CompositeHealth composite = mock(CompositeHealth.class);
        when(composite.getStatus()).thenReturn(Status.UP);
        Map<String, HealthComponent> components = new LinkedHashMap<>();
        components.put("redis", Health.up().withDetail("version", "7.0").build());
        components.put("nacos", Health.up().build());
        components.put("diskSpace", Health.up().withDetail("free", "50GB").build());
        components.put("db", Health.up().build());
        components.put("customPlugin", Health.down().build());
        when(composite.getComponents()).thenReturn(components);
        when(healthEndpoint.health()).thenReturn(composite);

        List<HealthDto> result = adminMonitorService.getHealth();

        assertThat(result).hasSize(6);
        assertThat(result.get(0).getComponent()).isEqualTo("app");

        HealthDto redis = result.stream()
                .filter(h -> "redis".equals(h.getComponent())).findFirst().orElseThrow();
        assertThat(redis.getStatus()).isEqualTo("UP");
        assertThat(redis.getDetail()).isNotNull();

        HealthDto custom = result.stream()
                .filter(h -> "customPlugin".equals(h.getComponent())).findFirst().orElseThrow();
        assertThat(custom.getStatus()).isEqualTo("DOWN");
    }

    @Test
    void shouldExtractNonHealthComponent() {
        CompositeHealth composite = mock(CompositeHealth.class);
        when(composite.getStatus()).thenReturn(Status.UP);
        Map<String, HealthComponent> components = new LinkedHashMap<>();
        HealthComponent unknownMock = mock(HealthComponent.class);
        when(unknownMock.getStatus()).thenReturn(Status.UP);
        components.put("unknownType", unknownMock);
        when(composite.getComponents()).thenReturn(components);
        when(healthEndpoint.health()).thenReturn(composite);
        when(jdbcTemplate.queryForObject("SELECT 1", Long.class)).thenReturn(1L);

        List<HealthDto> result = adminMonitorService.getHealth();

        HealthDto unknown = result.stream()
                .filter(h -> "unknownType".equals(h.getComponent())).findFirst().orElseThrow();
        assertThat(unknown.getStatus()).isEqualTo("UP");
        assertThat(unknown.getInstanceCount()).isEqualTo(1);
    }

    // ── getMetrics ────────────────────────────────────────────

    @Test
    void shouldReturnMetricsWithHeapMax() {
        var result = adminMonitorService.getMetrics();

        assertThat(result).isNotNull();
        assertThat(result.getHeapMaxBytes()).isGreaterThan(0);
    }

    @Test
    void shouldReturnMetricsWithSystemMemory() {
        var result = adminMonitorService.getMetrics();

        assertThat(result).isNotNull();
        assertThat(result.getSystemMemoryTotal()).isGreaterThan(0);
        assertThat(result.getSystemMemoryFree()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldReturnMetricsWithProcessCpuLoad() {
        var result = adminMonitorService.getMetrics();

        assertThat(result).isNotNull();
        assertThat(result.getProcessCpuLoad()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void shouldReturnMetricsWithSystemCpuLoad() {
        var result = adminMonitorService.getMetrics();

        assertThat(result).isNotNull();
        assertThat(result.getSystemCpuLoad()).isGreaterThanOrEqualTo(0.0);
        assertThat(result.getSystemCpuLoad()).isLessThanOrEqualTo(1.0);
    }

    @Test
    void shouldReturnMetricsWithMemoryFields() {
        var result = adminMonitorService.getMetrics();

        assertThat(result).isNotNull();
        assertThat(result.getRssBytes()).isGreaterThan(0);
        assertThat(result.getHeapUsedBytes()).isGreaterThan(0);
    }

    // ── getAlerts ──────────────────────────────────────────────

    @Test
    void shouldReturnAlertsWithoutFilters() {
        List<AlertDto> result = adminMonitorService.getAlerts(null, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnAlertsWithStatusFilter() {
        List<AlertDto> result = adminMonitorService.getAlerts("FIRING", null, null, null);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnAlertsWithSeverityFilter() {
        List<AlertDto> result = adminMonitorService.getAlerts(null, "P0", null, null);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnAlertsWithBothFilters() {
        List<AlertDto> result = adminMonitorService.getAlerts("FIRING", "P1", null, null);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnAlertsWithTimeRange() {
        List<AlertDto> result = adminMonitorService.getAlerts(
                null, null, "2026-05-01", "2026-05-23");

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnAlertsWithAllFilters() {
        List<AlertDto> result = adminMonitorService.getAlerts(
                "RESOLVED", "P2", "2026-05-22", "2026-05-23");

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    // ── getLoggers with null levels ────────────────────────────

    @Test
    void shouldReturnLoggersWithNullConfiguredLevel() {
        LoggerLevelsDescriptor nullDesc = mock(LoggerLevelsDescriptor.class);
        when(nullDesc.getConfiguredLevel()).thenReturn(null);

        Map<String, LoggerLevelsDescriptor> loggerMap = new LinkedHashMap<>();
        loggerMap.put("null.logger", nullDesc);

        LoggersDescriptor descriptor = new LoggersDescriptor(
                new TreeSet<>(), loggerMap, Map.of());
        when(loggersEndpoint.loggers()).thenReturn(descriptor);

        List<LoggerDto> result = adminMonitorService.getLoggers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getConfiguredLevel()).isEqualTo("null");
        assertThat(result.get(0).getEffectiveLevel()).isEqualTo("null");
    }

    @Test
    void shouldReturnLoggersWithNullEffectiveLevel() {
        SingleLoggerLevelsDescriptor singleDesc = mock(SingleLoggerLevelsDescriptor.class);
        when(singleDesc.getConfiguredLevel()).thenReturn("INFO");
        when(singleDesc.getEffectiveLevel()).thenReturn(null);

        Map<String, LoggerLevelsDescriptor> loggerMap = new LinkedHashMap<>();
        loggerMap.put("test.logger", singleDesc);

        LoggersDescriptor descriptor = new LoggersDescriptor(
                new TreeSet<>(), loggerMap, Map.of());
        when(loggersEndpoint.loggers()).thenReturn(descriptor);

        List<LoggerDto> result = adminMonitorService.getLoggers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getConfiguredLevel()).isEqualTo("INFO");
        assertThat(result.get(0).getEffectiveLevel()).isEqualTo("null");
    }

    @Test
    void shouldReturnLoggersMixedWithPlainAndSingleDescriptors() {
        LoggerLevelsDescriptor plainDesc = new LoggerLevelsDescriptor(LogLevel.WARN);
        SingleLoggerLevelsDescriptor singleDesc = new SingleLoggerLevelsDescriptor(
                new LoggerConfiguration("app", LogLevel.INFO, LogLevel.DEBUG));

        Map<String, LoggerLevelsDescriptor> loggerMap = new LinkedHashMap<>();
        loggerMap.put("ROOT", plainDesc);
        loggerMap.put("app", singleDesc);

        LoggersDescriptor descriptor = new LoggersDescriptor(
                new TreeSet<>(), loggerMap, Map.of());
        when(loggersEndpoint.loggers()).thenReturn(descriptor);

        List<LoggerDto> result = adminMonitorService.getLoggers();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEffectiveLevel()).isEqualTo("WARN");
        assertThat(result.get(1).getEffectiveLevel()).isEqualTo("DEBUG");
    }

    // ── setLoggerLevel edge cases ──────────────────────────────

    @Test
    void shouldSetLoggerLevelToTrace() {
        adminMonitorService.setLoggerLevel("com.example", "TRACE");

        verify(loggersEndpoint).configureLogLevel("com.example", LogLevel.TRACE);
    }

    @Test
    void shouldSetLoggerLevelToError() {
        adminMonitorService.setLoggerLevel("com.example", "ERROR");

        verify(loggersEndpoint).configureLogLevel("com.example", LogLevel.ERROR);
    }

    // ── getHealth with Down app and Db up ──────────────────────

    @Test
    void shouldReportAppDownDbUp() {
        Health health = Health.down().withDetail("reason", "OOM").build();
        when(healthEndpoint.health()).thenReturn(health);
        when(jdbcTemplate.queryForObject("SELECT 1", Long.class)).thenReturn(1L);

        List<HealthDto> result = adminMonitorService.getHealth();

        HealthDto app = result.stream()
                .filter(h -> "app".equals(h.getComponent())).findFirst().orElseThrow();
        assertThat(app.getStatus()).isEqualTo("DOWN");
    }

    @Test
    void shouldReportAppUpWithUnknownLabel() {
        Health health = Health.up().withDetail("custom", "value").build();
        when(healthEndpoint.health()).thenReturn(health);
        when(jdbcTemplate.queryForObject("SELECT 1", Long.class)).thenReturn(1L);

        List<HealthDto> result = adminMonitorService.getHealth();

        List<HealthDto> appEntries = result.stream()
                .filter(h -> "app".equals(h.getComponent())).toList();
        assertThat(appEntries).hasSize(2);
        assertThat(appEntries.get(0).getStatus()).isEqualTo("UP");
        assertThat(appEntries.get(1).getDetail()).isNotNull();
    }

    // ── Process resource metrics ─────────────

    @Test
    void shouldReturnProcessResourceMetrics() {
        var result = adminMonitorService.getMetrics();

        assertThat(result).isNotNull();
        assertThat(result.getCpuCores()).isGreaterThan(0);
        assertThat(result.getThreadCount()).isGreaterThan(0);
        assertThat(result.getPeakThreadCount()).isGreaterThan(0);
        assertThat(result.getProcessUptimeMs()).isGreaterThan(0);
        assertThat(result.getHeapMaxBytes()).isGreaterThan(0);
        assertThat(result.getSystemMemoryTotal()).isGreaterThan(0);
    }

    @Test
    void shouldHandleAlertsWhenAlertManagerUrlIsNull() {
        List<AlertDto> result = adminMonitorService.getAlerts(
                "FIRING", "P0", "2026-05-01", "2026-05-23");

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnMetricsWithConsistentValues() {
        var result1 = adminMonitorService.getMetrics();
        var result2 = adminMonitorService.getMetrics();

        assertThat(result1.getCpuCores()).isEqualTo(result2.getCpuCores());
        assertThat(result1.getHeapMaxBytes()).isEqualTo(result2.getHeapMaxBytes());
        assertThat(result1.getSystemMemoryTotal()).isEqualTo(result2.getSystemMemoryTotal());
    }
}
