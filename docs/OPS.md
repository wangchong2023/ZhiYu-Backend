# ZhiYu-Backend 运维手册

> 本文档定义生产环境的 SLO/SLI、监控告警、灾备方案、发布回滚流程和应急预案。在项目上线前根据实际部署环境更新。

## 1. SLO / SLI 定义

### 1.1 服务等级目标

| 指标 | SLO | SLI | 测量窗口 |
|------|-----|-----|---------|
| 可用性 | 99.9% | `成功请求数 / 总请求数` (排除 4xx) | 30 天 |
| API P50 延迟 | < 100ms | `histogram_quantile(0.5, http_request_duration_seconds)` | 滚动 5 分钟 |
| API P99 延迟 | < 500ms | `histogram_quantile(0.99, http_request_duration_seconds)` | 滚动 5 分钟 |
| 登录 P99 延迟 | < 1000ms | 同上，按 `/auth/login` 过滤 | 滚动 5 分钟 |
| 错误率 | < 0.1% | `5xx 请求数 / 总请求数` | 30 天 |
| 支付回调成功率 | > 99.9% | `支付成功数 / 回调总数` | 滚动 24 小时 |

### 1.2 错误预算

| 窗口 | 允许的不可用时间 | 允许的 5xx 错误数 |
|------|:-------------:|:------------:|
| 月度 | 43.2 分钟 | 0.1% of total |
| 季度 | 2.16 小时 | — |
| 年度 | 8.76 小时 | — |

> 错误预算耗尽时冻结所有非紧急发布，直到下个窗口恢复。

---

## 2. 监控面板

### 2.1 Grafana 大盘布局

```
┌─────────────────────────────────────────────────────┐
│  Row 1: 服务健康                                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐ │
│  │Instance   │ │DB Health │ │Redis     │ │Nacos    │ │
│  │UP/DOWN    │ │UP/DOWN   │ │UP/DOWN   │ │UP/DOWN  │ │
│  └──────────┘ └──────────┘ └──────────┘ └─────────┘ │
├─────────────────────────────────────────────────────┤
│  Row 2: 核心指标                                      │
│  ┌──────────────────┐ ┌──────────────────────────┐  │
│  │ QPS (今日)        │ │ P50/P95/P99 延迟 (1h)     │  │
│  └──────────────────┘ └──────────────────────────┘  │
├─────────────────────────────────────────────────────┤
│  Row 3: 业务指标                                      │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌──────────────┐ │
│  │今日注册 │ │今日登录 │ │今日下单 │ │今日支付成功    │ │
│  │  1,234  │ │ 45,678  │ │   890   │ │    876       │ │
│  └────────┘ └────────┘ └────────┘ └──────────────┘ │
├─────────────────────────────────────────────────────┤
│  Row 4: 错误与限流                                    │
│  ┌──────────────────┐ ┌──────────────────────────┐  │
│  │ 5xx 错误率 (1h)   │ │ Sentinel 限流触发次数      │  │
│  └──────────────────┘ └──────────────────────────┘  │
├─────────────────────────────────────────────────────┤
│  Row 5: JVM                                          │
│  ┌──────────────────┐ ┌──────────────────────────┐  │
│  │ Heap 使用率       │ │ GC 暂停时间                │  │
│  └──────────────────┘ └──────────────────────────┘  │
│  Row 6: 连接池                                        │
│  ┌──────────────────┐ ┌──────────────────────────┐  │
│  │ HikariCP 活跃连接 │ │ Lettuce Redis 连接状态     │  │
│  └──────────────────┘ └──────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### 2.2 关键指标说明

| 面板 | 数据源 | 查询 |
|------|--------|------|
| QPS | Prometheus | `rate(http_server_requests_seconds_count[1m])` |
| 延迟 | Prometheus | `histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))` |
| 注册数 | Prometheus | `increase(user_registered_total[1h])` |
| 5xx 率 | Prometheus | `rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m])` |
| Heap | Prometheus | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}` |
| HikariCP | Prometheus | `hikaricp_connections_active` |

---

## 3. 告警规则

### 3.1 告警定义

| 告警名 | 级别 | 条件 | 持续 | 通知渠道 | 处理 |
|--------|:----:|------|:----:|---------|------|
| `InstanceDown` | **P0** | `up{job="zhiyu-backend"} == 0` | 1m | 电话 + 钉钉 | 立即响应 |
| `HighErrorRate` | **P1** | `rate(5xx[5m]) > 0.01` | 5m | 钉钉 + 邮件 | 30 分钟内响应 |
| `HighP99Latency` | **P1** | `histogram_quantile(0.99, ...) > 2.0` | 5m | 钉钉 + 邮件 | 30 分钟内响应 |
| `DBConnectionPoolHigh` | **P2** | `hikaricp_connections_active / max > 0.8` | 10m | 钉钉 | 1 小时内处理 |
| `RedisDown` | **P0** | `redis_connected == 0` | 1m | 电话 + 钉钉 | 立即响应 |
| `PaymentFailureRateHigh` | **P0** | `rate(payment_failed_total[10m]) > 0.05` | 10m | 电话 + 钉钉 | 立即响应 |
| `ReconciliationMismatch` | **P1** | `reconciliation_mismatch_total > 10` | 触发即报 | 钉钉 + 邮件 | 当日处理 |
| `DiskSpaceLow` | **P2** | `disk_used_percent > 85` | 10m | 钉钉 | 4 小时内处理 |
| `ErrorBudgetBurnRate` | **P1** | 错误预算消耗速率 > 10x | 1h | 钉钉 + 邮件 | 冻结发布 |

### 3.2 告警升级路径

```
P2 告警 ──(1h未确认)──▶ 升级为 P1 ──(30m未处理)──▶ 升级为 P0
P1 告警 ──(30m未处理)──▶ 升级为 P0
P0 告警 ──────────────────────▶ 直接电话通知 on-call
```

---

## 4. 灾备方案 (DRP)

### 4.1 目标

| 指标 | 目标值 |
|------|:------:|
| RTO (Recovery Time Objective) | < 30 分钟 |
| RPO (Recovery Point Objective) | < 5 分钟（数据） |

### 4.2 MySQL 恢复

**备份策略：**
- 每日全量备份：凌晨 4:00 通过 `mysqldump` 或云厂商自动备份
- Binlog 增量备份：持续写入，保留 7 天
- 备份存储：OSS + 跨区域冗余

**恢复流程：**
```bash
# 1. 停止应用流量（切到维护模式）
# 2. 恢复最近全量备份
mysql -h <new-host> -u root < backup_20260518_040000.sql

# 3. 应用 binlog 增量到指定时间点
mysqlbinlog --stop-datetime="2026-05-18 10:00:00" binlog.000123 | mysql -h <new-host>

# 4. 验证数据一致性
# 5. 更新 K8s ConfigMap → 恢复流量
```

### 4.3 Redis 恢复

- Sentinel 自动故障转移（分钟级）
- 极端情况：从 RDB/AOF 快照重建
- 重建后需要预热（预加载热点数据）

### 4.4 Nacos 恢复

- Nacos 集群自动故障转移（3 节点，过半存活即可）
- 配置快照每 6 小时自动导出到 Git
- 极端恢复：从 Git 快照重新导入 Nacos

---

## 5. 发布流程

### 5.1 标准发布

```
1. GitHub Actions 触发构建
   ├── mvn clean verify (单元测试 + 集成测试 + JaCoCo)
   ├── 构建 Docker 镜像 (tag: <short-sha>)
   └── 推送到镜像仓库

2. 部署到 test 环境
   ├── deploy.sh dev deploy --dry-run（校验配置）
   ├── deploy.sh dev deploy（ConfigMap + Secret + Deployment + HPA/PDB/NetworkPolicy）
   ├── kubectl port-forward + curl E2E 冒烟测试
   └── 健康检查验证

3. 部署到 release (Argo Rollouts 金丝雀)
   ├── deploy.sh release deploy --dry-run（校验配置）
   ├── deploy.sh release deploy（ConfigMap + Secret + HPA/PDB/NetworkPolicy/SA）
   ├── kubectl argo rollouts set image（启动金丝雀 20% → AnalysisTemplate 自动分析）
   ├── 分析通过 → kubectl argo rollouts promote → 40%（继续分析）
   ├── 分析通过 → kubectl argo rollouts promote → 100%
   ├── 任意阶段分析失败 → kubectl argo rollouts abort（自动回滚）
   └── 监控 30 分钟 → 确认稳定
```

### 5.2 发布检查清单

- [ ] CI 全部通过（编译、测试、覆盖率、Checkstyle）
- [ ] test 环境 E2E 通过
- [ ] 数据库迁移脚本已 review
- [ ] 配置变更已记录
- [ ] 回滚方案已准备
- [ ] 若含支付代码变更 → 沙箱环境验证通过

---

## 6. 回滚流程

### 6.1 代码回滚

```bash
# 方式 1: Argo Rollouts 回滚到上一个版本（staging/release）
kubectl argo rollouts undo zhiyu-backend -n release

# 方式 2: 指定版本回滚
kubectl argo rollouts set image zhiyu-backend \
  zhiyu-backend=<registry>/zhiyu-backend:<previous-version> \
  -n release

# 方式 3: 金丝雀进行中异常时立即中止
kubectl argo rollouts abort zhiyu-backend -n release

# 方式 4: dev 环境 Deployment 回滚（无 Argo Rollouts）
kubectl rollout undo deployment/zhiyu-backend -n zhiyu-dev

# 验证回滚后 Pod 健康
kubectl get pods -n release -w
kubectl argo rollouts status zhiyu-backend -n release
```

### 6.2 数据库回滚

```bash
# Flyway 回滚（仅限有 undo 脚本的版本）
# ⚠️ 数据回滚是高危操作，需二次确认
flyway -url=<url> -user=<user> -password=<pw> -target=<previous-version> undo

# 若无 undo 脚本 → 从备份恢复（见 4.2）
```

### 6.3 配置回滚

管理后台 `/admin/config/history/{groupId}/{dataId}` → 选择历史版本 → 回滚。或直接通过 Nacos 控制台回滚。

---

## 7. 日志查询指南

### 7.1 Loki 常用查询

```logql
# 按 traceId 查询全链路
{job="zhiyu-backend"} |= "a1b2c3d4-e5f6-7890"

# 查询某时间段 ERROR
{job="zhiyu-backend"} | json | level = "ERROR" | line_format "{{.message}}"

# 慢请求 (耗时 > 1000ms)
{job="zhiyu-backend"} | json | duration_ms > 1000

# 支付回调错误
{job="zhiyu-backend"} |= "callback" |= "ERROR"

# 限流触发统计（按路径聚合）
sum by(path) (count_over_time({job="zhiyu-backend"} |= "限流触发"[1h]))
```

### 7.2 慢查询定位

```sql
-- MySQL 慢查询日志查询
SELECT
  start_time, query_time, lock_time, rows_examined, rows_sent,
  sql_text
FROM mysql.slow_log
WHERE start_time >= '2026-05-18 00:00:00'
  AND query_time > 0.500
ORDER BY query_time DESC
LIMIT 20;
```

---

## 8. 应急预案

### 8.1 数据库宕机

```
症状：Actuator /health 报告 DB DOWN，API 返回 50302
步骤：
1. 确认 MySQL 主库状态
2. 若主库宕机 → 触发故障转移（ProxySQL 或 K8s Service 自动切换）
3. 若自动切换失败 → 手动提升从库
   CHANGE MASTER TO MASTER_HOST='<new-master>';
4. 更新 K8s ConfigMap (DB_HOST) → 滚动重启 Pod
5. 检查数据一致性
6. 恢复后补充从库
```

### 8.2 Redis 不可用

```
症状：Lettuce 连接异常，部分功能降级
自动降级（代码内置）：
  - JWT 黑名单 → 内存 LRU 缓存
  - 配额计数 → 本地队列
  - 验证码 → 直接拒绝 (503)
  - 一般缓存 → 查数据库（受 Sentinel 限流保护）

恢复步骤：
1. Sentinel 自动故障转移
2. 若 Sentinel 也失败 → 手动重启 Redis + 重新加载 RDB
3. 应用自动重连（Lettuce 指数退避，最大 30s）
4. 批量同步本地队列中的配额计数
```

### 8.3 Nacos 不可达

```
症状：Nacos 健康检查失败
自动降级（代码内置）：
  - 使用本地快照缓存（last-known-good config）
  - 服务发现依赖已缓存的实例列表

恢复步骤：
1. 检查 Nacos 集群状态
2. 若单节点故障 → 无需操作，集群自动恢复
3. 若全集群故障 → 手动启动 Nacos 节点
4. 从 Git 快照重新导入配置（若数据丢失）
5. 应用自动重连
```

### 8.4 支付回调堆积

```
症状：订单长时间 PENDING，payment_record 缺失
排查：
1. 检查第三方支付平台回调日志
2. 在支付平台后台查订单状态
3. 若第三方已支付但回调未到 → 手动查单补单
4. 若第三方未支付 → 取消超时订单（30分钟）
预防：
- 定时任务每 5 分钟查一次 PENDING 超 15 分钟的订单
- 主动查询第三方支付状态作为仲裁
```

### 8.5 服务 OOM

```
症状：Pod 被 K8s OOMKilled，重启后恢复
排查：
1. kubectl describe pod <pod-name> → 查看 OOMKilled 详情
2. 拉取 heap dump（-XX:+HeapDumpOnOutOfMemoryError）
3. 分析内存泄漏（MAT / JProfiler）
4. 若短期无法修复 → 临时扩容 JVM heap 或增加 replicas
```

---

## 9. 定期运维任务

| 频率 | 任务 | 负责 |
|------|------|------|
| 每日 | 检查告警和错误率 | On-call |
| 每日 | 确认支付对账结果 | 财务/技术 |
| 每周 | 检查磁盘使用率 | On-call |
| 每月 | 审视错误预算 | 技术负责人 |
| 每月 | 数据库备份验证（还原到测试环境） | DBA |
| 每季度 | 灾备演练（模拟主库宕机） | 全团队 |
| 每季度 | 安全扫描和依赖更新 | 安全 |

---

## 10. 容量规划

### 10.1 预估模型

| 阶段 | 时间 | MAU | DAU | 峰值 QPS | 存储 (DB) | 存储 (OSS) |
|------|------|----:|----:|:--------:|----------|----------|
| MVP | 上线 0-3 月 | 5,000 | 500 | 50 | 10 GB | 50 GB |
| 增长期 | 3-12 月 | 50,000 | 5,000 | 500 | 100 GB | 500 GB |
| 规模化 | 12-24 月 | 200,000 | 20,000 | 2,000 | 500 GB | 2 TB |

### 10.2 资源配置建议

| 阶段 | K8s Pods | CPU/Pod | Memory/Pod | MySQL 规格 | Redis 规格 |
|------|:-------:|:-------:|:----------:|-----------|-----------|
| MVP | 2 | 1 core | 2 Gi | 2C4G (单机) | 2G (Sentinel) |
| 增长期 | 4 | 2 core | 4 Gi | 4C8G (主从) | 8G (Sentinel 3 节点) |
| 规模化 | 8+ | 4 core | 8 Gi | 8C16G (读写分离) | 16G (Cluster) |

### 10.3 扩容触发条件

| 指标 | 阈值 | 动作 |
|------|:----:|------|
| CPU 使用率 | > 70% (5min) | 增加 1 个 Pod |
| Memory 使用率 | > 80% (5min) | 增加 1 个 Pod |
| P99 延迟 | > 800ms (5min) | 增加 2 个 Pod + 排查 |
| DB 连接数 | > 70% 池 | 增加 max pool size 或读写分离 |
| 磁盘使用率 | > 80% | 清理日志 / 扩容磁盘 |

### 10.4 大促预案 (如 618 / 双11)

- 提前 1 周扩容至 1.5x 预估峰值
- 支付回调处理增加消费者实例
- 暂时关闭非核心功能（审计日志采样写入）
- Sentinel 降级策略切换到「快速失败」模式
- 24h 值班 + 技术支持待命

---

## 11. 接口限流阈值

### 11.1 默认限流规则

| 接口分组 | QPS 阈值 (单实例) | 突发容忍 | 降级策略 |
|---------|:----------------:|:------:|---------|
| `/auth/login`, `/auth/register` | 20 | 30/s 持续 2s | 429 + "请稍后重试" |
| `/auth/send-code` | 5 | 10/s 持续 1s | 429 + "验证码发送过于频繁" |
| `/auth/refresh` | 50 | 80/s 持续 2s | 429 + 静默重试 |
| `/user/**` | 50 | 80/s 持续 2s | 429 + 通用错误 |
| `/subscription/**` | 30 | 50/s 持续 2s | 429 + 通用错误 |
| `/admin/**` | 20 | 30/s 持续 2s | 429 + 通用错误 |
| 支付回调 (`/subscription/callback/**`) | 100 | 200/s 持续 5s | 排队 + 重试 |
| 静态资源 (`/public/**`) | 200 | 500/s 持续 5s | CDN 兜底 |

### 11.2 用户级限流

| 场景 | 规则 | 时间窗口 |
|------|------|:------:|
| 登录失败 (同 IP) | 20 次 | 1 分钟 |
| 发送验证码 (同手机/邮箱) | 1 次 | 60 秒 |
| 发送验证码 (同手机/邮箱) | 5 次 | 1 小时 |
| 发送验证码 (同 IP) | 50 次 | 1 小时 |
| 密码重置 | 3 次 | 1 小时 |
| 导出数据 | 1 次 | 10 分钟 |

### 11.3 Sentinel 配置示例

```json
// Nacos: zhiyu-backback-release-sentinel.json
{
  "rules": [
    {
      "resource": "POST:/auth/login",
      "grade": "QPS",
      "count": 20,
      "limitApp": "default",
      "controlBehavior": "WARM_UP",
      "warmUpPeriodSec": 5
    },
    {
      "resource": "POST:/auth/send-code",
      "grade": "QPS",
      "count": 5,
      "limitApp": "default",
      "controlBehavior": "REJECT",
      "maxQueueingTimeMs": 0
    }
  ]
}
```
