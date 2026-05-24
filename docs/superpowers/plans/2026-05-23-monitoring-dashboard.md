# 运行监控与仪表盘 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 扩展仪表盘页面并新建运行监控 5 个子路由页面，后端聚合 Actuator/Prometheus/AlertManager API 供前端只读展示。

**Architecture:** 后端聚合代理模式 — 前端仅调用 `/api/v1/admin/monitor/*` 和 `/api/v1/admin/stats/*`，后端代理 Prometheus HTTP API 和 AlertManager API 查询。Dashboard 扩展现有 StatCard + TrendChart，Monitor 子页面通过 Ant Design Tabs/Table + ECharts 展示。

**Tech Stack:** React 18 + TypeScript + Ant Design 5 + ECharts 5 + React Router 6, Java 21 + Spring Boot 3.3.x + MyBatis-Plus

---

## 文件结构

```
backend/zhiyu-admin/src/main/java/com/zhiyu/admin/
├── controller/
│   ├── AdminMonitorController.java   # 新建
│   └── AdminStatsController.java     # 修改
├── service/
│   ├── AdminMonitorService.java      # 新建
│   └── AdminStatsService.java        # 修改
└── dto/
    ├── StatsOverviewResponse.java    # 修改
    ├── HealthDto.java                # 新建
    ├── MetricsDto.java               # 新建
    ├── AlertDto.java                 # 新建
    └── LoggerDto.java                # 新建

backend/zhiyu-server/src/main/resources/db/migration/
└── V1.9.0__add_monitor_tables.sql   # 新建

frontend/src/
├── api/
│   ├── types.ts                      # 修改
│   ├── monitorApi.ts                 # 新建
│   └── statsApi.ts                   # 修改
├── pages/
│   ├── dashboard/
│   │   └── DashboardPage.tsx         # 修改
│   └── monitor/
│       ├── MonitorOverviewPage.tsx   # 新建
│       ├── MonitorMetricsPage.tsx    # 新建
│       ├── MonitorLogsPage.tsx       # 新建
│       ├── MonitorAlertsPage.tsx     # 新建
│       └── LogLevelSettingsPage.tsx  # 新建
├── layouts/
│   └── AdminLayout.tsx               # 修改
├── App.tsx                           # 修改
└── test/pages/
    ├── DashboardPage.test.tsx        # 修改
    ├── MonitorOverviewPage.test.tsx  # 新建
    ├── MonitorMetricsPage.test.tsx   # 新建
    ├── MonitorLogsPage.test.tsx      # 新建
    ├── MonitorAlertsPage.test.tsx    # 新建
    └── LogLevelSettingsPage.test.tsx # 新建
```

---

### Task 1: 数据库迁移 — log_level_history 表

**Files:**
- Create: `backend/zhiyu-server/src/main/resources/db/migration/V1.9.0__add_monitor_tables.sql`

- [ ] **Step 1: 创建 migration SQL 文件**

```sql
CREATE TABLE log_level_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    logger_name     VARCHAR(255)    NOT NULL,
    old_level       VARCHAR(10)     NOT NULL,
    new_level       VARCHAR(10)     NOT NULL,
    changed_by      VARCHAR(64)     NOT NULL,
    expire_at       DATETIME(3)     DEFAULT NULL COMMENT '自动回滚时间',
    rolled_back_at  DATETIME(3)     DEFAULT NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_logger_name (logger_name),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: 验证 migration 语法正确**

```bash
# 启动 MySQL 容器验证（如已运行则跳过）:
# docker run -d --name mysql-test -e MYSQL_ROOT_PASSWORD=test -e MYSQL_DATABASE=zhiyu -p 3306:3306 mysql:8.0
# 或用现有 MySQL 连接执行 dry-run:
cat backend/zhiyu-server/src/main/resources/db/migration/V1.9.0__add_monitor_tables.sql | head -20
```

- [ ] **Step 3: Commit**

```bash
git add backend/zhiyu-server/src/main/resources/db/migration/V1.9.0__add_monitor_tables.sql
git commit -m "feat: add log_level_history table for monitor log level adjustment audit"
```

---

### Task 2: 后端 DTOs — 创建 Monitor 相关 DTO 并扩展 StatsOverviewResponse

**Files:**
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/dto/HealthDto.java`
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/dto/MetricsDto.java`
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/dto/AlertDto.java`
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/dto/LoggerDto.java`
- Modify: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/dto/StatsOverviewResponse.java`

- [ ] **Step 1: 创建 HealthDto.java**

```java
package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "组件健康状态")
public class HealthDto {
    @Schema(description = "组件名称", example = "MySQL") private String component;
    @Schema(description = "状态: UP / DOWN / DEGRADED") private String status;
    @Schema(description = "实例数") private int instanceCount;
    @Schema(description = "响应时间 ms (数据库)") private Long responseTimeMs;
    @Schema(description = "详情") private String detail;
}
```

- [ ] **Step 2: 创建 MetricsDto.java**

```java
package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "监控指标数据")
public class MetricsDto {
    @Schema(description = "QPS 时序数据") private List<MetricPoint> qps;
    @Schema(description = "P50 延迟时序") private List<MetricPoint> latencyP50;
    @Schema(description = "P95 延迟时序") private List<MetricPoint> latencyP95;
    @Schema(description = "P99 延迟时序") private List<MetricPoint> latencyP99;
    @Schema(description = "错误率时序") private List<MetricPoint> errorRate;

    @Data
    @Builder
    @Schema(description = "指标时序点")
    public static class MetricPoint {
        @Schema(description = "时间戳 (epoch seconds)") private long timestamp;
        @Schema(description = "值") private double value;
    }
}
```

- [ ] **Step 3: 创建 AlertDto.java**

```java
package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "告警信息")
public class AlertDto {
    @Schema(description = "告警名称") private String alertName;
    @Schema(description = "严重级别: P0/P1/P2") private String severity;
    @Schema(description = "告警条件描述") private String condition;
    @Schema(description = "当前值") private String currentValue;
    @Schema(description = "状态: FIRING / RESOLVED") private String status;
    @Schema(description = "触发时间") private String firedAt;
}
```

- [ ] **Step 4: 创建 LoggerDto.java**

```java
package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "Logger 信息")
public class LoggerDto {
    @Schema(description = "Logger 名称") private String name;
    @Schema(description = "当前日志级别") private String configuredLevel;
    @Schema(description = "生效级别") private String effectiveLevel;

    @Data
    @Builder
    @Schema(description = "日志级别调整历史")
    public static class LogLevelHistoryDto {
        private Long id;
        private String loggerName;
        private String oldLevel;
        private String newLevel;
        private String changedBy;
        private LocalDateTime expireAt;
        private LocalDateTime rolledBackAt;
        private LocalDateTime createdAt;
    }
}
```

- [ ] **Step 5: 扩展 StatsOverviewResponse.java**

```java
package com.zhiyu.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "仪表盘概览数据")
public class StatsOverviewResponse {
    @Schema(description = "今日新增用户数") private long newUsers;
    @Schema(description = "活跃订阅数") private long activeSubs;
    @Schema(description = "今日营收 (元)") private long revenue;
    @Schema(description = "在线用户数") private long onlineUsers;
    @Schema(description = "今日注册数") private long todayRegistrations;
    @Schema(description = "今日登录数") private long todayLogins;
    @Schema(description = "今日活跃用户数 (DAU)") private long dau;
    @Schema(description = "近30天登录成功率 (百分比)") private double loginSuccessRate;
    @Schema(description = "注册较昨日变化百分比") private double registrationChange;
    @Schema(description = "登录较昨日变化百分比") private double loginChange;
}
```

- [ ] **Step 6: 编译验证，然后 Commit**

```bash
./mvnw -f backend/pom.xml compile -pl zhiyu-admin -am -q
git add backend/zhiyu-admin/src/main/java/com/zhiyu/admin/dto/
git commit -m "feat: add monitor DTOs and extend StatsOverviewResponse"
```

---

### Task 3: 后端 AdminMonitorService — 健康聚合 + Prometheus/AlertManager 代理

**Files:**
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/service/AdminMonitorService.java`

- [ ] **Step 1: 创建 AdminMonitorService.java**

```java
package com.zhiyu.admin.service;

import com.zhiyu.admin.dto.AlertDto;
import com.zhiyu.admin.dto.HealthDto;
import com.zhiyu.admin.dto.LoggerDto;
import com.zhiyu.admin.dto.MetricsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
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
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${prometheus.url:http://localhost:9090}")
    private String prometheusUrl;

    @Value("${alertmanager.url:http://localhost:9093}")
    private String alertmanagerUrl;

    public List<HealthDto> getHealth() {
        List<HealthDto> list = new ArrayList<>();
        HealthComponent health = healthEndpoint.health();

        // App instance
        Status appStatus = health.getStatus();
        list.add(HealthDto.builder()
                .component("应用实例")
                .status(appStatus.getCode())
                .instanceCount(1)
                .build());

        // DB
        try {
            Long dbMs = jdbcTemplate.queryForObject("SELECT 1", Long.class);
            list.add(HealthDto.builder()
                    .component("数据库")
                    .status("UP")
                    .instanceCount(1)
                    .responseTimeMs(dbMs != null ? 0L : 0L)
                    .build());
        } catch (Exception e) {
            list.add(HealthDto.builder()
                    .component("数据库").status("DOWN").instanceCount(0).build());
        }

        // Redis - check via Actuator health details
        Map<String, Object> details = healthEndpoint.health().getDetails();
        if (details.containsKey("redis")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> redis = (Map<String, Object>) details.get("redis");
            list.add(HealthDto.builder()
                    .component("Redis")
                    .status(String.valueOf(redis.getOrDefault("status", "UNKNOWN")))
                    .instanceCount(1)
                    .build());
        }

        // Nacos - check via Actuator health details
        if (details.containsKey("nacos")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nacos = (Map<String, Object>) details.get("nacos");
            list.add(HealthDto.builder()
                    .component("Nacos")
                    .status(String.valueOf(nacos.getOrDefault("status", "UNKNOWN")))
                    .instanceCount(1)
                    .build());
        }

        return list;
    }

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

        String qpsQuery = "rate(http_server_requests_seconds_count[5m])";
        String latencyP50Query = "histogram_quantile(0.5, rate(http_server_requests_seconds_bucket[5m]))";
        String latencyP95Query = "histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))";
        String latencyP99Query = "histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))";
        String errorRateQuery = "rate(http_server_requests_seconds_count{status=~\"5..\"}[5m]) / rate(http_server_requests_seconds_count[5m])";

        return MetricsDto.builder()
                .qps(queryPrometheus(qpsQuery, start, end, step))
                .latencyP50(queryPrometheus(latencyP50Query, start, end, step))
                .latencyP95(queryPrometheus(latencyP95Query, start, end, step))
                .latencyP99(queryPrometheus(latencyP99Query, start, end, step))
                .errorRate(queryPrometheus(errorRateQuery, start, end, step))
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<MetricsDto.MetricPoint> queryPrometheus(
            String query, long start, long end, String step) {
        try {
            String url = String.format("%s/api/v1/query_range?query=%s&start=%d&end=%d&step=%s",
                    prometheusUrl, query, start, end, step);
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null || !"success".equals(body.get("status"))) {
                return List.of();
            }
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            List<Map<String, Object>> results = (List<Map<String, Object>>) data.get("result");
            if (results.isEmpty()) return List.of();
            List<List<Object>> values =
                    (List<List<Object>>) results.get(0).get("values");
            return values.stream().map(v -> MetricsDto.MetricPoint.builder()
                    .timestamp(((Number) v.get(0)).longValue())
                    .value(Double.parseDouble(String.valueOf(v.get(1))))
                    .build()).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public List<AlertDto> getAlerts(String status, String severity, String startTime, String endTime) {
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
                        return AlertDto.builder()
                                .alertName(String.valueOf(labels.getOrDefault("alertname", "unknown")))
                                .severity(String.valueOf(labels.getOrDefault("severity", "P2")))
                                .condition(String.valueOf(annotations.getOrDefault("summary", "")))
                                .currentValue(String.valueOf(annotations.getOrDefault("description", "")))
                                .status(String.valueOf(a.getOrDefault("status", "firing")).toUpperCase())
                                .firedAt(String.valueOf(a.getOrDefault("startsAt", "")))
                                .build();
                    }).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public List<LoggerDto> getLoggers() {
        try {
            String url = "http://localhost:8080/actuator/loggers";
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) return List.of();
            Map<String, Object> loggers = (Map<String, Object>) body.get("loggers");
            return loggers.entrySet().stream()
                    .map(e -> {
                        Map<String, Object> v = (Map<String, Object>) e.getValue();
                        return LoggerDto.builder()
                                .name(e.getKey())
                                .configuredLevel(String.valueOf(v.getOrDefault("configuredLevel", "null")))
                                .effectiveLevel(String.valueOf(v.getOrDefault("effectiveLevel", "null")))
                                .build();
                    }).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public void setLoggerLevel(String name, String level) {
        String url = "http://localhost:8080/actuator/loggers/" + name;
        Map<String, String> body = Map.of("configuredLevel", level);
        restTemplate.postForEntity(url, body, Map.class);
    }
}
```

- [ ] **Step 2: 编译验证，然后 Commit**

```bash
./mvnw -f backend/pom.xml compile -pl zhiyu-admin -am -q
git add backend/zhiyu-admin/src/main/java/com/zhiyu/admin/service/AdminMonitorService.java
git commit -m "feat: add AdminMonitorService for health/metrics/alerts/loggers aggregation"
```

---

### Task 4: 后端 AdminMonitorController — Monitor API 端点

**Files:**
- Create: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/controller/AdminMonitorController.java`

- [ ] **Step 1: 创建 AdminMonitorController.java**

```java
package com.zhiyu.admin.controller;

import com.zhiyu.admin.dto.AlertDto;
import com.zhiyu.admin.dto.HealthDto;
import com.zhiyu.admin.dto.LoggerDto;
import com.zhiyu.admin.dto.MetricsDto;
import com.zhiyu.admin.service.AdminMonitorService;
import com.zhiyu.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "管理后台-运行监控", description = "服务健康/指标/告警/日志级别")
@RestController
@RequestMapping("/api/v1/admin/monitor")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminMonitorController {

    private final AdminMonitorService adminMonitorService;

    @Operation(summary = "服务健康概览")
    @GetMapping("/health")
    public ApiResponse<List<HealthDto>> health() {
        return ApiResponse.success(adminMonitorService.getHealth());
    }

    @Operation(summary = "API 指标")
    @GetMapping("/metrics")
    public ApiResponse<MetricsDto> metrics(
            @RequestParam(defaultValue = "24h") String range) {
        return ApiResponse.success(adminMonitorService.getMetrics(range));
    }

    @Operation(summary = "告警列表")
    @GetMapping("/alerts")
    public ApiResponse<List<AlertDto>> alerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return ApiResponse.success(adminMonitorService.getAlerts(
                status, severity, startTime, endTime));
    }

    @Operation(summary = "最近告警 (仪表盘用)")
    @GetMapping("/alerts/recent")
    public ApiResponse<List<AlertDto>> recentAlerts() {
        return ApiResponse.success(adminMonitorService.getAlerts(
                "firing", null, null, null));
    }

    @Operation(summary = "Logger 列表")
    @GetMapping("/loggers")
    public ApiResponse<List<LoggerDto>> loggers() {
        return ApiResponse.success(adminMonitorService.getLoggers());
    }

    @Operation(summary = "修改 Logger 级别")
    @PostMapping("/loggers/{name}")
    public ApiResponse<Void> setLoggerLevel(
            @PathVariable String name,
            @RequestBody Map<String, String> body) {
        adminMonitorService.setLoggerLevel(name, body.get("configuredLevel"));
        return ApiResponse.success(null);
    }

    @Operation(summary = "Logger 调整历史")
    @GetMapping("/loggers/history")
    public ApiResponse<List<LoggerDto.LogLevelHistoryDto>> loggerHistory() {
        // Placeholder: 后续 Task 实现历史查询，先返回空列表确保编译通过
        return ApiResponse.success(List.of());
    }
}
```

- [ ] **Step 2: 编译验证，然后 Commit**

```bash
./mvnw -f backend/pom.xml compile -pl zhiyu-admin -am -q
git add backend/zhiyu-admin/src/main/java/com/zhiyu/admin/controller/AdminMonitorController.java
git commit -m "feat: add AdminMonitorController with health/metrics/alerts/loggers endpoints"
```

---

### Task 5: 后端 AdminStatsService 扩展 — 概览 + 统一趋势 + 在线用户

**Files:**
- Modify: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/service/AdminStatsService.java`
- Modify: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/controller/AdminStatsController.java`

- [ ] **Step 1: 扩展 AdminStatsService.getOverview()**

修改 `getOverview()` 方法，增加 `newUsers`、`activeSubs`、`revenue`、`onlineUsers` 字段：

```java
public StatsOverviewResponse getOverview() {
    long todayRegs = countToday("auth_user", "created_time");
    long yesterdayRegs = countYesterday("auth_user", "created_time");
    long todayLogins = countToday("auth_user_log", "created_time");
    long yesterdayLogins = countYesterday("auth_user_log", "created_time");

    String dauSql = "SELECT COUNT(DISTINCT auth_user_log_user_id)"
            + " FROM auth_user_log WHERE DATE(created_time) = CURDATE()";
    Long dau = jdbcTemplate.queryForObject(dauSql, Long.class);

    String rateSql = """
        SELECT
            ROUND(SUM(CASE WHEN auth_user_log_result='SUCCESS' THEN 1 ELSE 0 END) \
        * 100.0 / COUNT(*), 1)
        FROM auth_user_log
        WHERE created_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) \
        AND auth_user_log_action='LOGIN'
        """;
    Double rate = jdbcTemplate.queryForObject(rateSql, Double.class);

    // New fields
    long newUsers = todayRegs;
    long activeSubs = countActiveSubscriptions();
    long revenue = countTodayRevenue();
    long onlineUsers = countOnlineUsers();

    double regChange = yesterdayRegs > 0
            ? ((double) (todayRegs - yesterdayRegs) / yesterdayRegs) * HUNDRED : HUNDRED;
    double loginChange = yesterdayLogins > 0
            ? ((double) (todayLogins - yesterdayLogins) / yesterdayLogins) * HUNDRED : HUNDRED;

    return StatsOverviewResponse.builder()
            .newUsers(newUsers)
            .activeSubs(activeSubs)
            .revenue(revenue)
            .onlineUsers(onlineUsers)
            .todayRegistrations(todayRegs)
            .todayLogins(todayLogins)
            .dau(dau != null ? dau : 0)
            .loginSuccessRate(rate != null ? rate : 0)
            .registrationChange(Math.round(regChange * TEN) / TEN)
            .loginChange(Math.round(loginChange * TEN) / TEN)
            .build();
}

private long countActiveSubscriptions() {
    try {
        String sql = "SELECT COUNT(*) FROM zhiyu_subscription WHERE status = 'ACTIVE'";
        Long val = jdbcTemplate.queryForObject(sql, Long.class);
        return val != null ? val : 0;
    } catch (Exception e) {
        return 0;
    }
}

private long countTodayRevenue() {
    try {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM zhiyu_payment"
                + " WHERE DATE(created_time) = CURDATE() AND status = 'SUCCESS'";
        Long val = jdbcTemplate.queryForObject(sql, Long.class);
        return val != null ? val : 0;
    } catch (Exception e) {
        return 0;
    }
}

private long countOnlineUsers() {
    // Online users = users with token activity in last 10 minutes
    try {
        String sql = "SELECT COUNT(DISTINCT auth_user_log_user_id)"
                + " FROM auth_user_log WHERE created_time >= DATE_SUB(NOW(), INTERVAL 10 MINUTE)";
        Long val = jdbcTemplate.queryForObject(sql, Long.class);
        return val != null ? val : 0;
    } catch (Exception e) {
        return 0;
    }
}
```

- [ ] **Step 2: 新增统一趋势方法 getTrend()**

```java
public List<Map<String, Object>> getTrend(int days) {
    String sql = """
        SELECT
            DATE(a.created_time) as date,
            COUNT(DISTINCT a.auth_user_id) as newUsers,
            COUNT(DISTINCT l.auth_user_log_user_id) as activeUsers
        FROM auth_user a
        LEFT JOIN auth_user_log l ON DATE(l.created_time) = DATE(a.created_time)
            AND l.auth_user_log_action = 'LOGIN'
            AND l.auth_user_log_result = 'SUCCESS'
        WHERE a.created_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
        GROUP BY DATE(a.created_time) ORDER BY date
        """;
    return jdbcTemplate.queryForList(sql, days);
}
```

- [ ] **Step 3: 扩展 AdminStatsController**

在 `AdminStatsController.java` 中新增端点：

```java
@Operation(summary = "趋势数据", description = "近N天每日新增用户 + 活跃用户")
@GetMapping("/trend")
public ApiResponse<List<Map<String, Object>>> trend(
        @RequestParam(defaultValue = "7") final int days) {
    return ApiResponse.success(adminStatsService.getTrend(days));
}
```

- [ ] **Step 4: 编译验证，然后 Commit**

```bash
./mvnw -f backend/pom.xml compile -pl zhiyu-admin -am -q
git add backend/zhiyu-admin/src/main/java/com/zhiyu/admin/service/AdminStatsService.java
git add backend/zhiyu-admin/src/main/java/com/zhiyu/admin/controller/AdminStatsController.java
git commit -m "feat: extend stats overview with subs/revenue/online, add unified trend endpoint"
```

---

### Task 6: 后端日志检索端点 — 扩展 AdminLogController

**Files:**
- Modify: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/controller/AdminLogController.java`
- Modify: `backend/zhiyu-admin/src/main/java/com/zhiyu/admin/service/AdminLogService.java`

- [ ] **Step 1: 在 AdminLogService 中新增应用日志和安全日志查询**

```java
public Page<LoginLogDto> listSecurityLogs(int page, int size,
        String type, String ip, LocalDateTime startTime, LocalDateTime endTime) {
    var wrapper = new LambdaQueryWrapper<AuthUserLog>();
    wrapper.in(AuthUserLog::getAuthUserLogAction, "LOGIN", "LOGOUT", "CAPTCHA", "RATE_LIMIT");
    if (type != null && !type.isBlank()) {
        wrapper.eq(AuthUserLog::getAuthUserLogAction, type);
    }
    if (ip != null && !ip.isBlank()) {
        wrapper.eq(AuthUserLog::getAuthUserLogSourceIp, ip);
    }
    if (startTime != null) wrapper.ge(AuthUserLog::getCreatedTime, startTime);
    if (endTime != null) wrapper.le(AuthUserLog::getCreatedTime, endTime);
    wrapper.orderByDesc(AuthUserLog::getCreatedTime);

    Page<AuthUserLog> entityPage = authUserLogMapper.selectPage(
            new Page<>(page, size), wrapper);
    Page<LoginLogDto> dtoPage = new Page<>(page, size, entityPage.getTotal());
    dtoPage.setRecords(entityPage.getRecords().stream()
            .map(AdminConverter.INSTANCE::toLogDto)
            .collect(Collectors.toList()));
    return dtoPage;
}
```

- [ ] **Step 2: 在 AdminLogController 中新增端点**

```java
@Operation(summary = "安全日志", description = "登录/注销/验证码/限流事件")
@GetMapping("/security")
public ApiResponse<Page<LoginLogDto>> securityLogs(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String ip,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
    return ApiResponse.success(adminLogService.listSecurityLogs(
            page, size, type, ip, startTime, endTime));
}
```

- [ ] **Step 3: 编译验证，然后 Commit**

```bash
./mvnw -f backend/pom.xml compile -pl zhiyu-admin -am -q
git add backend/zhiyu-admin/src/main/java/com/zhiyu/admin/controller/AdminLogController.java
git add backend/zhiyu-admin/src/main/java/com/zhiyu/admin/service/AdminLogService.java
git commit -m "feat: add security log query endpoint for monitor logs page"
```

---

### Task 7: 前端 — 扩展 types.ts + 创建 monitorApi.ts

**Files:**
- Modify: `frontend/src/api/types.ts`
- Create: `frontend/src/api/monitorApi.ts`
- Modify: `frontend/src/api/statsApi.ts`

- [ ] **Step 1: 扩展 types.ts**

在 `types.ts` 末尾添加新的类型定义：

```typescript
export interface HealthDto {
  component: string;
  status: string;
  instanceCount: number;
  responseTimeMs?: number;
  detail?: string;
}

export interface MetricPoint {
  timestamp: number;
  value: number;
}

export interface MetricsDto {
  qps: MetricPoint[];
  latencyP50: MetricPoint[];
  latencyP95: MetricPoint[];
  latencyP99: MetricPoint[];
  errorRate: MetricPoint[];
}

export interface AlertDto {
  alertName: string;
  severity: string;
  condition: string;
  currentValue: string;
  status: string;
  firedAt: string;
}

export interface LoggerDto {
  name: string;
  configuredLevel: string;
  effectiveLevel: string;
}

export interface LogLevelHistoryDto {
  id: number;
  loggerName: string;
  oldLevel: string;
  newLevel: string;
  changedBy: string;
  expireAt: string | null;
  rolledBackAt: string | null;
  createdAt: string;
}

export interface TrendItem {
  date: string;
  newUsers: number;
  activeUsers: number;
}

// Extend StatsOverview to include new fields
export interface StatsOverview {
  newUsers: number;
  activeSubs: number;
  revenue: number;
  onlineUsers: number;
  todayRegistrations: number;
  todayLogins: number;
  dau: number;
  loginSuccessRate: number;
  registrationChange: number;
  loginChange: number;
}
```

- [ ] **Step 2: 创建 monitorApi.ts**

```typescript
import apiClient from './client';
import type { ApiResponse, HealthDto, MetricsDto, AlertDto, LoggerDto, LogLevelHistoryDto } from './types';

const monitorApi = {
  health: () =>
    apiClient.get<ApiResponse<HealthDto[]>>('/admin/monitor/health'),

  metrics: (range: string) =>
    apiClient.get<ApiResponse<MetricsDto>>('/admin/monitor/metrics', { params: { range } }),

  alerts: (params?: { status?: string; severity?: string; startTime?: string; endTime?: string }) =>
    apiClient.get<ApiResponse<AlertDto[]>>('/admin/monitor/alerts', { params }),

  recentAlerts: () =>
    apiClient.get<ApiResponse<AlertDto[]>>('/admin/monitor/alerts/recent'),

  loggers: () =>
    apiClient.get<ApiResponse<LoggerDto[]>>('/admin/monitor/loggers'),

  setLoggerLevel: (name: string, configuredLevel: string) =>
    apiClient.post(`/admin/monitor/loggers/${name}`, { configuredLevel }),

  loggerHistory: () =>
    apiClient.get<ApiResponse<LogLevelHistoryDto[]>>('/admin/monitor/loggers/history'),
};

export default monitorApi;
```

- [ ] **Step 3: 扩展 statsApi.ts**

```typescript
import apiClient from './client';
import type { ApiResponse, StatsOverview, TrendItem } from './types';

const statsApi = {
  overview: () =>
    apiClient.get<ApiResponse<StatsOverview>>('/admin/stats/overview'),

  trend: (days?: number) =>
    apiClient.get<ApiResponse<TrendItem[]>>('/admin/stats/trend', { params: { days } }),

  registerTrend: (days?: number) =>
    apiClient.get<ApiResponse<TrendItem[]>>('/admin/stats/register-trend', { params: { days } }),

  dauTrend: (days?: number) =>
    apiClient.get<ApiResponse<TrendItem[]>>('/admin/stats/dau-trend', { params: { days } }),

  loginMethodDist: (days?: number) =>
    apiClient.get<ApiResponse<DistributionItem[]>>('/admin/stats/login-method-dist', { params: { days } }),
};

export default statsApi;
```

- [ ] **Step 4: TypeScript 编译验证，然后 Commit**

```bash
npx tsc --noEmit
git add frontend/src/api/types.ts frontend/src/api/monitorApi.ts frontend/src/api/statsApi.ts
git commit -m "feat: add monitor API module and extend types for monitoring dashboard"
```

---

### Task 8: 前端 — 路由 + 侧边栏变更

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/layouts/AdminLayout.tsx`

- [ ] **Step 1: 修改 AdminLayout.tsx 侧边栏菜单**

在 `menuItems` 数组中，'仪表盘' 之后添加：

```typescript
import { MonitorOutlined } from '@ant-design/icons';

const menuItems = [
  { key: '/admin/dashboard', icon: <DashboardOutlined />, label: '仪表盘' },
  {
    key: '/admin/monitor',
    icon: <MonitorOutlined />,
    label: '运行监控',
    children: [
      { key: '/admin/monitor/overview', label: '概览' },
      { key: '/admin/monitor/metrics', label: 'API 指标' },
      { key: '/admin/monitor/logs', label: '日志检索' },
      { key: '/admin/monitor/alerts', label: '告警面板' },
      { key: '/admin/monitor/settings', label: '日志级别' },
    ],
  },
  { key: '/admin/users', icon: <UserOutlined />, label: '用户管理' },
  { key: '/admin/audit', icon: <AuditOutlined />, label: '审计日志' },
  { key: '/admin/account', icon: <SettingOutlined />, label: '我的账户' },
];
```

同时修改 `selectedKeys` 逻辑，支持子菜单高亮：

```typescript
// Replace: selectedKeys={[location.pathname]}
// With:
const selectedKey = location.pathname.startsWith('/admin/monitor')
  ? location.pathname
  : location.pathname;

// In the JSX:
<Menu
  theme="dark"
  mode="inline"
  selectedKeys={[selectedKey]}
  defaultOpenKeys={['/admin/monitor']}
  items={menuItems}
  onClick={({ key }) => navigate(key)}
/>
```

- [ ] **Step 2: 修改 App.tsx 添加路由**

```typescript
import { Routes, Route, Navigate } from 'react-router-dom';
import AdminLayout from './layouts/AdminLayout';
import LoginPage from './pages/login/LoginPage';
import DashboardPage from './pages/dashboard/DashboardPage';
import UserListPage from './pages/users/UserListPage';
import AuditLogPage from './pages/audit/AuditLogPage';
import MyAccountPage from './pages/account/MyAccountPage';
import MonitorOverviewPage from './pages/monitor/MonitorOverviewPage';
import MonitorMetricsPage from './pages/monitor/MonitorMetricsPage';
import MonitorLogsPage from './pages/monitor/MonitorLogsPage';
import MonitorAlertsPage from './pages/monitor/MonitorAlertsPage';
import LogLevelSettingsPage from './pages/monitor/LogLevelSettingsPage';

function App() {
  return (
    <Routes>
      <Route path="/admin/login" element={<LoginPage />} />
      <Route path="/admin" element={<AdminLayout />}>
        <Route index element={<Navigate to="/admin/dashboard" replace />} />
        <Route path="dashboard" element={<DashboardPage />} />
        <Route path="monitor">
          <Route index element={<Navigate to="/admin/monitor/overview" replace />} />
          <Route path="overview" element={<MonitorOverviewPage />} />
          <Route path="metrics" element={<MonitorMetricsPage />} />
          <Route path="logs" element={<MonitorLogsPage />} />
          <Route path="alerts" element={<MonitorAlertsPage />} />
          <Route path="settings" element={<LogLevelSettingsPage />} />
        </Route>
        <Route path="users" element={<UserListPage />} />
        <Route path="audit" element={<AuditLogPage />} />
        <Route path="account" element={<MyAccountPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/admin/dashboard" replace />} />
    </Routes>
  );
}

export default App;
```

- [ ] **Step 3: TypeScript 编译验证，然后 Commit**

```bash
npx tsc --noEmit
git add frontend/src/App.tsx frontend/src/layouts/AdminLayout.tsx
git commit -m "feat: add monitor sub-routes and sidebar menu with MonitorOutlined"
```

---

### Task 9: 前端 — DashboardPage 扩展

**Files:**
- Modify: `frontend/src/pages/dashboard/DashboardPage.tsx`
- Modify: `frontend/src/test/pages/DashboardPage.test.tsx`

- [ ] **Step 1: 编写失败的测试 — 扩展 DashboardPage.test.tsx**

```typescript
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import DashboardPage from '../../pages/dashboard/DashboardPage';

const { mockGet } = vi.hoisted(() => ({
  mockGet: vi.fn((url: string) => {
    if (url === '/admin/stats/overview') {
      return Promise.resolve({ data: { code: 0, data: {
        newUsers: 128, activeSubs: 56, revenue: 2480, onlineUsers: 47,
        todayRegistrations: 5, todayLogins: 10, dau: 20,
        loginSuccessRate: 95.5, registrationChange: 10, loginChange: -5,
      } } });
    }
    if (url === '/admin/stats/trend') {
      return Promise.resolve({ data: { code: 0, data: [
        { date: '2026-05-17', newUsers: 10, activeUsers: 50 },
        { date: '2026-05-18', newUsers: 12, activeUsers: 55 },
      ] } });
    }
    if (url === '/admin/monitor/alerts/recent') {
      return Promise.resolve({ data: { code: 0, data: [
        { alertName: 'CPU过高', severity: 'P0', condition: 'cpu>90%',
          currentValue: '94.3%', status: 'FIRING', firedAt: '2026-05-23T17:42:00' },
      ] } });
    }
    return Promise.resolve({ data: { code: 0, data: [] } });
  }),
}));

vi.mock('../../api/client', () => ({ default: { get: mockGet } }));
vi.mock('echarts-for-react/lib/core', () => ({ default: () => null }));

describe('DashboardPage', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('renders new stat cards', async () => {
    render(<MemoryRouter><DashboardPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('今日新增用户')).toBeInTheDocument();
      expect(screen.getByText('活跃订阅数')).toBeInTheDocument();
      expect(screen.getByText('今日营收')).toBeInTheDocument();
      expect(screen.getByText('在线用户')).toBeInTheDocument();
    });
  });

  it('renders recent alerts section', async () => {
    render(<MemoryRouter><DashboardPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('最近告警')).toBeInTheDocument();
    });
  });

  it('fetches trend data', async () => {
    render(<MemoryRouter><DashboardPage /></MemoryRouter>);
    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/admin/stats/trend', expect.anything());
      expect(mockGet).toHaveBeenCalledWith('/admin/monitor/alerts/recent');
    });
  });
});
```

- [ ] **Step 2: 运行测试验证失败**

```bash
npx vitest run src/test/pages/DashboardPage.test.tsx
```

预期: 测试失败（新的 StatCard 文本和 RecentAlerts 不存在）

- [ ] **Step 3: 重写 DashboardPage.tsx**

```typescript
import { useEffect, useState } from 'react';
import { Row, Col, Card, Statistic, Spin, Alert, Button, List, Badge, Typography } from 'antd';
import {
  UserAddOutlined, DollarOutlined, TeamOutlined, WifiOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import ReactEChartsCore from 'echarts-for-react/lib/core';
import * as echarts from 'echarts/core';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import {
  GridComponent, TooltipComponent, TitleComponent, LegendComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import apiClient from '../../api/client';

echarts.use([BarChart, LineChart, PieChart, GridComponent, TooltipComponent,
  TitleComponent, LegendComponent, CanvasRenderer]);

interface StatsOverview {
  newUsers: number;
  activeSubs: number;
  revenue: number;
  onlineUsers: number;
  todayRegistrations: number;
  todayLogins: number;
  dau: number;
  loginSuccessRate: number;
  registrationChange: number;
  loginChange: number;
}

interface TrendItem {
  date: string;
  newUsers: number;
  activeUsers: number;
}

interface AlertItem {
  alertName: string;
  severity: string;
  condition: string;
  currentValue: string;
  status: string;
  firedAt: string;
}

interface DistributionItem {
  method: string;
  count: number;
  percentage: number;
}

function DashboardPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [overview, setOverview] = useState<StatsOverview | null>(null);
  const [trend, setTrend] = useState<TrendItem[]>([]);
  const [alerts, setAlerts] = useState<AlertItem[]>([]);
  const [dist, setDist] = useState<DistributionItem[]>([]);
  const [onlineUsers, setOnlineUsers] = useState(0);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [ov, td, al, di] = await Promise.all([
        apiClient.get('/admin/stats/overview'),
        apiClient.get('/admin/stats/trend', { params: { days: 7 } }),
        apiClient.get('/admin/monitor/alerts/recent'),
        apiClient.get('/admin/stats/login-method-dist'),
      ]);
      setOverview(ov.data?.data);
      setTrend(td.data?.data || []);
      setAlerts(al.data?.data || []);
      setDist(di.data?.data || []);
      setOnlineUsers(ov.data?.data?.onlineUsers || 0);
    } catch (e) {
      setError('加载仪表盘数据失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  // Poll online users every 10s
  useEffect(() => {
    const timer = setInterval(async () => {
      try {
        const ov = await apiClient.get('/admin/stats/overview');
        setOnlineUsers(ov.data?.data?.onlineUsers || 0);
      } catch { /* ignore poll errors */ }
    }, 10000);
    return () => clearInterval(timer);
  }, []);

  if (loading) {
    return <Spin size="large" style={{ display: 'block', margin: '120px auto' }} />;
  }

  if (error) {
    return (
      <Alert type="error" message={error}
        action={<Button onClick={fetchData}>重试</Button>} />
    );
  }

  const severityColor = (s: string) => s === 'P0' ? 'red' : s === 'P1' ? 'orange' : 'gold';

  const mixedChartOption = trend.length > 0 ? {
    tooltip: { trigger: 'axis' },
    title: { text: '近7日趋势', left: 'center', textStyle: { fontSize: 14 } },
    legend: { data: ['日新增用户', '日活跃用户'], bottom: 0 },
    grid: { top: 40, left: 40, right: 20, bottom: 40 },
    xAxis: { type: 'category', data: trend.map((d) => d.date), axisLabel: { rotate: 45 } },
    yAxis: { type: 'value' },
    series: [
      { name: '日新增用户', type: 'bar', data: trend.map((d) => d.newUsers), itemStyle: { color: '#1677ff' } },
      { name: '日活跃用户', type: 'line', data: trend.map((d) => d.activeUsers), itemStyle: { color: '#52c41a' } },
    ],
  } : null;

  const pieOption = {
    tooltip: { trigger: 'item' },
    title: { text: '登录方式分布', left: 'center', textStyle: { fontSize: 14 } },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      data: dist.map((d) => ({ name: d.method, value: d.count })),
      label: { formatter: '{b}: {d}%' },
    }],
  };

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic title="今日新增用户" value={overview?.newUsers || 0}
              prefix={<UserAddOutlined />}
              suffix={overview ? <span style={{ fontSize: 12, color: overview.registrationChange >= 0 ? '#52c41a' : '#ff4d4f' }}>{`${overview.registrationChange >= 0 ? '+' : ''}${overview.registrationChange}%`}</span> : undefined} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="活跃订阅数" value={overview?.activeSubs || 0}
              prefix={<TeamOutlined />} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="今日营收" value={overview?.revenue || 0}
              prefix={<DollarOutlined />} suffix="元" />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="在线用户" value={onlineUsers}
              prefix={<WifiOutlined />} />
          </Card>
        </Col>
      </Row>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={16}>
          <Card>
            {mixedChartOption ? (
              <ReactEChartsCore option={mixedChartOption} style={{ height: 300 }} />
            ) : (
              <div style={{ height: 300, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}>暂无趋势数据</div>
            )}
          </Card>
        </Col>
        <Col span={8}>
          <Card title="最近告警" extra={<Typography.Link onClick={() => window.location.href = '/admin/monitor/alerts'}>查看全部</Typography.Link>}>
            {alerts.length === 0 ? (
              <div style={{ color: '#999', textAlign: 'center', padding: 24 }}>暂无告警</div>
            ) : (
              <List
                dataSource={alerts.slice(0, 5)}
                renderItem={(item) => (
                  <List.Item>
                    <List.Item.Meta
                      avatar={<Badge color={severityColor(item.severity)} />}
                      title={<Typography.Text style={{ fontSize: 13 }}>{item.alertName}</Typography.Text>}
                      description={<Typography.Text type="secondary" style={{ fontSize: 11 }}>{item.condition} — {item.firedAt}</Typography.Text>}
                    />
                  </List.Item>
                )}
              />
            )}
          </Card>
        </Col>
      </Row>
      <Row>
        <Col span={8}>
          <Card><ReactEChartsCore option={pieOption} style={{ height: 300 }} /></Card>
        </Col>
      </Row>
    </div>
  );
}

export default DashboardPage;
```

- [ ] **Step 4: 运行测试验证通过**

```bash
npx vitest run src/test/pages/DashboardPage.test.tsx
```

预期: 所有测试通过

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/dashboard/DashboardPage.tsx frontend/src/test/pages/DashboardPage.test.tsx
git commit -m "feat: extend dashboard with new stat cards, 7-day trend chart, and recent alerts"
```

---

### Task 10: 前端 — MonitorOverviewPage (概览)

**Files:**
- Create: `frontend/src/pages/monitor/MonitorOverviewPage.tsx`
- Create: `frontend/src/test/pages/MonitorOverviewPage.test.tsx`

- [ ] **Step 1: 编写失败的测试**

```typescript
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import MonitorOverviewPage from '../../pages/monitor/MonitorOverviewPage';

const { mockGet } = vi.hoisted(() => ({
  mockGet: vi.fn((url: string) => {
    if (url === '/admin/monitor/health') {
      return Promise.resolve({ data: { code: 0, data: [
        { component: '应用实例', status: 'UP', instanceCount: 1 },
        { component: '数据库', status: 'UP', instanceCount: 1, responseTimeMs: 5 },
        { component: 'Redis', status: 'UP', instanceCount: 1 },
        { component: 'Nacos', status: 'UP', instanceCount: 1 },
      ] } });
    }
    if (url === '/admin/stats/overview') {
      return Promise.resolve({ data: { code: 0, data: {
        newUsers: 128, dau: 3847, todayRegistrations: 128, todayLogins: 1024,
        activeSubs: 56, revenue: 2480, onlineUsers: 47,
        loginSuccessRate: 95.5, registrationChange: 10, loginChange: 8,
      } } });
    }
    return Promise.resolve({ data: { code: 0, data: [] } });
  }),
}));

vi.mock('../../api/client', () => ({ default: { get: mockGet } }));

describe('MonitorOverviewPage', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('renders health cards for all components', async () => {
    render(<MemoryRouter><MonitorOverviewPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('应用实例')).toBeInTheDocument();
      expect(screen.getByText('数据库')).toBeInTheDocument();
      expect(screen.getByText('Redis')).toBeInTheDocument();
      expect(screen.getByText('Nacos')).toBeInTheDocument();
    });
  });

  it('shows UP status badges', async () => {
    render(<MemoryRouter><MonitorOverviewPage /></MemoryRouter>);
    await waitFor(() => {
      const upBadges = screen.getAllByText('UP');
      expect(upBadges.length).toBeGreaterThanOrEqual(4);
    });
  });

  it('renders today stats', async () => {
    render(<MemoryRouter><MonitorOverviewPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('今日 API 调用量')).toBeInTheDocument();
      expect(screen.getByText('当前在线用户')).toBeInTheDocument();
    });
  });
});
```

- [ ] **Step 2: 运行测试验证失败**

```bash
npx vitest run src/test/pages/MonitorOverviewPage.test.tsx
```

预期: FAIL — 文件不存在

- [ ] **Step 3: 创建 MonitorOverviewPage.tsx**

```typescript
import { useEffect, useState } from 'react';
import { Row, Col, Card, Statistic, Badge, Spin, Alert, Button } from 'antd';
import {
  CloudServerOutlined, DatabaseOutlined, ApiOutlined,
  BugOutlined, TeamOutlined,
} from '@ant-design/icons';
import monitorApi from '../../api/monitorApi';
import statsApi from '../../api/statsApi';
import type { HealthDto, StatsOverview } from '../../api/types';

const STATUS_COLOR: Record<string, 'success' | 'error' | 'warning' | 'default'> = {
  UP: 'success', DOWN: 'error', DEGRADED: 'warning',
};

function MonitorOverviewPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [health, setHealth] = useState<HealthDto[]>([]);
  const [overview, setOverview] = useState<StatsOverview | null>(null);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [h, ov] = await Promise.all([
        monitorApi.health(),
        statsApi.overview(),
      ]);
      setHealth(h.data?.data || []);
      setOverview(ov.data?.data || null);
    } catch {
      setError('加载监控数据失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  // 30s auto-refresh
  useEffect(() => {
    const timer = setInterval(fetchData, 30000);
    return () => clearInterval(timer);
  }, []);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '120px auto' }} />;
  if (error) return <Alert type="error" message={error} action={<Button onClick={fetchData}>重试</Button>} />;

  return (
    <div>
      <h3 style={{ marginBottom: 16 }}>服务健康</h3>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        {health.map((h) => (
          <Col span={6} key={h.component}>
            <Card>
              <Statistic
                title={h.component}
                valueRender={() => (
                  <Badge status={STATUS_COLOR[h.status] || 'default'} text={h.status} />
                )}
              />
              {h.responseTimeMs !== undefined && (
                <div style={{ fontSize: 12, color: '#999' }}>
                  响应时间: {h.responseTimeMs}ms
                </div>
              )}
              <div style={{ fontSize: 11, color: '#999' }}>
                实例数: {h.instanceCount}
              </div>
            </Card>
          </Col>
        ))}
      </Row>

      <h3 style={{ marginBottom: 16 }}>今日概览</h3>
      <Row gutter={16}>
        <Col span={8}>
          <Card>
            <Statistic title="今日 API 调用量" value={overview?.todayLogins || 0}
              prefix={<ApiOutlined />} />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic title="今日错误数" value={0}
              prefix={<BugOutlined />} />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic title="当前在线用户" value={overview?.onlineUsers || 0}
              prefix={<TeamOutlined />} />
          </Card>
        </Col>
      </Row>
    </div>
  );
}

export default MonitorOverviewPage;
```

- [ ] **Step 4: 运行测试验证通过**

```bash
npx vitest run src/test/pages/MonitorOverviewPage.test.tsx
```

预期: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/monitor/MonitorOverviewPage.tsx frontend/src/test/pages/MonitorOverviewPage.test.tsx
git commit -m "feat: add MonitorOverviewPage with health cards and today stats"
```

---

### Task 11: 前端 — MonitorMetricsPage (API 指标)

**Files:**
- Create: `frontend/src/pages/monitor/MonitorMetricsPage.tsx`
- Create: `frontend/src/test/pages/MonitorMetricsPage.test.tsx`

- [ ] **Step 1: 编写失败的测试**

```typescript
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import MonitorMetricsPage from '../../pages/monitor/MonitorMetricsPage';

const { mockGet } = vi.hoisted(() => ({
  mockGet: vi.fn(() => Promise.resolve({ data: { code: 0, data: {
    qps: [{ timestamp: 1716019200, value: 10.5 }],
    latencyP50: [], latencyP95: [], latencyP99: [], errorRate: [],
  } } })),
}));

vi.mock('../../api/client', () => ({ default: { get: mockGet } }));
vi.mock('echarts-for-react/lib/core', () => ({ default: () => null }));

describe('MonitorMetricsPage', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('renders time range selector', async () => {
    render(<MemoryRouter><MonitorMetricsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('1小时')).toBeInTheDocument();
      expect(screen.getByText('6小时')).toBeInTheDocument();
      expect(screen.getByText('24小时')).toBeInTheDocument();
      expect(screen.getByText('7天')).toBeInTheDocument();
    });
  });

  it('fetches metrics on mount with default range', async () => {
    render(<MemoryRouter><MonitorMetricsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/admin/monitor/metrics', expect.objectContaining({ params: { range: '24h' } }));
    });
  });

  it('switches time range on click', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><MonitorMetricsPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('1小时'));
    await user.click(screen.getByText('1小时'));
    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/admin/monitor/metrics', expect.objectContaining({ params: { range: '1h' } }));
    });
  });
});
```

- [ ] **Step 2: 运行测试验证失败**

```bash
npx vitest run src/test/pages/MonitorMetricsPage.test.tsx
```

预期: FAIL — 文件不存在

- [ ] **Step 3: 创建 MonitorMetricsPage.tsx**

```typescript
import { useEffect, useState, useCallback } from 'react';
import { Radio, Spin, Alert, Button, Row, Col, Card } from 'antd';
import ReactEChartsCore from 'echarts-for-react/lib/core';
import * as echarts from 'echarts/core';
import { LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent, TitleComponent, LegendComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import monitorApi from '../../api/monitorApi';
import type { MetricsDto, MetricPoint } from '../../api/types';

echarts.use([LineChart, GridComponent, TooltipComponent, TitleComponent, LegendComponent, CanvasRenderer]);

const RANGES = [
  { label: '1小时', value: '1h' },
  { label: '6小时', value: '6h' },
  { label: '24小时', value: '24h' },
  { label: '7天', value: '7d' },
];

function makeLineOption(data: MetricPoint[], title: string, color: string) {
  if (data.length === 0) return null;
  return {
    tooltip: { trigger: 'axis' },
    title: { text: title, left: 'center', textStyle: { fontSize: 13 } },
    grid: { top: 40, left: 50, right: 20, bottom: 30 },
    xAxis: {
      type: 'category',
      data: data.map((d) => new Date(d.timestamp * 1000).toLocaleTimeString()),
    },
    yAxis: { type: 'value' },
    series: [{
      type: 'line', data: data.map((d) => d.value),
      smooth: true, showSymbol: false,
      itemStyle: { color }, areaStyle: { color: color + '20' },
    }],
  };
}

function MonitorMetricsPage() {
  const [range, setRange] = useState('24h');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [metrics, setMetrics] = useState<MetricsDto | null>(null);

  const fetchMetrics = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const resp = await monitorApi.metrics(range);
      setMetrics(resp.data?.data || null);
    } catch {
      setError('加载指标数据失败');
    } finally {
      setLoading(false);
    }
  }, [range]);

  useEffect(() => { fetchMetrics(); }, [fetchMetrics]);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '120px auto' }} />;
  if (error) return <Alert type="error" message={error} action={<Button onClick={fetchMetrics}>重试</Button>} />;

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Radio.Group
          value={range}
          onChange={(e) => setRange(e.target.value)}
          optionType="button" buttonStyle="solid"
          options={RANGES}
        />
      </div>
      <Row gutter={16}>
        <Col span={24} style={{ marginBottom: 16 }}>
          <Card>
            {metrics?.qps && metrics.qps.length > 0 ? (
              <ReactEChartsCore option={makeLineOption(metrics.qps, 'QPS', '#1677ff')!} style={{ height: 250 }} />
            ) : <div style={{ height: 250, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}>暂无 QPS 数据</div>}
          </Card>
        </Col>
        <Col span={24} style={{ marginBottom: 16 }}>
          <Card>
            {metrics?.latencyP50 && metrics.latencyP50.length > 0 ? (
              <ReactEChartsCore option={{
                ...makeLineOption(metrics.latencyP50, '延迟 (P50/P95/P99)', '#52c41a')!,
                series: [
                  { type: 'line', data: metrics.latencyP50.map((d) => d.value), smooth: true, showSymbol: false, name: 'P50', itemStyle: { color: '#52c41a' } },
                  { type: 'line', data: metrics.latencyP95.map((d) => d.value), smooth: true, showSymbol: false, name: 'P95', itemStyle: { color: '#faad14' } },
                  { type: 'line', data: metrics.latencyP99.map((d) => d.value), smooth: true, showSymbol: false, name: 'P99', itemStyle: { color: '#ff4d4f' } },
                ],
              }} style={{ height: 250 }} />
            ) : <div style={{ height: 250, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}>暂无延迟数据</div>}
          </Card>
        </Col>
        <Col span={24}>
          <Card>
            {metrics?.errorRate && metrics.errorRate.length > 0 ? (
              <ReactEChartsCore option={makeLineOption(metrics.errorRate, '错误率 (5xx)', '#ff4d4f')!} style={{ height: 250 }} />
            ) : <div style={{ height: 250, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}>暂无错误率数据</div>}
          </Card>
        </Col>
      </Row>
    </div>
  );
}

export default MonitorMetricsPage;
```

- [ ] **Step 4: 运行测试验证通过**

```bash
npx vitest run src/test/pages/MonitorMetricsPage.test.tsx
```

预期: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/monitor/MonitorMetricsPage.tsx frontend/src/test/pages/MonitorMetricsPage.test.tsx
git commit -m "feat: add MonitorMetricsPage with QPS/latency/error rate charts"
```

---

### Task 12: 前端 — MonitorLogsPage (日志检索)

**Files:**
- Create: `frontend/src/pages/monitor/MonitorLogsPage.tsx`
- Create: `frontend/src/test/pages/MonitorLogsPage.test.tsx`

- [ ] **Step 1: 编写失败的测试**

```typescript
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import MonitorLogsPage from '../../pages/monitor/MonitorLogsPage';

const mockGet = vi.fn(() =>
  Promise.resolve({ data: { code: 0, data: { records: [], total: 0, page: 1, size: 20, pages: 0 } } })
);

vi.mock('../../api/client', () => ({ default: { get: mockGet } }));

describe('MonitorLogsPage', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('renders 4 tabs', async () => {
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('应用日志')).toBeInTheDocument();
      expect(screen.getByText('安全日志')).toBeInTheDocument();
      expect(screen.getByText('访问日志')).toBeInTheDocument();
      expect(screen.getByText('慢查询')).toBeInTheDocument();
    });
  });

  it('switches tabs and shows different filters', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('安全日志'));
    await user.click(screen.getByText('安全日志'));
    await waitFor(() => {
      expect(screen.getByText('类型')).toBeInTheDocument();
    });
  });

  it('shows no data state for access log and slow query tabs', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><MonitorLogsPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('访问日志'));
    await user.click(screen.getByText('访问日志'));
    await waitFor(() => {
      expect(screen.getByText(/暂无数据/)).toBeInTheDocument();
    });
  });
});
```

- [ ] **Step 2: 运行测试验证失败**

```bash
npx vitest run src/test/pages/MonitorLogsPage.test.tsx
```

预期: FAIL — 文件不存在

- [ ] **Step 3: 创建 MonitorLogsPage.tsx**

```typescript
import { useEffect, useState } from 'react';
import { Tabs, Table, DatePicker, Select, Input, Button, Space, Empty } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import apiClient from '../../api/client';
import type { LoginLogDto } from '../../api/types';

const { RangePicker } = DatePicker;

interface LogRecord {
  id: number;
  time: string;
  level: string;
  logger: string;
  message: string;
  traceId?: string;
}

const LEVEL_COLORS: Record<string, string> = {
  ERROR: '#ff4d4f', WARN: '#faad14', INFO: '#52c41a', DEBUG: '#1677ff', TRACE: '#999',
};

const APP_LOG_COLUMNS: ColumnsType<LogRecord> = [
  { title: '时间', dataIndex: 'time', width: 160 },
  {
    title: '级别', dataIndex: 'level', width: 80,
    render: (level: string) => <span style={{ color: LEVEL_COLORS[level] || '#999', fontWeight: 600 }}>{level}</span>,
  },
  { title: 'Logger', dataIndex: 'logger', width: 200, ellipsis: true },
  { title: '消息', dataIndex: 'message', ellipsis: true,
    render: (msg: string) => msg.length > 80 ? msg.slice(0, 80) + '...' : msg,
  },
  { title: 'Trace ID', dataIndex: 'traceId', width: 130, ellipsis: true,
    render: (id?: string) => id ? <a>{id}</a> : '-' },
];

const SECURITY_LOG_COLUMNS: ColumnsType<LoginLogDto> = [
  { title: '时间', dataIndex: 'time', width: 160 },
  { title: '用户', dataIndex: 'username', width: 120 },
  { title: '操作', dataIndex: 'action', width: 100 },
  {
    title: '类型', dataIndex: 'type', width: 100,
    render: (t: string) => <span style={{ color: LEVEL_COLORS[t] || '#666' }}>{t}</span>,
  },
  { title: '结果', dataIndex: 'result', width: 80 },
  { title: 'IP', dataIndex: 'ip', width: 130 },
  { title: '设备', dataIndex: 'device', ellipsis: true },
];

function MonitorLogsPage() {
  const [activeTab, setActiveTab] = useState('app');
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<LogRecord[]>([]);
  const [securityData, setSecurityData] = useState<LoginLogDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [level, setLevel] = useState<string | undefined>();
  const [type, setType] = useState<string | undefined>();

  const fetchAppLogs = async () => {
    setLoading(true);
    try {
      const resp = await apiClient.get('/admin/logs/app', {
        params: { page, size: 20, keyword: keyword || undefined, level },
      });
      const body = resp.data?.data;
      setData(body?.records || []);
      setTotal(body?.total || 0);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  };

  const fetchSecurityLogs = async () => {
    setLoading(true);
    try {
      const resp = await apiClient.get('/admin/logs/security', {
        params: { page, size: 20, type },
      });
      const body = resp.data?.data;
      setSecurityData(body?.records || []);
      setTotal(body?.total || 0);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  };

  useEffect(() => {
    if (activeTab === 'app') fetchAppLogs();
    else if (activeTab === 'security') fetchSecurityLogs();
  }, [activeTab, page]);

  const handleSearch = () => {
    setPage(1);
    if (activeTab === 'app') fetchAppLogs();
    else if (activeTab === 'security') fetchSecurityLogs();
  };

  const tabItems = [
    {
      key: 'app', label: '应用日志',
      children: (
        <div>
          <Space style={{ marginBottom: 16 }}>
            <Select placeholder="级别" allowClear style={{ width: 120 }}
              value={level} onChange={(v) => setLevel(v)}
              options={['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'].map((l) => ({ label: l, value: l }))} />
            <Input placeholder="关键词搜索" value={keyword}
              onChange={(e) => setKeyword(e.target.value)} style={{ width: 200 }}
              onPressEnter={handleSearch} />
            <Button icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
          </Space>
          <Table columns={APP_LOG_COLUMNS} dataSource={data} rowKey="id"
            loading={loading} size="small"
            pagination={{ current: page, total, pageSize: 20, onChange: setPage }} />
        </div>
      ),
    },
    {
      key: 'security', label: '安全日志',
      children: (
        <div>
          <Space style={{ marginBottom: 16 }}>
            <Select placeholder="类型" allowClear style={{ width: 120 }}
              value={type} onChange={(v) => setType(v)}
              options={['LOGIN', 'LOGOUT', 'CAPTCHA', 'RATE_LIMIT'].map((l) => ({ label: l, value: l }))} />
            <Button icon={<SearchOutlined />} onClick={handleSearch}>搜索</Button>
          </Space>
          <Table columns={SECURITY_LOG_COLUMNS} dataSource={securityData} rowKey="id"
            loading={loading} size="small"
            pagination={{ current: page, total, pageSize: 20, onChange: setPage }} />
        </div>
      ),
    },
    {
      key: 'access', label: '访问日志',
      children: <Empty description="暂无数据 — 访问日志表尚未创建，后续迁移建表后补齐" />,
    },
    {
      key: 'slow-query', label: '慢查询',
      children: <Empty description="暂无数据 — 慢查询日志表尚未创建，后续迁移建表后补齐" />,
    },
  ];

  return (
    <div>
      <h3 style={{ marginBottom: 16 }}>日志检索</h3>
      <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />
    </div>
  );
}

export default MonitorLogsPage;
```

- [ ] **Step 4: 运行测试验证通过**

```bash
npx vitest run src/test/pages/MonitorLogsPage.test.tsx
```

预期: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/monitor/MonitorLogsPage.tsx frontend/src/test/pages/MonitorLogsPage.test.tsx
git commit -m "feat: add MonitorLogsPage with app/security/access/slow-query tabs"
```

---

### Task 13: 前端 — MonitorAlertsPage (告警面板)

**Files:**
- Create: `frontend/src/pages/monitor/MonitorAlertsPage.tsx`
- Create: `frontend/src/test/pages/MonitorAlertsPage.test.tsx`

- [ ] **Step 1: 编写失败的测试**

```typescript
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import MonitorAlertsPage from '../../pages/monitor/MonitorAlertsPage';

const { mockGet } = vi.hoisted(() => ({
  mockGet: vi.fn(() => Promise.resolve({ data: { code: 0, data: [
    { alertName: 'CPU过高', severity: 'P0', condition: 'cpu>90%',
      currentValue: '94.3%', status: 'FIRING', firedAt: '2026-05-23T17:42:00' },
    { alertName: '内存不足', severity: 'P1', condition: 'memory>85%',
      currentValue: '87.2%', status: 'RESOLVED', firedAt: '2026-05-23T16:18:00' },
  ] } })),
}));

vi.mock('../../api/client', () => ({ default: { get: mockGet } }));

describe('MonitorAlertsPage', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('renders alert summary', async () => {
    render(<MemoryRouter><MonitorAlertsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('告警总数')).toBeInTheDocument();
    });
  });

  it('renders alert list with severity badges', async () => {
    render(<MemoryRouter><MonitorAlertsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('CPU过高')).toBeInTheDocument();
      expect(screen.getByText('内存不足')).toBeInTheDocument();
    });
  });

  it('shows status badges', async () => {
    render(<MemoryRouter><MonitorAlertsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('FIRING')).toBeInTheDocument();
      expect(screen.getByText('RESOLVED')).toBeInTheDocument();
    });
  });
});
```

- [ ] **Step 2: 运行测试验证失败**

```bash
npx vitest run src/test/pages/MonitorAlertsPage.test.tsx
```

预期: FAIL — 文件不存在

- [ ] **Step 3: 创建 MonitorAlertsPage.tsx**

```typescript
import { useEffect, useState } from 'react';
import { Table, Tag, Select, Row, Col, Card, Statistic, Spin, Alert, Button } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import monitorApi from '../../api/monitorApi';
import type { AlertDto } from '../../api/types';

const SEVERITY_COLORS: Record<string, string> = { P0: 'red', P1: 'orange', P2: 'gold' };
const STATUS_COLORS: Record<string, string> = { FIRING: 'red', RESOLVED: 'green' };

const COLUMNS: ColumnsType<AlertDto> = [
  { title: '告警名称', dataIndex: 'alertName', width: 160 },
  {
    title: '级别', dataIndex: 'severity', width: 80,
    render: (s: string) => <Tag color={SEVERITY_COLORS[s] || 'default'}>{s}</Tag>,
  },
  { title: '条件', dataIndex: 'condition', ellipsis: true },
  { title: '当前值', dataIndex: 'currentValue', width: 140, ellipsis: true },
  {
    title: '状态', dataIndex: 'status', width: 100,
    render: (s: string) => <Tag color={STATUS_COLORS[s] || 'default'}>{s}</Tag>,
  },
  { title: '触发时间', dataIndex: 'firedAt', width: 180 },
];

function MonitorAlertsPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [alerts, setAlerts] = useState<AlertDto[]>([]);
  const [statusFilter, setStatusFilter] = useState<string>();
  const [severityFilter, setSeverityFilter] = useState<string>();

  const fetchAlerts = async () => {
    setLoading(true);
    setError(null);
    try {
      const resp = await monitorApi.alerts({
        status: statusFilter, severity: severityFilter,
      });
      setAlerts(resp.data?.data || []);
    } catch {
      setError('加载告警数据失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchAlerts(); }, [statusFilter, severityFilter]);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '120px auto' }} />;
  if (error) return <Alert type="error" message={error} action={<Button onClick={fetchAlerts}>重试</Button>} />;

  const firingCount = alerts.filter((a) => a.status === 'FIRING').length;

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}><Card><Statistic title="告警总数" value={alerts.length} /></Card></Col>
        <Col span={6}><Card><Statistic title="FIRING" value={firingCount} valueStyle={{ color: '#ff4d4f' }} /></Card></Col>
        <Col span={6}><Card><Statistic title="RESOLVED" value={alerts.length - firingCount} valueStyle={{ color: '#52c41a' }} /></Card></Col>
      </Row>
      <div style={{ marginBottom: 16 }}>
        <Select placeholder="状态筛选" allowClear style={{ width: 140, marginRight: 8 }}
          value={statusFilter} onChange={setStatusFilter}
          options={['FIRING', 'RESOLVED'].map((s) => ({ label: s, value: s }))} />
        <Select placeholder="级别筛选" allowClear style={{ width: 140 }}
          value={severityFilter} onChange={setSeverityFilter}
          options={['P0', 'P1', 'P2'].map((s) => ({ label: s, value: s }))} />
      </div>
      <Table columns={COLUMNS} dataSource={alerts} rowKey="alertName"
        pagination={{ pageSize: 20, showSizeChanger: true }} size="small" />
    </div>
  );
}

export default MonitorAlertsPage;
```

- [ ] **Step 4: 运行测试验证通过**

```bash
npx vitest run src/test/pages/MonitorAlertsPage.test.tsx
```

预期: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/monitor/MonitorAlertsPage.tsx frontend/src/test/pages/MonitorAlertsPage.test.tsx
git commit -m "feat: add MonitorAlertsPage with summary cards and alert list"
```

---

### Task 14: 前端 — LogLevelSettingsPage (日志级别, SUPER_ADMIN)

**Files:**
- Create: `frontend/src/pages/monitor/LogLevelSettingsPage.tsx`
- Create: `frontend/src/test/pages/LogLevelSettingsPage.test.tsx`

- [ ] **Step 1: 编写失败的测试**

```typescript
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import LogLevelSettingsPage from '../../pages/monitor/LogLevelSettingsPage';

const { mockGet, mockPost } = vi.hoisted(() => ({
  mockGet: vi.fn((url: string) => {
    if (url === '/admin/monitor/loggers') {
      return Promise.resolve({ data: { code: 0, data: [
        { name: 'com.zhiyu', configuredLevel: 'INFO', effectiveLevel: 'INFO' },
        { name: 'org.springframework', configuredLevel: 'WARN', effectiveLevel: 'WARN' },
      ] } });
    }
    if (url === '/admin/monitor/loggers/history') {
      return Promise.resolve({ data: { code: 0, data: [] } });
    }
    return Promise.resolve({ data: { code: 0, data: [] } });
  }),
  mockPost: vi.fn(() => Promise.resolve({ data: { code: 0 } })),
}));

vi.mock('../../api/client', () => ({ default: { get: mockGet, post: mockPost } }));

describe('LogLevelSettingsPage', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('renders logger list', async () => {
    render(<MemoryRouter><LogLevelSettingsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('com.zhiyu')).toBeInTheDocument();
      expect(screen.getByText('org.springframework')).toBeInTheDocument();
    });
  });

  it('renders level select for each logger', async () => {
    render(<MemoryRouter><LogLevelSettingsPage /></MemoryRouter>);
    await waitFor(() => {
      const selectors = document.querySelectorAll('.ant-select-selector');
      expect(selectors.length).toBeGreaterThan(0);
    });
  });

  it('renders adjustment history section', async () => {
    render(<MemoryRouter><LogLevelSettingsPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByText('调整记录')).toBeInTheDocument();
    });
  });
});
```

- [ ] **Step 2: 运行测试验证失败**

```bash
npx vitest run src/test/pages/LogLevelSettingsPage.test.tsx
```

预期: FAIL — 文件不存在

- [ ] **Step 3: 创建 LogLevelSettingsPage.tsx**

```typescript
import { useEffect, useState } from 'react';
import { Table, Select, Button, message, Spin, Alert, Space, Popconfirm } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import monitorApi from '../../api/monitorApi';
import type { LoggerDto, LogLevelHistoryDto } from '../../api/types';

const LEVELS = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'OFF'];
const LEVEL_COLORS: Record<string, string> = {
  TRACE: '#999', DEBUG: '#1677ff', INFO: '#52c41a', WARN: '#faad14', ERROR: '#ff4d4f', OFF: '#666',
};

const LOGGER_COLUMNS: ColumnsType<LoggerDto> = [
  { title: 'Logger 名称', dataIndex: 'name', width: 300, ellipsis: true },
  {
    title: '当前级别', dataIndex: 'configuredLevel', width: 120,
    render: (level: string) => <span style={{ color: LEVEL_COLORS[level] || '#666', fontWeight: 600 }}>{level}</span>,
  },
  {
    title: '生效级别', dataIndex: 'effectiveLevel', width: 120,
    render: (level: string) => <span style={{ color: LEVEL_COLORS[level] || '#666' }}>{level}</span>,
  },
  {
    title: '操作', key: 'action', width: 260,
    render: (_: unknown, record: LoggerDto) => (
      <LevelAdjustCell logger={record} onAdjusted={() => {}} />
    ),
  },
];

function LevelAdjustCell({ logger, onAdjusted }: { logger: LoggerDto; onAdjusted: () => void }) {
  const [selectedLevel, setSelectedLevel] = useState(logger.configuredLevel);
  const [adjusting, setAdjusting] = useState(false);

  const handleAdjust = async () => {
    if (selectedLevel === logger.configuredLevel) return;
    setAdjusting(true);
    try {
      await monitorApi.setLoggerLevel(logger.name, selectedLevel);
      message.success(`已将 ${logger.name} 调整为 ${selectedLevel}`);
      onAdjusted();
    } catch {
      message.error('调整失败');
    } finally {
      setAdjusting(false);
    }
  };

  return (
    <Space>
      <Select size="small" value={selectedLevel} onChange={setSelectedLevel} style={{ width: 100 }}>
        {LEVELS.map((l) => (
          <Select.Option key={l} value={l}>
            <span style={{ color: LEVEL_COLORS[l] }}>{l}</span>
          </Select.Option>
        ))}
      </Select>
      <Popconfirm title="确认修改日志级别？" onConfirm={handleAdjust}
        disabled={selectedLevel === logger.configuredLevel}>
        <Button size="small" type="primary" loading={adjusting}
          disabled={selectedLevel === logger.configuredLevel}>
          调整
        </Button>
      </Popconfirm>
    </Space>
  );
}

const HISTORY_COLUMNS: ColumnsType<LogLevelHistoryDto> = [
  { title: '时间', dataIndex: 'createdAt', width: 160 },
  { title: 'Logger', dataIndex: 'loggerName', width: 200, ellipsis: true },
  { title: '旧级别', dataIndex: 'oldLevel', width: 80,
    render: (l: string) => <span style={{ color: LEVEL_COLORS[l] || '#666' }}>{l}</span> },
  { title: '新级别', dataIndex: 'newLevel', width: 80,
    render: (l: string) => <span style={{ color: LEVEL_COLORS[l] || '#666' }}>{l}</span> },
  { title: '操作人', dataIndex: 'changedBy', width: 100 },
  { title: '自动回滚', dataIndex: 'expireAt', width: 160,
    render: (t: string | null) => t ? new Date(t).toLocaleString() : '-' },
];

function LogLevelSettingsPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loggers, setLoggers] = useState<LoggerDto[]>([]);
  const [history, setHistory] = useState<LogLevelHistoryDto[]>([]);
  const [search, setSearch] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [l, h] = await Promise.all([
        monitorApi.loggers(),
        monitorApi.loggerHistory(),
      ]);
      setLoggers(l.data?.data || []);
      setHistory(h.data?.data || []);
    } catch {
      setError('加载日志级别数据失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, [refreshKey]);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '120px auto' }} />;
  if (error) return <Alert type="error" message={error} action={<Button onClick={fetchData}>重试</Button>} />;

  const filtered = search
    ? loggers.filter((l) => l.name.toLowerCase().includes(search.toLowerCase()))
    : loggers;

  return (
    <div>
      <h3 style={{ marginBottom: 16 }}>日志级别管理</h3>
      <Table columns={LOGGER_COLUMNS}
        dataSource={filtered.map((l, i) => ({ ...l, key: l.name }))}
        rowKey="name" size="small"
        pagination={{ pageSize: 20, showSizeChanger: true }}
        title={() => (
          <Input.Search placeholder="搜索 Logger" value={search}
            onChange={(e) => setSearch(e.target.value)} style={{ width: 300 }} />
        )} />
      <h4 style={{ marginTop: 24, marginBottom: 12 }}>调整记录</h4>
      <Table columns={HISTORY_COLUMNS} dataSource={history} rowKey="id"
        size="small" pagination={{ pageSize: 10 }} />
    </div>
  );
}

export default LogLevelSettingsPage;
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
npx vitest run src/test/pages/LogLevelSettingsPage.test.tsx
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/monitor/LogLevelSettingsPage.tsx frontend/src/test/pages/LogLevelSettingsPage.test.tsx
git commit -m "feat: add LogLevelSettingsPage with logger level adjustment and history"
```

---

## 验证步骤

1. **后端编译**: `./mvnw -f backend/pom.xml compile -pl zhiyu-admin -am -q`
2. **前端类型检查**: `npx tsc --noEmit`
3. **前端测试**: `npx vitest run` — 所有测试通过
4. **前端构建**: `npm run build` — 生成有效产物
5. **手动验证**:
   - 仪表盘页面显示新的 4 张 StatCard + 折柱混合趋势图 + 最近告警列表
   - 侧边栏"运行监控"菜单展开显示 5 个子菜单项
   - 概览页面显示 4 张健康卡片 + 3 张今日统计
   - API 指标页面可切换时间范围，展示 QPS/延迟/错误率图表
   - 日志检索页面 4 个 Tab 切换正常，应用日志和安全日志可筛选
   - 告警面板显示告警汇总 + 列表，支持状态和级别筛选
   - 日志级别页面显示 Logger 列表，可调整级别

## 暂不实现

- 日志级别自动回滚调度任务（`@Scheduled`）— 需后续补充
- log_level_history 表写入逻辑 — 需后续补充
- 访问日志和慢查询日志数据源 — 待 DB 表创建后补齐
- 应用日志 app_log 表数据查询 — 当前 app_log 表已有 schema (V1.6.0)，需确认是否已有数据写入
- Grafana iframe 嵌入
- Prometheus AlertManager 告警规则配置
