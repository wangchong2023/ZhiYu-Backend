package com.zhiyu.admin.service;

import com.zhiyu.admin.dto.AlertDto;
import com.zhiyu.admin.dto.HealthDto;
import com.zhiyu.admin.dto.LoggerDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;
import org.springframework.core.ParameterizedTypeReference;
import org.mockito.ArgumentMatchers;
import java.util.HashMap;
import java.util.ArrayList;
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

    /**
     * 描述: 测试从 Alertmanager 成功拉取警报数据列表，并验证不带过滤参数时的全量解析映射是否正确。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnAlertsSuccessfullyWhenAlertmanagerResponds() {
        // 1. 构建 Mock 的 RestTemplate 实例
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(adminMonitorService, "restTemplate", mockRestTemplate);

        // 2. 构造 Alertmanager 模拟返回的数据结构
        List<Map<String, Object>> mockAlerts = new ArrayList<>();
        
        // 警报记录 1：所有字段齐全
        Map<String, Object> alert1 = new HashMap<>();
        alert1.put("state", "firing");
        alert1.put("startsAt", "2026-05-28T10:00:00Z");
        
        Map<String, Object> labels1 = new HashMap<>();
        labels1.put("alertname", "CpuUsageHigh");
        labels1.put("severity", "P0");
        alert1.put("labels", labels1);
        
        Map<String, Object> annotations1 = new HashMap<>();
        annotations1.put("summary", "CPU load too high");
        annotations1.put("description", "CPU > 90%");
        alert1.put("annotations", annotations1);
        
        mockAlerts.add(alert1);

        // 警报记录 2：缺失部分 labels 与 annotations（触发 toDto 内部 getOrDefault 兜底逻辑）
        Map<String, Object> alert2 = new HashMap<>();
        mockAlerts.add(alert2);

        ResponseEntity<List<Map<String, Object>>> responseEntity = ResponseEntity.ok(mockAlerts);

        // 3. Mock 拦截 exchange 动作并返回构造的响应
        when(mockRestTemplate.exchange(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(HttpMethod.GET),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        // 4. 执行业务调用
        List<AlertDto> result = adminMonitorService.getAlerts(null, null, null, null);

        // 5. 校验映射与兜底值是否完全匹配
        assertThat(result).hasSize(2);
        
        // 校验警报 1 映射
        AlertDto dto1 = result.get(0);
        assertThat(dto1.getAlertName()).isEqualTo("CpuUsageHigh");
        assertThat(dto1.getSeverity()).isEqualTo("P0");
        assertThat(dto1.getStatus()).isEqualTo("FIRING");
        assertThat(dto1.getCondition()).isEqualTo("CPU load too high");
        assertThat(dto1.getCurrentValue()).isEqualTo("CPU > 90%");
        assertThat(dto1.getFiredAt()).isEqualTo("2026-05-28T10:00:00Z");

        // 校验警报 2 兜底映射
        AlertDto dto2 = result.get(1);
        assertThat(dto2.getAlertName()).isEqualTo("unknown");
        assertThat(dto2.getSeverity()).isEqualTo("P2");
        assertThat(dto2.getStatus()).isEqualTo("FIRING"); // 默认 firing
    }

    /**
     * 描述: 测试从 Alertmanager 拉取警报数据列表，并应用 status（状态）与 severity（等级）过滤器进行精准匹配。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldFilterAlertsByStatusAndSeverity() {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(adminMonitorService, "restTemplate", mockRestTemplate);

        List<Map<String, Object>> mockAlerts = new ArrayList<>();
        
        // 警报 1: firing, P0
        Map<String, Object> alert1 = new HashMap<>();
        alert1.put("state", "firing");
        Map<String, Object> labels1 = new HashMap<>();
        labels1.put("alertname", "Alert1");
        labels1.put("severity", "P0");
        alert1.put("labels", labels1);
        mockAlerts.add(alert1);

        // 警报 2: resolved, P1
        Map<String, Object> alert2 = new HashMap<>();
        alert2.put("state", "resolved");
        Map<String, Object> labels2 = new HashMap<>();
        labels2.put("alertname", "Alert2");
        labels2.put("severity", "P1");
        alert2.put("labels", labels2);
        mockAlerts.add(alert2);

        ResponseEntity<List<Map<String, Object>>> responseEntity = ResponseEntity.ok(mockAlerts);
        when(mockRestTemplate.exchange(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(HttpMethod.GET),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        // 验证只按状态过滤
        List<AlertDto> filterStatus = adminMonitorService.getAlerts("RESOLVED", null, null, null);
        assertThat(filterStatus).hasSize(1);
        assertThat(filterStatus.get(0).getAlertName()).isEqualTo("Alert2");

        // 验证只按等级过滤
        List<AlertDto> filterSeverity = adminMonitorService.getAlerts(null, "P0", null, null);
        assertThat(filterSeverity).hasSize(1);
        assertThat(filterSeverity.get(0).getAlertName()).isEqualTo("Alert1");

        // 验证双重过滤匹配
        List<AlertDto> filterBoth = adminMonitorService.getAlerts("FIRING", "P0", null, null);
        assertThat(filterBoth).hasSize(1);
        assertThat(filterBoth.get(0).getAlertName()).isEqualTo("Alert1");

        // 验证无任何匹配
        List<AlertDto> filterNone = adminMonitorService.getAlerts("RESOLVED", "P0", null, null);
        assertThat(filterNone).isEmpty();
    }

    /**
     * 描述: 测试当 Alertmanager 接口发生 RestTemplate 调用异常（例如网络连接超时或服务宕机）时，
     *       系统能够健壮防御抛错并优雅降级返回空列表，满足 TSK-P1-002 网络异常兜底单元测试规范。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldHandleAlertsGracefullyWhenRestTemplateThrowsException() {
        // 1. 构建 Mock 的 RestTemplate 实例
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(adminMonitorService, "restTemplate", mockRestTemplate);

        // 2. 模拟 RestTemplate.exchange 抛出 RestClientException 异常（例如网络连接被拒绝）
        when(mockRestTemplate.exchange(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(HttpMethod.GET),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.any(ParameterizedTypeReference.class)
        )).thenThrow(new RestClientException("Connection refused to Alertmanager"));

        // 3. 执行调用并校验是否防崩溃优雅降级
        List<AlertDto> result = adminMonitorService.getAlerts(null, null, null, null);

        // 4. 确认返回空列表，表示成功兜底
        assertThat(result).isEmpty();
    }
}
