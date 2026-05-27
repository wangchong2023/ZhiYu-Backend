package com.zhiyu.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.admin.dto.AlertDto;
import com.zhiyu.admin.dto.HealthDto;
import com.zhiyu.admin.dto.LoggerDto;
import com.zhiyu.admin.dto.MetricsDto;
import com.zhiyu.admin.dto.PodInfo;
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

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Comparator;
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

    // K8s API constants
    private static final String K8S_API_HOST = "https://kubernetes.default.svc";
    private static final String K8S_PODS_PATH = "/api/v1/namespaces/%s/pods";
    private static final String K8S_LABEL_SELECTOR = "app%20in%20(zhiyu-admin,ufp-gateway,ufp-auth)";
    private static final String SA_TOKEN_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token";
    private static final String SA_CA_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";
    private static final String DEFAULT_NAMESPACE = "zhiyu";
    private static final String METADATA = "metadata";
    private static final String STATUS = "status";
    private static final String PHASE = "phase";
    private static final String NAME = "name";
    private static final String NAMESPACE = "namespace";
    private static final String CREATION_TIMESTAMP = "creationTimestamp";
    private static final String START_TIME = "startTime";
    private static final String CONTAINER_STATUSES = "containerStatuses";
    private static final String RESTART_COUNT = "restartCount";
    private static final String LAST_STATE = "lastState";
    private static final String TERMINATED = "terminated";
    private static final String FINISHED_AT = "finishedAt";
    private static final String ITEMS = "items";

    @Value("${POD_NAMESPACE:" + DEFAULT_NAMESPACE + "}")
    private String podNamespace;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // AlertManager API 字段名
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

    /**
     * 描述: 查询 K8s 集群中 zhiyu 相关 Pod 的状态列表，包含启动时间和重启信息。
     *       若 K8s API 不可达（如本地开发环境）则返回空列表。
     * @return Pod 状态列表
     */
    public List<PodInfo> getPods() {
        try {
            String token = readFile(SA_TOKEN_PATH);
            if (token == null) {
                log.debug("K8s service account token not found — returning empty pod list");
                return List.of();
            }
            String url = K8S_API_HOST + String.format(K8S_PODS_PATH, podNamespace)
                    + "?labelSelector=" + K8S_LABEL_SELECTOR;
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> resp = buildK8sHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.body() == null) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(resp.body());
            List<PodInfo> pods = new ArrayList<>();
            for (JsonNode item : root.path(ITEMS)) {
                JsonNode meta = item.path(METADATA);
                JsonNode statusNode = item.path(STATUS);
                String lastRestart = null;
                int maxRestarts = 0;
                JsonNode containers = statusNode.path(CONTAINER_STATUSES);
                for (JsonNode cs : containers) {
                    int rc = cs.path(RESTART_COUNT).asInt();
                    if (rc > maxRestarts) {
                        maxRestarts = rc;
                    }
                    JsonNode terminatedNode =
                            cs.path(LAST_STATE).path(TERMINATED);
                    if (!terminatedNode.isMissingNode()) {
                        String finished = terminatedNode.path(FINISHED_AT).asText();
                        if (lastRestart == null || finished.compareTo(lastRestart) > 0) {
                            lastRestart = finished;
                        }
                    }
                }
                pods.add(PodInfo.builder()
                        .name(meta.path(NAME).asText())
                        .namespace(meta.path(NAMESPACE).asText())
                        .status(statusNode.path(PHASE).asText())
                        .startTime(statusNode.path(START_TIME).asText())
                        .restartCount(maxRestarts)
                        .lastRestartTime(lastRestart)
                        .build());
            }
            pods.sort(Comparator.comparing(PodInfo::getName));
            return pods;
        } catch (Exception e) {
            log.warn("Failed to query K8s pod list: {}", e.getMessage());
            return List.of();
        }
    }

    private java.net.http.HttpClient buildK8sHttpClient() throws Exception {
        File caFile = new File(SA_CA_PATH);
        if (!caFile.exists()) {
            return java.net.http.HttpClient.newHttpClient();
        }
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        java.security.cert.Certificate caCert =
                cf.generateCertificate(new ByteArrayInputStream(Files.readAllBytes(caFile.toPath())));
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null);
        ks.setCertificateEntry("k8s-ca", caCert);
        TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, tmf.getTrustManagers(), null);
        return java.net.http.HttpClient.newBuilder()
                .sslContext(ssl)
                .build();
    }

    private String readFile(final String path) {
        try {
            return Files.readString(new File(path).toPath()).trim();
        } catch (Exception e) {
            return null;
        }
    }
}
