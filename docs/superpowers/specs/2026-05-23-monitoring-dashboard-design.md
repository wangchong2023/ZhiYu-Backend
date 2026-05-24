# 运行监控与仪表盘 Design Spec

**Date:** 2026-05-23
**Status:** Approved
**Based on:** `docs/product-design/FRONTEND-DESIGN.md` §2.2 + §2.12, `docs/deploy-ops/OPS.md` §2

## Overview

完善两处与监控相关的页面：

1. **仪表盘 `/admin/dashboard`** — 扩展现有 Dashboard，对齐 FRONTEND-DESIGN §2.2 设计
2. **运行监控 `/admin/monitor`** — 新建 5 个子路由页面，对齐 FRONTEND-DESIGN §2.12 设计

基础设施层（Prometheus、Grafana、mysqld_exporter、AlertManager）遵循 OPS.md §2 设计，管理后台通过 API 读取 Prometheus/AlertManager 数据展示。

## Route 结构

```
/admin/dashboard              # 仪表盘（已有，扩展）
/admin/monitor/overview       # 服务健康概览
/admin/monitor/metrics        # API 指标
/admin/monitor/logs           # 日志检索
/admin/monitor/alerts         # 告警面板
/admin/monitor/settings       # 日志级别调整 (仅 SUPER_ADMIN)
```

## Design Decisions

| Decision | Choice | Source |
|----------|--------|--------|
| 路由结构 | 独立子路由（侧边栏子菜单展开） | FRONTEND-DESIGN §2.12 |
| 基础设施监控 | Prometheus + Grafana，管理后台只读展示 | OPS.md §2 |
| 告警来源 | Prometheus AlertManager API 只读 | FRONTEND-DESIGN §2.12.4 |
| 日志级别调整 | Actuator loggers endpoint 代理 | FRONTEND-DESIGN §2.12.5 |

---

## 1. 仪表盘 `/admin/dashboard` [扩展]

### 当前状态 vs 目标

| 组件 | 当前 | 目标 |
|------|------|------|
| StatCard 1 | 今日注册 | 今日新增用户 (Trend: ↑↓%) → onClick → /admin/users |
| StatCard 2 | 今日登录 | 活跃订阅数 |
| StatCard 3 | DAU | 今日营收 |
| StatCard 4 | 登录成功率 | 在线用户 (实时轮询 10s) |
| TrendChart | 注册趋势 + DAU 趋势（两张图） | 7日趋势 ECharts 折柱混合：日新增用户(bar) + 日活跃用户(line) |
| RecentAlerts | 无 | 最近告警列表（最多 5 条）→ onClick → /admin/monitor/alerts |

### API 端点

| 端点 | 说明 |
|------|------|
| `GET /admin/stats/overview` | **扩展返回**：newUsers, activeSubs, revenue, onlineUsers |
| `GET /admin/stats/trend` | **替代**现有 register-trend + dau-trend：返回 7 日 [{ date, newUsers, activeUsers }] |
| `GET /admin/monitor/alerts/recent` | 最近 5 条 FIRING 告警（来自 Prometheus AlertManager） |

---

## 2. 运行监控 `/admin/monitor`

侧边栏新增"运行监控"菜单项（`MonitorOutlined`），hover 展开子菜单：概览 / API 指标 / 日志检索 / 告警面板 / 日志级别。

### 2.1 概览 `/admin/monitor/overview`

```
MonitorOverviewPage
├── PageHeader ("运行监控")
├── HealthCards (4 卡片)
│   ├── HealthCard (应用实例) [UP/DOWN + 实例数]
│   ├── HealthCard (数据库)     [响应时间 ms]
│   ├── HealthCard (Redis)      [连接状态]
│   └── HealthCard (Nacos)      [连接状态]
├── TodayStats (3 卡片)
│   ├── 今日 API 调用量
│   ├── 今日错误数
│   └── 当前在线用户
└── HealthStatusBadge: green(UP) / yellow(DEGRADED) / red(DOWN)
```

**API:** `GET /admin/monitor/health` — 聚合 Actuator health，返回各组件状态 + 实例数 + DB 响应时间。

**刷新:** 30s 定时轮询 HealthCards + TodayStats。

### 2.2 API 指标 `/admin/monitor/metrics`

```
MonitorMetricsPage
├── TimeRangeSelector (1h | 6h | 24h | 7d)
├── QPSChart (ECharts area chart)
├── LatencyChart (P50/P95/P99 ECharts line chart)
└── ErrorRateChart (5xx rate ECharts line chart)
```

**API:** `GET /admin/monitor/metrics?range=1h|6h|24h|7d`

后端从 Prometheus HTTP API 查询 HTTP 请求指标：
- `rate(http_server_requests_seconds_count[<range>])` → QPS
- `histogram_quantile(0.5/0.95/0.99, rate(http_server_requests_seconds_bucket[<range>]))` → Latency
- `rate(http_server_requests_seconds_count{status=~"5.."}[<range>]) / rate(http_server_requests_seconds_count[<range>])` → Error rate

**刷新:** 页面加载 + TimeRangeSelector 切换时拉取。无自动轮询。

### 2.3 日志检索 `/admin/monitor/logs`

```
MonitorLogsPage
├── Tabs
│   ├── Tab: 访问日志
│   ├── Tab: 应用日志
│   ├── Tab: 慢查询
│   └── Tab: 安全日志
├── SearchForm (按 Tab 动态切换 filter)
│   ├── [通用] DateRangePicker
│   ├── [访问] PathInput, StatusSelect, IpInput
│   ├── [应用] LevelSelect, KeywordInput
│   ├── [慢查询] MinMsInput
│   └── [安全] TypeSelect, IpInput
├── LogTable (可展开详情)
│   Columns: 时间 | Level | Logger | Message(截断) | traceId
│   ExpandRow: 完整 message + 异常堆栈 (代码块)
│   traceId 可点击 → 过滤该 traceId 的所有日志
└── LogStatsPanel (底部折叠)
    └── 7 天趋势图 + 按级别分布饼图
```

**API:**

| 端点 | Tab | 数据源 |
|------|-----|--------|
| `GET /admin/logs/access` | 访问日志 | nginx access log 解析存入 DB，或直接查 nginx 日志文件 |
| `GET /admin/logs/app` | 应用日志 | `app_log` 表（V1.6.0 已建） |
| `GET /admin/logs/slow-query` | 慢查询 | MySQL slow_query_log 解析存入 DB，或直接查 slow log |
| `GET /admin/logs/security` | 安全日志 | `auth_user_log` 表（登录/注销/验证码/限流） |

所有端点支持：分页、时间范围筛选、Tab 特有 filter。

**实现策略：** 访问日志和慢查询日志 — 若 DB 中暂无对应表，先用 `app_log` + `auth_user_log` 实现应用日志和安全日志两个 Tab；访问日志和慢查询标记为"暂无数据"状态，后续迁移建表后补齐。

**LogStatsPanel API:** `GET /admin/logs/stats?days=7` — 返回 7 天内每日日志量趋势 + 按级别分布。

### 2.4 告警面板 `/admin/monitor/alerts`

```
MonitorAlertsPage
├── AlertSummary (今日告警总数 | P0/P1/P2 分别)
├── AlertList
│   └── AlertItem
│       ├── SeverityBadge (P0=red, P1=orange, P2=yellow)
│       ├── AlertName, Condition, CurrentValue, FiredAt
│       └── Status (FIRING | RESOLVED)
└── 所有数据只读，来自 Prometheus AlertManager
```

**API:** `GET /admin/monitor/alerts` — 后端代理 Prometheus AlertManager API (`/api/v2/alerts`)，转换格式后返回。

筛选：状态（FIRING/RESOLVED）、严重级别（P0/P1/P2）、时间范围。

### 2.5 日志级别 `/admin/monitor/settings` (仅 SUPER_ADMIN)

```
LogLevelSettingsPage
├── PageHeader
├── LoggerList (搜索 + 表格)
│   Columns: Logger名称 | 当前级别 | 操作
│   Row Actions: [调整] → LevelSelect + ConfirmButton
├── LevelSelect (TRACE | DEBUG | INFO | WARN | ERROR | OFF)
└── AdjustmentHistory (调整记录)
    Column: 时间 | 操作人 | Logger | 旧级别→新级别 | 自动回滚时间
```

**API:**

| 端点 | 说明 |
|------|------|
| `GET /admin/monitor/loggers` | 列出所有 logger 及当前级别（代理 Actuator `/loggers`） |
| `POST /admin/monitor/loggers/{name}` | 修改 logger 级别 `{ "configuredLevel": "DEBUG" }` |
| `GET /admin/monitor/loggers/history` | 调整历史记录（需新建 `log_level_history` 表） |

**自动回滚:** 修改日志级别时允许设置过期时间（默认 30 分钟），后端 `@Scheduled` 定时任务检查过期配置并恢复到旧级别。

**权限控制:** 路由 `ProtectedRoute permission="monitor.settings"` + 菜单项仅 SUPER_ADMIN 可见。

### DB 变更

**log_level_history** 表（新 migration `V1.9.0__add_monitor_tables.sql`）：
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

## 侧边栏变更

`AdminLayout.tsx` 菜单 items 中新增：

```typescript
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
}
```

`MonitorOutlined` 来自 `@ant-design/icons`。

## 路由变更

`App.tsx` 中新增：

```tsx
// 运行监控子路由
<Route path="/admin/monitor" element={<MonitorLayout />}>
  <Route index element={<Navigate to="/admin/monitor/overview" replace />} />
  <Route path="overview" element={<MonitorOverviewPage />} />
  <Route path="metrics" element={<MonitorMetricsPage />} />
  <Route path="logs" element={<MonitorLogsPage />} />
  <Route path="alerts" element={<MonitorAlertsPage />} />
  <Route path="settings" element={<LogLevelSettingsPage />} />
</Route>
```

`MonitorLayout` 是可选的中间布局组件（如需共用 PageHeader），也可直接在 AdminLayout 的 `<Outlet />` 下挂载。

## 文件结构

```
frontend/src/pages/monitor/
├── MonitorOverviewPage.tsx       # 概览
├── MonitorMetricsPage.tsx        # API 指标
├── MonitorLogsPage.tsx           # 日志检索
├── MonitorAlertsPage.tsx         # 告警面板
├── LogLevelSettingsPage.tsx      # 日志级别 (SUPER_ADMIN)

backend/zhiyu-admin/src/main/java/com/zhiyu/admin/
├── controller/AdminMonitorController.java  # 新增
├── service/AdminMonitorService.java         # 新增
└── dto/                                     # 新增 Monitor DTOs
    ├── HealthDto.java
    ├── MetricsDto.java
    ├── AlertDto.java
    └── LoggerDto.java
```

## 测试策略

### 后端
- `AdminMonitorServiceTest`: health 聚合测试, metrics 查询代理测试, logger 级别修改测试, 自动回滚调度测试
- `AdminStatsServiceTest`: 扩展 overview/trend 端点测试

### 前端
- `MonitorOverviewPage.test.tsx`: 健康卡片渲染, 统计卡片, 状态 badge
- `MonitorMetricsPage.test.tsx`: 时间范围切换, 图表渲染
- `MonitorLogsPage.test.tsx`: 4 个 Tab 切换, 筛选器按 Tab 变化, traceId 点击过滤
- `MonitorAlertsPage.test.tsx`: 告警摘要, 告警列表, 级别 badge
- `LogLevelSettingsPage.test.tsx`: logger 列表, 级别修改, 历史记录
- `DashboardPage.test.tsx`: 扩展测试新增 StatCard + 趋势图 + RecentAlerts

## 暂不涉及

- Grafana iframe 嵌入 — OPS.md 中 Grafana 独立访问
- Prometheus AlertManager 配置 — 基础设施层面，不在后端代码范围
- mysqld_exporter / nacos Prometheus 端点配置 — 已在 OPS.md 定义
- 告警规则创建/修改 — 通过 Prometheus AlertManager 配置文件管理
