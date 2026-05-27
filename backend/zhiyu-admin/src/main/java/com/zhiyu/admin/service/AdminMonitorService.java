/*
 * Copyright (c) 2026 ZhiYu Team. All Rights Reserved.
 *
 * 本软件为 ZhiYu 后端管理系统的一部分。
 * 未经授权，不得传播、修改或用于商业用途。
 */

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 描述: 系统监控与服务治理核心业务类。
 *       提供应用健康检查状态汇总、JVM 与系统硬件监控指标、日志等级动态配置、
 *       Alertmanager 警报列表查询以及 Kubernetes 集群 Pod 状态查询等监控治理能力。
 *
 * @author ZhiYu Team
 * @version 1.0.0
 */
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

    /**
     * 健康检查中需要忽略的子组件键值集合，避免无关的健康检查指标干扰核心视图
     */
    private static final Set<String> SKIP_HEALTH_KEYS =
            Set.of("ping", "livenessState", "readinessState",
                   "discoveryComposite", "refresh", "configServer");

    private static final String COMPONENT_APP = "app";
    private static final String COMPONENT_DB = "db";
    private static final String DB_HEALTH_QUERY = "SELECT 1";
    private static final String NULL_DISPLAY = "null";

    // Kubernetes API 相关配置常量
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

    // AlertManager API 数据解析字段常量
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

    /**
     * 获取当前系统各个组件（包含 Application、Database 以及 Actuator 子端点）的健康状态汇总。
     *
     * @return 各组件健康状态传输对象 (HealthDto) 列表
     */
    public List<HealthDto> getHealth() {
        List<HealthDto> list = new ArrayList<>();
        HealthComponent health = healthEndpoint.health();
        Status appStatus = health.getStatus();

        // 默认先添加应用基础健康状态
        list.add(HealthDto.builder()
                .component(COMPONENT_APP)
                .status(appStatus.getCode())
                .instanceCount(1)
                .build());

        boolean hasDb = false;
        // 如果是组合型健康检查指标，解析其子组件指标
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

        // 若组件中未涵盖数据库健康检查，则主动使用 JDBC 探活并补充健康状态
        if (!hasDb) {
            try {
                jdbcTemplate.queryForObject(DB_HEALTH_QUERY, Long.class);
                list.add(HealthDto.builder()
                        .component(COMPONENT_DB)
                        .status(Status.UP.getCode())
                        .instanceCount(1)
                        .responseTimeMs(0L)
                        .build());
            } catch (RuntimeException e) {
                log.warn("JDBC 数据库健康检查探活执行失败: {}", e.getMessage());
                list.add(HealthDto.builder()
                        .component(COMPONENT_DB)
                        .status(Status.DOWN.getCode())
                        .instanceCount(0)
                        .build());
            }
        }

        return list;
    }

    /**
     * 获取 JVM 内存以及操作系统 CPU 等监控性能快照。
     *
     * @return 性能指标数据传输对象 (MetricsDto)
     */
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

    /**
     * 从警报管理器 (Alertmanager) 查询警报信息列表，并支持过滤选项。
     *
     * @param status 过滤的目标警报状态，如 FIRING、RESOLVED
     * @param severity 过滤的目标警报级别，如 P0、P1、P2
     * @param startTime 警报起始查询时间
     * @param endTime 警报截止查询时间
     * @return 警报数据传输对象 (AlertDto) 列表
     */
    @SuppressWarnings("unchecked")
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
            log.warn("无法从 Alertmanager 获取警报信息: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取当前系统定义的所有 Logger 及其配置和生效的日志等级。
     *
     * @return 日志记录器传输对象 (LoggerDto) 列表
     */
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

    /**
     * 动态设置指定日志记录器 (Logger) 的日志打印等级。
     *
     * @param name 日志记录器全限定名称
     * @param level 目标日志级别（如 DEBUG, INFO, WARN, ERROR）
     */
    public void setLoggerLevel(final String name, final String level) {
        LogLevel logLevel = LogLevel.valueOf(level.toUpperCase(Locale.ROOT));
        loggersEndpoint.configureLogLevel(name, logLevel);
    }

    /**
     * 解析并抽取组件的健康状态数据结构。
     *
     * @param list 健康结果装载列表
     * @param key 组件标识键值
     * @param component 原始健康检查组件实例
     */
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
     * 查询 Kubernetes 集群中 zhiyu 命名空间下相关 Pod 的当前运行状态列表，包括重启计数和最后一次重启的时间戳。
     * 若 Kubernetes API 不可达（如本地开发环境），则自动忽略并降级返回空列表。
     *
     * @return 正在运行的 Pod 信息列表
     */
    public List<PodInfo> getPods() {
        try {
            String token = readFile(SA_TOKEN_PATH);
            if (token == null) {
                log.debug("未发现 K8s 服务账户令牌 —— 降级返回空 Pod 列表");
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
        } catch (java.io.IOException | java.security.GeneralSecurityException e) {
            log.warn("从 Kubernetes API 查询 Pod 状态失败（网络或安全证书配置异常）: {}", e.getMessage());
            return List.of();
        } catch (InterruptedException e) {
            log.warn("查询 Kubernetes Pod 状态的线程调用被中断", e);
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /**
     * 基于 Kubernetes CA 根证书动态构建支持 TLS 安全连接的 HTTP 客户端。
     * 若未找到 CA 文件，则退化返回不包含特殊 TLS 配置的默认 HTTP 客户端。
     *
     * @return 构建的 HttpClient 实例
     * @throws java.security.GeneralSecurityException 证书工厂解析、密钥库初始及 SSL 上下文加载等安全配置异常
     * @throws java.io.IOException 读取证书文件时发生的 IO 错误
     */
    private java.net.http.HttpClient buildK8sHttpClient()
            throws java.security.GeneralSecurityException, java.io.IOException {
        Path caPath = Path.of(SA_CA_PATH);
        if (!Files.exists(caPath)) {
            return java.net.http.HttpClient.newHttpClient();
        }
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        java.security.cert.Certificate caCert;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Files.readAllBytes(caPath))) {
            caCert = cf.generateCertificate(bis);
        }
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

    /**
     * 读取指定路径的文本文件内容并裁剪空格。
     * 采用 Java NIO 框架以避免引入不合规的 File 绝对路径构建问题。
     *
     * @param path 文件绝对路径
     * @return 文件的全部文本内容，若读取失败（例如文件不存在）则返回 null
     */
    private String readFile(final String path) {
        try {
            return Files.readString(Path.of(path)).trim();
        } catch (java.io.IOException e) {
            log.debug("读取监控所需外部配置文件 [{}] 失败: {}", path, e.getMessage());
            return null;
        }
    }
}
