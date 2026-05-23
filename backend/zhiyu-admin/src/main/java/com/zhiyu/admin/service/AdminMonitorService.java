package com.zhiyu.admin.service;

import com.zhiyu.admin.dto.AlertDto;
import com.zhiyu.admin.dto.HealthDto;
import com.zhiyu.admin.dto.LoggerDto;
import com.zhiyu.admin.dto.MetricsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminMonitorService {

    private final HealthEndpoint healthEndpoint;
    private final LoggersEndpoint loggersEndpoint;
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${prometheus.url:http://localhost:9090}")
    private String prometheusUrl;

    @Value("${alertmanager.url:http://localhost:9093}")
    private String alertmanagerUrl;

    /**
     * 聚合 Actuator health 返回各组件健康状态
     */
    public List<HealthDto> getHealth() {
        List<HealthDto> list = new ArrayList<>();
        HealthComponent health = healthEndpoint.health();
        Status appStatus = health.getStatus();

        list.add(HealthDto.builder()
                .component("应用实例")
                .status(appStatus.getCode())
                .instanceCount(1)
                .build());

        // DB ping test
        try {
            jdbcTemplate.queryForObject("SELECT 1", Long.class);
            list.add(HealthDto.builder()
                    .component("数据库")
                    .status("UP")
                    .instanceCount(1)
                    .responseTimeMs(0L)
                    .build());
        } catch (Exception e) {
            list.add(HealthDto.builder()
                    .component("数据库").status("DOWN").instanceCount(0).build());
        }

        // Extract component health details from HealthComponent
        if (health instanceof CompositeHealth composite) {
            for (Map.Entry<String, HealthComponent> entry :
                    composite.getComponents().entrySet()) {
                extractComponentHealth(list, entry.getKey(), entry.getValue());
            }
        } else if (health instanceof Health simple) {
            extractComponentHealth(list, "应用", simple);
        }

        return list;
    }

    private void extractComponentHealth(List<HealthDto> list,
                                         String key, HealthComponent component) {
        String label = switch (key) {
            case "redis" -> "Redis";
            case "nacos" -> "Nacos";
            case "diskSpace" -> "磁盘";
            case "db" -> "数据库";
            default -> key;
        };
        if (component instanceof Health h) {
            list.add(HealthDto.builder()
                    .component(label)
                    .status(h.getStatus().getCode())
                    .instanceCount(1)
                    .detail(h.getDetails().isEmpty()
                            ? null : h.getDetails().toString())
                    .build());
        } else {
            list.add(HealthDto.builder()
                    .component(label)
                    .status(component.getStatus().getCode())
                    .instanceCount(1)
                    .build());
        }
    }

    /**
     * 从 Prometheus 查询 HTTP 请求指标
     */
    public MetricsDto getMetrics(String range) {
        String step = switch (range) {
            case "1h" -> "60s";
            case "6h" -> "300s";
            case "24h" -> "900s";
            case "7d" -> "3600s";
            default -> "300s";
        };

        long end = Instant.now().getEpochSecond();
        long start = end - switch (range) {
            case "1h" -> 3600;
            case "6h" -> 21600;
            case "7d" -> 604800;
            default -> 86400;
        };

        return MetricsDto.builder()
                .qps(queryPrometheus("rate(http_server_requests_seconds_count[5m])", start, end, step))
                .latencyP50(queryPrometheus("histogram_quantile(0.5, rate(http_server_requests_seconds_bucket[5m]))", start, end, step))
                .latencyP95(queryPrometheus("histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))", start, end, step))
                .latencyP99(queryPrometheus("histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))", start, end, step))
                .errorRate(queryPrometheus("rate(http_server_requests_seconds_count{status=~\"5..\"}[5m]) / rate(http_server_requests_seconds_count[5m])", start, end, step))
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<MetricsDto.MetricPoint> queryPrometheus(
            String query, long start, long end, String step) {
        try {
            String url = String.format(
                    "%s/api/v1/query_range?query=%s&start=%d&end=%d&step=%s",
                    prometheusUrl, query, start, end, step);
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null || !"success".equals(body.get("status"))) {
                return List.of();
            }
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            List<Map<String, Object>> results =
                    (List<Map<String, Object>>) data.get("result");
            if (results.isEmpty()) return List.of();
            List<List<Object>> values =
                    (List<List<Object>>) results.get(0).get("values");
            if (values == null) return List.of();
            return values.stream()
                    .map(v -> MetricsDto.MetricPoint.builder()
                            .timestamp(((Number) v.get(0)).longValue())
                            .value(Double.parseDouble(String.valueOf(v.get(1))))
                            .build())
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 从 AlertManager 查询告警
     */
    @SuppressWarnings("unchecked")
    public List<AlertDto> getAlerts(String status, String severity,
                                     String startTime, String endTime) {
        try {
            String url = alertmanagerUrl + "/api/v2/alerts";
            ResponseEntity<List> resp = restTemplate.getForEntity(url, List.class);
            List<Map<String, Object>> alerts = resp.getBody();
            if (alerts == null) return List.of();
            return alerts.stream()
                    .map(a -> {
                        Map<String, Object> labels =
                                (Map<String, Object>) a.getOrDefault("labels", Map.of());
                        Map<String, Object> annotations =
                                (Map<String, Object>) a.getOrDefault("annotations", Map.of());
                        String alertStatus = String.valueOf(
                                a.getOrDefault("state", "firing")).toUpperCase();
                        String alertSeverity = String.valueOf(
                                labels.getOrDefault("severity", "P2"));
                        // Apply filters
                        if (status != null && !status.isBlank()
                                && !alertStatus.equalsIgnoreCase(status)) return null;
                        if (severity != null && !severity.isBlank()
                                && !alertSeverity.equalsIgnoreCase(severity)) return null;
                        return AlertDto.builder()
                                .alertName(String.valueOf(labels.getOrDefault(
                                        "alertname", "unknown")))
                                .severity(alertSeverity)
                                .condition(String.valueOf(annotations.getOrDefault(
                                        "summary", "")))
                                .currentValue(String.valueOf(annotations.getOrDefault(
                                        "description", "")))
                                .status(alertStatus)
                                .firedAt(String.valueOf(a.getOrDefault("startsAt", "")))
                                .build();
                    })
                    .filter(a -> a != null)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 从 Actuator loggers endpoint 获取 logger 列表
     */
    public List<LoggerDto> getLoggers() {
        LoggersEndpoint.LoggersDescriptor descriptor = loggersEndpoint.loggers();
        if (descriptor == null || descriptor.getLoggers() == null) return List.of();
        return descriptor.getLoggers().entrySet().stream()
                .map(e -> {
                    String effectiveLevel = e.getValue().getConfiguredLevel();
                    if (e.getValue() instanceof LoggersEndpoint.SingleLoggerLevelsDescriptor s) {
                        effectiveLevel = s.getEffectiveLevel();
                    }
                    return LoggerDto.builder()
                            .name(e.getKey())
                            .configuredLevel(e.getValue().getConfiguredLevel() != null
                                    ? e.getValue().getConfiguredLevel() : "null")
                            .effectiveLevel(effectiveLevel != null
                                    ? effectiveLevel : "null")
                            .build();
                })
                .toList();
    }

    /**
     * 修改 Logger 级别
     */
    public void setLoggerLevel(String name, String level) {
        LogLevel logLevel = LogLevel.valueOf(level.toUpperCase());
        loggersEndpoint.configureLogLevel(name, logLevel);
    }
}
