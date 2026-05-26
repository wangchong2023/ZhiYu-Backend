package com.zhiyu.admin.service;

import com.zhiyu.admin.dto.AlertDto;
import com.zhiyu.admin.dto.HealthDto;
import com.zhiyu.admin.dto.LoggerDto;
import com.zhiyu.admin.dto.MetricsDto;
import com.zhiyu.ufp.common.monitor.CpuInfo;
import com.zhiyu.ufp.common.monitor.CpuInfoProvider;
import com.zhiyu.ufp.common.monitor.MemoryInfo;
import com.zhiyu.ufp.common.monitor.MemoryInfoProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
import org.springframework.boot.logging.LogLevel;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMonitorService {

    private final HealthEndpoint healthEndpoint;
    private final LoggersEndpoint loggersEndpoint;
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${alertmanager.url:http://localhost:9093}")
    private String alertmanagerUrl;

    private static final Set<String> SKIP_HEALTH_KEYS =
            Set.of("ping", "livenessState", "readinessState",
                   "discoveryComposite", "refresh", "configServer");

    private static final String COMPONENT_APP = "app";
    private static final String COMPONENT_DB = "db";
    private static final String DB_HEALTH_QUERY = "SELECT 1";
    private static final String NULL_DISPLAY = "null";

    // AlertManager API field names
    private static final String AM_LABELS = "labels";
    private static final String AM_ANNOTATIONS = "annotations";
    private static final String AM_STATE = "state";
    private static final String AM_SEVERITY = "severity";
    private static final String AM_ALERTNAME = "alertname";
    private static final String AM_SUMMARY = "summary";
    private static final String AM_DESCRIPTION = "description";
    private static final String AM_STARTS_AT = "startsAt";
    private static final String DEFAULT_ALERT_STATE = "firing";
    private static final String DEFAULT_ALERT_SEVERITY = "P2";
    private static final String DEFAULT_ALERT_NAME = "unknown";

    public List<HealthDto> getHealth() {
        List<HealthDto> list = new ArrayList<>();
        HealthComponent health = healthEndpoint.health();
        Status appStatus = health.getStatus();

        list.add(HealthDto.builder()
                .component(COMPONENT_APP)
                .status(appStatus.getCode())
                .instanceCount(1)
                .build());

        boolean hasDb = false;
        if (health instanceof CompositeHealth composite) {
            for (Map.Entry<String, HealthComponent> entry :
                    composite.getComponents().entrySet()) {
                if (SKIP_HEALTH_KEYS.contains(entry.getKey())) {
                    continue;
                }
                if (COMPONENT_DB.equals(entry.getKey())) {
                    hasDb = true;
                }
                extractComponentHealth(list, entry.getKey(), entry.getValue());
            }
        } else if (health instanceof Health simple) {
            extractComponentHealth(list, COMPONENT_APP, simple);
        }

        if (!hasDb) {
            try {
                jdbcTemplate.queryForObject(DB_HEALTH_QUERY, Long.class);
                list.add(HealthDto.builder()
                        .component(COMPONENT_DB)
                        .status(Status.UP.getCode())
                        .instanceCount(1)
                        .responseTimeMs(0L)
                        .build());
            } catch (Exception e) {
                list.add(HealthDto.builder()
                        .component(COMPONENT_DB).status(Status.DOWN.getCode()).instanceCount(0).build());
            }
        }

        return list;
    }

    public MetricsDto getMetrics() {
        CpuInfo cpu = CpuInfoProvider.snapshot();
        MemoryInfo mem = MemoryInfoProvider.snapshot();

        return MetricsDto.builder()
                .processCpuLoad(cpu.getProcessCpuLoad())
                .systemCpuLoad(cpu.getSystemCpuLoad())
                .cpuCores(cpu.getCpuCores())
                .threadCount(cpu.getThreadCount())
                .peakThreadCount(cpu.getPeakThreadCount())
                .processUptimeMs(cpu.getProcessUptimeMs())
                .rssBytes(mem.getRssBytes())
                .heapUsedBytes(mem.getHeapUsedBytes())
                .heapMaxBytes(mem.getHeapMaxBytes())
                .systemMemoryTotal(mem.getSystemMemoryTotal())
                .systemMemoryFree(mem.getSystemMemoryFree())
                .build();
    }

    public List<AlertDto> getAlerts(final String status, final String severity,
                                     final String startTime, final String endTime) {
        try {
            String url = alertmanagerUrl + "/api/v2/alerts";
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() { });
            List<Map<String, Object>> alerts = resp.getBody();
            if (alerts == null) {
                return List.of();
            }
            return alerts.stream()
                    .map(a -> {
                        Map<String, Object> labels =
                                (Map<String, Object>) a.getOrDefault(AM_LABELS, Map.of());
                        Map<String, Object> annotations =
                                (Map<String, Object>) a.getOrDefault(AM_ANNOTATIONS, Map.of());
                        String alertStatus = String.valueOf(
                                a.getOrDefault(AM_STATE, DEFAULT_ALERT_STATE)).toUpperCase(Locale.ROOT);
                        String alertSeverity = String.valueOf(
                                labels.getOrDefault(AM_SEVERITY, DEFAULT_ALERT_SEVERITY));
                        if (status != null && !status.isBlank()
                                && !alertStatus.equalsIgnoreCase(status)) {
                            return null;
                        }
                        if (severity != null && !severity.isBlank()
                                && !alertSeverity.equalsIgnoreCase(severity)) {
                            return null;
                        }
                        return AlertDto.builder()
                                .alertName(String.valueOf(labels.getOrDefault(
                                        AM_ALERTNAME, DEFAULT_ALERT_NAME)))
                                .severity(alertSeverity)
                                .condition(String.valueOf(annotations.getOrDefault(
                                        AM_SUMMARY, "")))
                                .currentValue(String.valueOf(annotations.getOrDefault(
                                        AM_DESCRIPTION, "")))
                                .status(alertStatus)
                                .firedAt(String.valueOf(a.getOrDefault(AM_STARTS_AT, "")))
                                .build();
                    })
                    .filter(a -> a != null)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<LoggerDto> getLoggers() {
        LoggersEndpoint.LoggersDescriptor descriptor = loggersEndpoint.loggers();
        if (descriptor == null || descriptor.getLoggers() == null) {
            return List.of();
        }
        return descriptor.getLoggers().entrySet().stream()
                .map(e -> {
                    String effectiveLevel = e.getValue().getConfiguredLevel();
                    if (e.getValue() instanceof LoggersEndpoint.SingleLoggerLevelsDescriptor s) {
                        effectiveLevel = s.getEffectiveLevel();
                    }
                    return LoggerDto.builder()
                            .name(e.getKey())
                            .configuredLevel(e.getValue().getConfiguredLevel() != null
                                    ? e.getValue().getConfiguredLevel() : NULL_DISPLAY)
                            .effectiveLevel(effectiveLevel != null
                                    ? effectiveLevel : NULL_DISPLAY)
                            .build();
                })
                .toList();
    }

    public void setLoggerLevel(final String name, final String level) {
        LogLevel logLevel = LogLevel.valueOf(level.toUpperCase(Locale.ROOT));
        loggersEndpoint.configureLogLevel(name, logLevel);
    }

    private void extractComponentHealth(final List<HealthDto> list,
                                         final String key,
                                         final HealthComponent component) {
        if (component instanceof Health h) {
            list.add(HealthDto.builder()
                    .component(key)
                    .status(h.getStatus().getCode())
                    .instanceCount(1)
                    .detail(h.getDetails().isEmpty()
                            ? null : h.getDetails().toString())
                    .build());
        } else {
            list.add(HealthDto.builder()
                    .component(key)
                    .status(component.getStatus().getCode())
                    .instanceCount(1)
                    .build());
        }
    }
}
