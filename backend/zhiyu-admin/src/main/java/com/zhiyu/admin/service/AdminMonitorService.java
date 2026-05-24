package com.zhiyu.admin.service;

import com.zhiyu.admin.dto.AlertDto;
import com.zhiyu.admin.dto.HealthDto;
import com.zhiyu.admin.dto.LoggerDto;
import com.zhiyu.admin.dto.MetricsDto;
import com.zhiyu.admin.dto.PodStatusDto;
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
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
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

    @Value("${k8s.namespace:zhiyu-dev}")
    private String k8sNamespace;

    private RestTemplate k8sRestTemplate;

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

    /**
     * 从 K8s API 获取当前命名空间的 Pod 状态列表
     */
    @SuppressWarnings("unchecked")
    public List<PodStatusDto> getPods() {
        try {
            RestTemplate rt = getK8sRestTemplate();
            String url = "https://kubernetes.default.svc/api/v1/namespaces/"
                    + k8sNamespace + "/pods";
            ResponseEntity<Map> resp = rt.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(k8sHeaders()), Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) return List.of();
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) body.get("items");
            if (items == null) return List.of();
            return items.stream().map(this::mapPod).toList();
        } catch (Exception e) {
            log.warn("Failed to query K8s pods: {}", e.getMessage());
            return List.of();
        }
    }

    private PodStatusDto mapPod(Map<String, Object> pod) {
        Map<String, Object> meta = (Map<String, Object>) pod.get("metadata");
        Map<String, Object> spec = (Map<String, Object>) pod.get("spec");
        Map<String, Object> status = (Map<String, Object>) pod.get("status");

        String name = String.valueOf(meta.getOrDefault("name", ""));
        String namespace = String.valueOf(meta.getOrDefault("namespace", ""));
        String startTime = String.valueOf(meta.getOrDefault("creationTimestamp", ""));
        String node = String.valueOf(spec.getOrDefault("nodeName", ""));

        List<Map<String, Object>> containers =
                (List<Map<String, Object>>) status.getOrDefault("containerStatuses", List.of());
        int totalContainers = containers.size();
        long readyContainers = containers.stream()
                .filter(c -> Boolean.TRUE.equals(c.get("ready"))).count();
        String ready = readyContainers + "/" + totalContainers;
        int restarts = containers.stream()
                .mapToInt(c -> ((Number) c.getOrDefault("restartCount", 0)).intValue())
                .sum();
        String phase = String.valueOf(status.getOrDefault("phase", "Unknown"));

        String lastRestartTime = containers.stream()
                .map(c -> {
                    Map<String, Object> state = (Map<String, Object>) c.getOrDefault("lastState", Map.of());
                    if (state.isEmpty()) return "";
                    Map<String, Object> terminated = (Map<String, Object>) state.get("terminated");
                    if (terminated != null) return String.valueOf(terminated.getOrDefault("finishedAt", ""));
                    return "";
                })
                .filter(s -> !s.isEmpty())
                .max(String::compareTo)
                .orElse("-");

        return PodStatusDto.builder()
                .name(name)
                .namespace(namespace)
                .ready(ready)
                .status(phase)
                .restarts(restarts)
                .startTime(startTime)
                .lastRestartTime(lastRestartTime)
                .node(node)
                .build();
    }

    private HttpHeaders k8sHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            String token = Files.readString(
                    Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/token"));
            headers.setBearerAuth(token.trim());
        } catch (Exception e) {
            log.debug("Service account token not available: {}", e.getMessage());
        }
        return headers;
    }

    private RestTemplate getK8sRestTemplate() {
        if (k8sRestTemplate != null) return k8sRestTemplate;
        try {
            String caPath = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            try (InputStream is = new FileInputStream(caPath)) {
                java.security.cert.CertificateFactory cf =
                        java.security.cert.CertificateFactory.getInstance("X.509");
                int i = 0;
                for (java.security.cert.Certificate cert :
                        cf.generateCertificates(is)) {
                    keyStore.setCertificateEntry("k8s-ca-" + i++, cert);
                }
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, tmf.getTrustManagers(), null);
            HttpClient httpClient = HttpClient.newBuilder()
                    .sslContext(ssl)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            JdkClientHttpRequestFactory factory =
                    new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(Duration.ofSeconds(10));
            k8sRestTemplate = new RestTemplate(factory);
        } catch (Exception e) {
            log.debug("K8s CA not available, using default SSL: {}", e.getMessage());
            k8sRestTemplate = new RestTemplate();
        }
        return k8sRestTemplate;
    }
}
