# ZhiYu 基础设施设计规格

> 本文档定义 Nacos 配置中心、Redis 缓存和 MySQL 数据库的部署架构、命名规范和设计决策。K8s 部署清单见 [CI-CD.md](CI-CD.md#5-k8s-部署清单)，备份恢复见 [OPS.md](OPS.md#4-灾备方案-drp)。

---

## 1. Nacos 配置中心

### 1.1 Namespace 设计

| Namespace | 用途 | 环境隔离 | 备注 |
|-----------|------|:------:|------|
| `public` | 保留（Nacos 默认） | — | 不使用，仅占位 |
| `dev` | 本地开发 | ✅ | 单机 Nacos，Derby 内置数据库 |
| `test` | 集成测试环境 | ✅ | 3 节点，外部 MySQL |
| `staging` | 预发布环境 | ✅ | 3 节点，外部 MySQL |
| `release` | 生产环境 | ✅ | 3 节点，外部 MySQL |

**原则：每个环境使用独立的 Namespace，共享配置在每个 Namespace 中各自维护一份**（不使用 Nacos 的跨 namespace 引用，避免环境耦合）。

### 1.2 Group 设计

```
Namespace: release
├── Group: DEFAULT_GROUP
│   ├── zhiyu-backend.yml               # 应用主配置（非敏感）
│   ├── sentinel-gateway-rules.json     # Sentinel Gateway 规则
│   ├── sentinel-app-rules.json         # Sentinel 应用层规则
│   └── sentinel-degrade-rules.json     # Sentinel 降级规则
├── Group: SUBSCRIPTION
│   ├── subscription-plans.yml          # 套餐定义
│   └── payment-channels.yml            # 支付渠道开关
├── Group: NOTIFICATION
│   ├── notification-templates.yml      # 通知模板
│   └── notification-preferences.yml    # 默认通知偏好
├── Group: SECURITY
│   ├── security-policy.yml             # 安全策略（锁定、密码策略）
│   └── captcha-config.yml              # CAPTCHA 配置
├── Group: RATE_LIMIT
│   └── rate-limit-thresholds.yml       # 限流阈值
└── Group: BIZ_CONFIG
    ├── feature-flags.yml               # 功能开关
    ├── quota-definitions.yml           # 配额定义
    └── trial-config.yml                # 试用规则
```

**Group 命名规范：** 大写蛇形（`UPPER_SNAKE_CASE`），用于逻辑分组，不区分环境。

### 1.3 Data ID 命名规范

```
格式: <service-name>[.<module>].<format>

示例:
  zhiyu-backend.yml                    # 应用主配置
  sentinel-gateway-rules.json          # 基础设施规则
  subscription-plans.yml               # 业务配置（跨模块共享）
  notification-templates.yml           # 业务配置
  feature-flags.yml                    # 功能开关
```

**规则：**
- 应用自身配置：`<service-name>.yml`（如 `zhiyu-backend.yml`）
- 基础设施规则：`<component>-<rule-type>.json`（如 `sentinel-gateway-rules.json`）
- 业务共享配置：`<domain>-<resource>.yml`（如 `subscription-plans.yml`）
- 文件格式：结构化规则用 `.json`，业务配置用 `.yml`，模板用 `.yml`

### 1.4 配置项清单

#### 1.4.1 zhiyu-backend.yml（应用主配置）

```yaml
# Nacos Data ID: zhiyu-backend.yml
# Group: DEFAULT_GROUP

spring:
  # 数据源配置（连接地址/用户名/密码等敏感信息走 K8s Secret 环境变量覆盖）
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000

  # Redis 配置（地址/密码在环境变量中）
  redis:
    lettuce:
      pool:
        max-active: 16
        max-idle: 8
        min-idle: 4
        max-wait: 2000ms
    timeout: 2000ms

zhiyu:
  server:
    graceful-shutdown-timeout: 30s

  # JWT 配置
  auth:
    access-token-expire: 15m
    refresh-token-expire: 7d
    action-token-expire: 5m
    bcrypt-cost-factor: 12
    max-devices-per-user: 5

  # 安全策略
  security:
    login:
      max-attempts: 5
      lock-duration: 15m
      window-duration: 5m

  # Flyway
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
```

#### 1.4.2 subscription-plans.yml（套餐定义）

```yaml
# Nacos Data ID: subscription-plans.yml
# Group: SUBSCRIPTION

subscription:
  plans:
    free:
      name: "免费游客"
      price_monthly: 0
      price_yearly: 0
      trial_days: 0
      features:
        - basic_chat
        - text_search
      quotas:
        daily_chat: 10
        file_upload_mb: 5

    lite:
      name: "Lite"
      price_monthly: 2900
      price_yearly: 29000
      trial_days: 7
      features:
        - basic_chat
        - text_search
        - file_upload
        - image_gen
      quotas:
        daily_chat: 200
        file_upload_mb: 50
        image_gen_daily: 20

    pro:
      name: "Pro"
      price_monthly: 9900
      price_yearly: 99000
      trial_days: 0
      features:
        - basic_chat
        - text_search
        - file_upload
        - image_gen
        - priority_queue
      quotas:
        daily_chat: -1
        file_upload_mb: 500
        image_gen_daily: 200
        priority_queue: true
```

#### 1.4.3 feature-flags.yml（功能开关）

```yaml
# Nacos Data ID: feature-flags.yml
# Group: BIZ_CONFIG

features:
  wechat-login: true
  qq-login: true
  google-login: true
  apple-login: true
  sms-login: true
  webauthn: false         # 初期关闭，迭代 3 后启用
  totp: true
  data-export: true
  gift-code: false        # 未来功能
  referral: false         # 未来功能

payment:
  wechat: true
  alipay: true
  apple-iap: true
  google-play: true
```

#### 1.4.4 环境变量（K8s Secret / application.yml）

以下配置**不在 Nacos 中**，仅通过环境变量或 K8s Secret 注入：

| 配置项 | 环境变量 | 说明 |
|--------|---------|------|
| DB_HOST | `DB_HOST` | 数据库地址 |
| DB_PASSWORD | `DB_PASSWORD` | 数据库密码 |
| REDIS_HOST | `REDIS_HOST` | Redis 地址 |
| REDIS_PASSWORD | `REDIS_PASSWORD` | Redis 密码 |
| JWT_PRIVATE_KEY | `JWT_PRIVATE_KEY` | JWT RS256 私钥（PEM base64） |
| JWT_PUBLIC_KEY | `JWT_PUBLIC_KEY` | JWT 公钥（允许 Nacos 存储） |
| WECHAT_APP_SECRET | `WECHAT_APP_SECRET` | 微信 AppSecret |
| ALIPAY_PRIVATE_KEY | `ALIPAY_PRIVATE_KEY` | 支付宝应用私钥 |
| ALIYUN_ACCESS_KEY | `ALIYUN_ACCESS_KEY` | 阿里云 AK |
| ALIYUN_SECRET_KEY | `ALIYUN_SECRET_KEY` | 阿里云 SK |
| APPLE_SHARED_SECRET | `APPLE_SHARED_SECRET` | Apple IAP Shared Secret |

### 1.5 Spring 客户端配置

```yaml
# application.yml — Nacos 客户端连接
spring:
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:dev}
        group: DEFAULT_GROUP
        file-extension: yml
        refresh-enabled: true
        # 多 Data ID 配置
        extension-configs:
          - group: DEFAULT_GROUP
            data-id: sentinel-gateway-rules.json
            refresh: true
          - group: SUBSCRIPTION
            data-id: subscription-plans.yml
            refresh: true
          - group: NOTIFICATION
            data-id: notification-templates.yml
            refresh: true
          - group: SECURITY
            data-id: security-policy.yml
            refresh: true
          - group: RATE_LIMIT
            data-id: rate-limit-thresholds.yml
            refresh: true
          - group: BIZ_CONFIG
            data-id: feature-flags.yml
            refresh: true
      # 服务发现
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:dev}
        group: DEFAULT_GROUP
```

### 1.6 配置变更流程

```
开发者提出变更
  │
  ├── 非敏感配置（套餐/开关/阈值）:
  │     1. 在 test 环境 Nacos 控制台修改
  │     2. 验证行为正确
  │     3. 提交 PR: 更新本仓库 docs/config/ 中的配置参考文件
  │     4. 合并后，管理员在 release Nacos 中执行相同修改
  │     5. 应用监听 Nacos RefreshScope，实时生效
  │
  └── 敏感配置（密钥/密码）:
       1. 生成新密钥 → 更新 K8s Secret
       2. 滚动重启 Pod
       3. 旧密钥保留 24h 后删除
```

**审计：** 所有 Nacos 配置变更通过 `audit_log` 记录（event_type=`CONFIG_CHANGE`），含操作人、时间、配置项、旧值→新值。

---

## 2. Redis 设计

### 2.1 部署模式

| 环境 | 模式 | 节点数 | 持久化 | 说明 |
|------|------|:-----:|:------:|------|
| `kubeadm` | StatefulSet | 1 | ✅ AOF | 本机开发，K8s 单实例 + PVC 持久化 |
| `dev` | Standalone | 1 | ❌ | ACK 单实例 |
| `test` | Standalone | 1 | AOF only | Testcontainers |
| `staging` | Sentinel | 3 | AOF | 模拟生产环境 |
| `release` | Sentinel | 3 | AOF + RDB | 自动故障转移 |

**Sentinel 配置（release）：**
```
Sentinel 节点: 3 (quorum = 2)
Master: redis-master-0  (1 主 2 从)
Sentinel 端口: 26379
监控: down-after-milliseconds=5000, failover-timeout=30000
```

### 2.2 Key 命名规范

```
格式: <entity>:<sub>:<identifier>

示例:
  verify:email:user@example.com            # 邮箱验证码
  verify:sms:13812345678                   # 短信验证码
  refresh:1001:device-uuid                 # refresh token
  login:fail:password:admin                # 登录失败计数 (按标识)
  login:fail:ip:192.168.1.1               # 登录失败计数 (按 IP)
  jwt:blacklist:jti-abc123                 # JWT 黑名单
  user:sessions:1001                       # 用户会话列表 (Hash, field=deviceId)
  quota:1001:daily_chat:2026-05-18         # 配额计数
  pay:processed:420000123420230101         # 支付幂等
  ratelimit:sms:13812345678:min            # 短信频率
  ratelimit:email:user@example.com:min     # 邮件频率
  captcha:state:a3b4c5                     # CAPTCHA 校验
  action:xy12zw34                          # 敏感操作 token
  reset:pwd:abc-def-123                    # 密码重置 token
  config:feature-flags                     # 功能开关缓存 (Hash)
  cache:subscription:plans                 # 套餐定义缓存 (Hash)
  cache:user:1001                          # 用户信息缓存 (Hash)
  stat:leaderboard:daily_chat:2026-05-18   # 排行榜 (Sorted Set)
```

**规则：**
- 分隔符使用冒号 `:`（Redis 社区约定，方便 `SCAN` 按模式匹配）
- 第一部分为领域：`auth`, `user`, `sub`, `rate`, `config`, `cache`
- 最后一部分为业务唯一标识
- 不超过 5 层嵌套
- **禁止**使用 `key` 作为 key 名的一部分（无意义）

### 2.3 数据结构选择

| 场景 | 数据结构 | Key 示例 | 选型理由 |
|------|:-------:|---------|---------|
| 验证码/临时 Token | **String** + TTL | `verify:email:<addr>` | 简单 K-V，TTL 自动过期 |
| 登录失败计数 | **Sorted Set** | `login:fail:password:<uid>` | 滑动窗口，score = 时间戳 |
| 用户会话列表 | **Hash** | `user:sessions:<uid>` | 字段为 device_id，值为 token JSON |
| 配额计数器 | **String** + TTL | `quota:<uid>:<type>:<dt>` | 原子 INCR，日初过期重建 |
| 支付幂等 | **String** | `pay:processed:<txn_id>` | EXISTS 判断，TTL=30d |
| 套餐定义缓存 | **Hash** | `cache:subscription:plans` | 字段为 planKey，值 JSON |
| 用户信息缓存 | **Hash** | `cache:user:<uid>` | 字段为属性名，可部分更新 |
| 功能开关 | **Hash** | `config:feature-flags` | 字段为 flagName，值 boolean |
| Token 黑名单 | **String** + TTL | `jwt:blacklist:<jti>` | 仅需知道是否存在，TTL 对齐过期 |
| 排行榜/列表 | **Sorted Set** | `stat:leaderboard:daily_chat:<dt>` | 分数排序，ZRANK 查询 |

**反模式（禁止）：**
- ❌ 用 `KEYS *` 扫描（阻塞）→ 使用 `SCAN` 游标
- ❌ 用 String 存 JSON 后频繁读取其中某个字段 → 使用 Hash
- ❌ 用 List 做去重集合 → 使用 Set
- ❌ 大 Key（单个 Key 的 value > 10MB）→ 拆分为多个 Key

### 2.4 TTL 策略

| 数据类型 | 默认 TTL | 说明 |
|---------|:-------:|------|
| 邮箱验证码 | 5 min | 过期后自动失效 |
| 短信验证码 | 5 min | 同上 |
| refresh_token | 7 days | 用户 7 天不活跃需重新登录 |
| JWT 黑名单 (jti) | 15 min | 对齐 access_token 剩余有效期 |
| 支付幂等去重 | 30 days | 覆盖退款最长 15 天窗口（留足余量） |
| 登录锁定 | 15 min | 临时锁定 |
| 配额计数 | 到当日 23:59:59 | 按自然日重置 |
| 用户信息缓存 | 30 min | 避免用户修改信息后长期不一致 |
| 套餐定义缓存 | 60 min | 套餐变更频率极低 |
| 功能开关缓存 | 5 min | 允许快速切换 |

**防缓存雪崩（TTL 抖动）：**
```java
// 为 TTL 添加随机抖动，避免大量 key 同时过期
int ttl = baseTtl + ThreadLocalRandom.current().nextInt((int) (baseTtl * 0.1));
redisTemplate.expire(key, Duration.ofSeconds(ttl));
```

### 2.5 内存管理

```yaml
# Redis 内存配置（release）
maxmemory: 4gb
maxmemory-policy: volatile-lru  # 仅淘汰设有 TTL 的 key
```

**内存估算（release，10 万用户规模）：**

| 数据类型 | 单条大小 | 条数 | 总内存 |
|---------|:------:|----:|:-----:|
| refresh_token | ~200B | 50,000 | ~10 MB |
| JWT 黑名单 | ~150B | 2,000 | ~0.3 MB |
| 验证码 | ~300B | 500 | ~0.15 MB |
| 用户信息缓存 | ~2 KB | 50,000 | ~100 MB |
| 套餐定义缓存 | ~3 KB | 3 | negligible |
| 配额计数 | ~100B | 50,000 | ~5 MB |
| 支付幂等 | ~200B | 5,000 | ~1 MB |
| **合计** | | | **~120 MB** |

> 4 GB 上限保留 ~3.5 GB buffer 应对峰值，远超基础需求。

**内存监控告警：**
| 条件 | 级别 | 说明 |
|------|:----:|------|
| `used_memory > maxmemory * 0.8` | P2 | 达到 80%，考虑扩容 |
| `evicted_keys increase > 100 / 10min` | P2 | 大量 key 淘汰，检查是否有异常 |
| `hit_rate < 0.8 over 10min` | P2 | 命中率过低，检查缓存策略 |

### 2.6 持久化策略

| 环境 | RDB | AOF | 说明 |
|------|:---:|:---:|------|
| `dev` | ❌ | ❌ | 数据可丢失 |
| `test` | ❌ | `appendfsync everysec` | 测试不关注恢复速度 |
| `staging` | `save 900 1` | `appendfsync everysec` | 模拟生产 |
| `release` | `save 900 1` + `save 300 10` | `appendfsync everysec` | 兼顾性能与持久性 |

```
# release Redis 持久化配置
save 900 1          # 15 分钟内至少 1 次写 → RDB 快照
save 300 10         # 5 分钟内至少 10 次写 → RDB 快照
save 60 10000       # 1 分钟内至少 10000 次写 → RDB 快照（密集写入保护）
appendonly yes
appendfsync everysec  # 每秒 sync 一次 AOF（最多丢 1s 数据）
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb
```

### 2.7 缓存设计模式

#### Cache-Aside（旁路缓存）— 默认模式

```
读操作:
  Client → Redis GET → 命中? 
    → YES: 返回
    → NO: 查 DB → 写入 Redis (带 TTL) → 返回

写操作:
  Client → 更新 DB → DELETE Redis key (或 SET 新值)
```

**实现模板：**
```java
// Cache-Aside 标准实现
public UserProfile getUserProfile(Long userId) {
    String cacheKey = "cache:user:" + userId;
    UserProfile cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) {
        return cached;
    }
    UserProfile profile = userMapper.selectById(userId).toProfile();
    if (profile != null) {
        redisTemplate.opsForValue().set(cacheKey, profile, 
            Duration.ofMinutes(30 + ThreadLocalRandom.current().nextInt(3)));
    }
    return profile;
}

public void updateUserProfile(Long userId, UpdateProfileReq req) {
    userMapper.updateById(req.toEntity(userId));
    redisTemplate.delete("cache:user:" + userId);  // 失效而非更新
}
```

#### 写入穿透防护（Bloom Filter）

对于第三方登录的 openid 查询（不存在用户占大多数新用户），用 Bloom Filter 加速「查不到」的判断：

```java
// Bloom Filter 预加载所有已绑定 openid
@PostConstruct
public void loadOpenIdBloomFilter() {
    BloomFilter<String> filter = redisson.getBloomFilter("bloom:openid:wechat");
    filter.tryInit(1000000, 0.01);  // 100 万容量，1% 误判率
    List<String> allOpenIds = authIdentityMapper.selectAllOpenIds("WECHAT");
    allOpenIds.forEach(filter::add);
}

// 使用
public boolean mightExist(String openId) {
    BloomFilter<String> filter = redisson.getBloomFilter("bloom:openid:wechat");
    return filter.contains(openId);
}
```

### 2.8 连接配置（Lettuce）

```yaml
spring:
  redis:
    # Sentinel 模式
    sentinel:
      master: redis-master
      nodes:
        - redis-sentinel-0.redis-sentinel:26379
        - redis-sentinel-1.redis-sentinel:26379
        - redis-sentinel-2.redis-sentinel:26379
    lettuce:
      pool:
        max-active: 16
        max-idle: 8
        min-idle: 4
        max-wait: 2000ms
      cluster:
        refresh:
          adaptive: true
          period: 30s
    timeout: 2000ms
    connect-timeout: 2000ms
```

**重连策略：** Lettuce 自动重连（指数退避），最大 30s。

```java
// Lettuce 客户端配置
@Bean
public LettuceClientConfiguration lettuceClientConfiguration() {
    return LettuceClientConfiguration.builder()
        .commandTimeout(Duration.ofSeconds(2))
        .clientOptions(ClientOptions.builder()
            .autoReconnect(true)
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
            .build())
        .build();
}
```

### 2.9 会话存储设计

```java
// TokenService: refresh_token 存储
public void storeRefreshToken(Long userId, String deviceId, String refreshToken) {
    String key = "refresh:" + userId + ":" + deviceId;
    redisTemplate.opsForValue().set(key, refreshToken, Duration.ofDays(7));
    // 同时写入 Hash，便于查询该用户所有活跃设备
    redisTemplate.opsForHash().put(
        "user:sessions:" + userId, deviceId, refreshToken);
}

// 查询该用户所有活跃会话
public Map<String, String> getUserSessions(Long userId) {
    return redisTemplate.<String, String>opsForHash()
        .entries("user:sessions:" + userId);
}

// 踢出设备 → 删除特定 session
public void removeSession(Long userId, String deviceId) {
    redisTemplate.delete("refresh:" + userId + ":" + deviceId);
    redisTemplate.opsForHash().delete("user:sessions:" + userId, deviceId);
}
```

---

## 3. MySQL 补充设计

MySQL 的 DDL、索引、主从拓扑已在 [DATABASE.md](DATABASE.md) 和 [ARCHITECTURE.md §5](ARCHITECTURE.md#5-部署架构) 中定义，本节仅补充连接池配置。

### 3.1 HikariCP 连接池

```yaml
spring:
  datasource:
    hikari:
      pool-name: ZhiYuPool
      # 连接池大小公式: pool_size = Tn * (Cm − 1) + 1
      # Tn = 最大线程数 (Tomcat default: 200)
      # Cm = 单线程最大持有连接数 (通常 1)
      # pool_size ≈ 200 * (1 - 1) + 1 = 1? 不，需要按实际 QPS 计算
      maximum-pool-size: 20    # per Pod，4 Pod × 20 = 80 总连接
      minimum-idle: 5
      connection-timeout: 3000  # 获取连接最大等待时间
      idle-timeout: 600000      # 10 分钟空闲回收
      max-lifetime: 1800000     # 30 分钟最大生命周期（低于 MySQL wait_timeout 8h）
      leak-detection-threshold: 60000  # 1 分钟未归还视为泄漏

      # 连接校验
      connection-test-query: SELECT 1
      validation-timeout: 3000

      # 预初始化（避免首请求冷启动延迟）
      initialization-fail-timeout: 1
```

**连接数计算：**
```
最大 DB 连接数 (RDS max_connections=400):
  - 应用 Pods: 4 × 20(hikari max) = 80
  - Nacos: 3 × 5 = 15
  - Flyway 迁移: ~5 (临时)
  - DB 管理/备份: ~10
  ─────────────────────────
  总计: ~110 / 400 (安全余量充足)
```

### 3.2 读写分离（预留）

当前项目规模下暂不做读写分离，日后扩展：

```yaml
# 未来: 使用 Spring AbstractRoutingDataSource
# 写操作 → 主库
# 读操作（非实时性） → 从库
zhiyu:
  datasource:
    read-write-split: false  # 当前关闭，QPS > 500 后评估开启
```

---

> **引用索引**：
> - [DATABASE.md](DATABASE.md) — 完整 DDL + 索引
> - [CI-CD.md §5](CI-CD.md#5-k8s-部署清单) — K8s 部署清单
> - [RATE-LIMITING.md §3](RATE-LIMITING.md#3-redis-key-设计) — 限流相关 Redis Key
> - [DEVELOPMENT-STANDARDS.md §2](DEVELOPMENT-STANDARDS.md#2-maven-依赖与版本管理) — Maven 依赖版本
> - [OPS.md §4](OPS.md#4-灾备方案-drp) — 灾备恢复

---

## 4. Monitoring 监控栈

Prometheus + Grafana 部署在 `monitoring` 命名空间中，提供集群和应用的指标采集、存储和可视化。

### 4.1 部署组件

| 组件 | 工作负载 | 端口 (ClusterIP) | NodePort | 用途 |
|------|---------|-----------------|----------|------|
| Prometheus | StatefulSet (1 副本) | 9090 | 30909 | 指标抓取 + 15d 本地存储 |
| Grafana | Deployment (1 副本) | 3000 | 30000 | 可视化看板 |
| kube-state-metrics | Deployment (1 副本) | 8080/8081 | — | K8s 对象状态指标 |
| node-exporter | DaemonSet | 9100 | — | 宿主机 CPU/内存/磁盘 |
| metrics-server | Deployment (1 副本) | 443 | — | CPU/内存聚合指标（HPA + kubectl top） |

### 4.2 部署命令

```bash
# 在线部署
./deploy/deploy.sh kubeadm monitoring

# 离线部署（一键打包）
./deploy/scripts/offline-pack.sh kubeadm

# 传输离线包并一键部署（包含镜像导入与服务部署）
scp artifact/zhiyu-backend-artifact-v*.tar.gz root@<node>:/tmp/
ssh root@<node> "cd /tmp && tar -xzf zhiyu-backend-artifact-v*.tar.gz && cd zhiyu-backend-artifact-v* && sudo ./offline-deploy.sh"
```

### 4.3 访问方式

| 服务 | 地址 | 凭据 |
|------|------|------|
| Grafana | `http://<node-ip>:30000` | `admin` / `$GRAFANA_PASSWORD` |
| Prometheus | `http://<node-ip>:30909` | 无 |

### 4.4 抓取目标

Prometheus 通过 K8s Service Discovery 自动发现以下目标：

- **zhiyu-backend** — Pod 注解 `prometheus.io/scrape: "true"` 自动发现
- **kubernetes-nodes** — kubelet `/metrics` 端点
- **kubernetes-cadvisor** — 容器 CPU/内存/网络指标
- **kube-state-metrics** — Deployment/StatefulSet/Pod 状态
- **node-exporter** — 宿主机指标
- **metrics-server** — 资源指标 API（供 HPA 自动伸缩与 kubectl top 使用）

### 4.5 镜像离线导入

```bash
# 一键打包（含应用 + 全部依赖镜像）
./deploy/scripts/offline-pack.sh kubeadm

# 传输离线包并一键部署
scp artifact/zhiyu-backend-artifact-v*.tar.gz root@<node>:/tmp/
ssh root@<node> "cd /tmp && tar -xzf zhiyu-backend-artifact-v*.tar.gz && cd zhiyu-backend-artifact-v* && sudo ./offline-deploy.sh"
```

镜像版本统一定义在 `deploy/envs/<env>.env` 中，可通过环境变量覆盖。

---

## 5. 默认凭据

开发环境（kubeadm）的凭据统一定义在 `deploy/envs/<env>.env` 中，部署时通过 `envsubst` 注入 K8s Secret。

### 5.1 凭据一览

| 组件 | 用户名 | 密码来源 | 存储位置 |
|------|--------|---------|---------|
| MySQL | `zhiyu` | `MYSQL_PASSWORD` | `mysql-secret` Secret |
| MySQL (root) | `root` | `MYSQL_ROOT_PASSWORD` | `mysql-secret` Secret |
| Redis | — | `REDIS_PASSWORD` | `redis-secret` Secret |
| Nacos | `nacos` | `NACOS_PASSWORD` | `nacos-secret` Secret |
| Grafana | `admin` | `GRAFANA_PASSWORD` | `grafana-secret` Secret |

### 5.2 查看当前凭据

```bash
# MySQL
kubectl get secret mysql-secret -n zhiyu-dev -o jsonpath='{.data.password}' | base64 -d

# Redis
kubectl get secret redis-secret -n zhiyu-dev -o jsonpath='{.data.password}' | base64 -d

# Nacos
kubectl get secret nacos-secret -n zhiyu-dev -o jsonpath='{.data.password}' | base64 -d

# Grafana
kubectl get secret grafana-secret -n monitoring -o jsonpath='{.data.admin-password}' | base64 -d
```

### 5.3 安全说明

- **开发环境** (kubeadm): 密码为自动生成的强随机密码，存储在 `deploy/envs/*.env` 并提交到 Git。仅用于本地开发和测试。
- **预发布/生产环境** (staging / release): 使用外部托管服务（阿里云 RDS / Redis Sentinel / Nacos 集群），凭据通过 CI/CD 环境变量注入，不在 env 文件中明文存储。
- **密码轮换**：修改 `deploy/envs/*.env` 后需在数据库内同步更新。MySQL 的 PVC 持久化会阻止自动密码变更——需通过 `ALTER USER` 手动同步后更新 K8s Secret 并重启 Pod。

---

## 6. 部署端口矩阵

### 6.1 K8s 集群服务 (kubeadm NodePort)

| 服务 | ClusterIP | NodePort | 地址 | 用户名 | 密码 |
|------|-----------|----------|------|--------|------|
| Nacos HTTP | 8848 | 30848 | `http://<node-ip>:30848/nacos` | `nacos` | `$NACOS_PASSWORD` |
| Nacos gRPC | 9848 | 31848 | —（内部通信） | — | — |
| Prometheus | 9090 | 30909 | `http://<node-ip>:30909` | 无 | — |
| Grafana | 3000 | 30000 | `http://<node-ip>:30000` | `admin` | `$GRAFANA_PASSWORD` |
| MySQL | 3306 | 30306 | `mysql -h <node-ip> -P 30306` | `zhiyu` | `$MYSQL_PASSWORD` |
| Redis | 6379 | 30679 | `redis-cli -h <node-ip> -p 30679` | — | `$REDIS_PASSWORD` |
| 业务 API | 8080 | — | `https://<node-ip>/api/v1`（Ingress 80/443） | JWT | — |

### 6.2 开发工具链服务 (Docker/宿主机)

| 服务 | 端口 | 地址 | 账号 / 说明 |
|------|------|------|-------------|
| Gitea | 3000 | `http://192.168.0.105:3000` | Git 仓库 + Woodpecker OAuth 认证 |
| Woodpecker CI | 8000 | `http://localhost:8000` | CI/CD 控制台，登录走 Gitea OAuth |
| Woodpecker gRPC | 9000 | `localhost:9000` | Agent ← Server 内部通信 |
| Nexus Maven | 8081 | `http://192.168.0.105:8081` | `admin` / `admin123` |

> Gitea 和 Nexus 使用宿主机 LAN IP (`192.168.0.105`)，同时兼容浏览器和 Docker 容器内访问。若 IP 变更需同步更新 `woodpecker/bin/docker-compose.yml` 和 `backend/.mvn/settings.xml`。

> K8s 服务凭据具体值见 `deploy/envs/kubeadm/passwords.env`，或运行 `./deploy/deploy.sh show-secrets` 查看。生产环境（staging/release）使用外部托管服务，仅暴露 ClusterIP，不可直连。
