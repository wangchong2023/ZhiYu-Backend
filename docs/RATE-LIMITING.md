# ZhiYu 接口限流设计规格

> 本文档定义接口限流的完整设计方案，包括分层架构、算法选型、Redis Key 设计、Sentinel 配置和降级策略。运行时阈值配置见 [OPS.md §11](OPS.md#11-接口限流阈值)。

## 1. 分层限流架构

```
                              ┌──────────────────────┐
                              │     Client Request    │
                              └──────────┬───────────┘
                                         │
                              ┌──────────┴───────────┐
                              │ Layer 1: Gateway 限流  │  Sentinel (Spring Cloud Gateway)
                              │ • 全局 QPS 控制        │  粗粒度，拒绝恶意流量洪峰
                              │ • 路径级别规则          │  429 + Retry-After
                              │ • 快速失败（无等待）     │
                              └──────────┬───────────┘
                                         │ (通过)
                              ┌──────────┴───────────┐
                              │ Layer 2: 应用级限流     │  Sentinel + 自定义拦截器
                              │ • 用户维度 QPS          │  中粒度，保护下游资源
                              │ • 角色差异化阈值         │  注册用户 > 游客
                              │ • WARM_UP 预热         │
                              └──────────┬───────────┘
                                         │ (通过)
                              ┌──────────┴───────────┐
                              │ Layer 3: 业务级限流     │  Redis + Lua
                              │ • 验证码发送频率         │  细粒度，精确计数
                              │ • 登录失败锁定           │  滑动窗口 / Sorted Set
                              │ • 敏感操作频率           │
                              └──────────┬───────────┘
                                         │ (通过)
                              ┌──────────┴───────────┐
                              │    Controller         │
                              └──────────────────────┘
```

### 1.1 各层职责

| 层级 | 实现 | 粒度 | 存储 | 特点 |
|------|------|------|------|------|
| Gateway | Sentinel Gateway 流控 | 全局 + 路径 | 内存 (单机) | 最小开销，拒绝在最外层 |
| 应用级 | Sentinel `@SentinelResource` | 用户 + API | 内存 (单机) | 支持预热、匀速排队 |
| 业务级 | Redis Lua 脚本 | 手机/邮箱/IP | Redis | 跨实例精确计数 |

### 1.2 穿透原则

每一层独立判断，**被上层拒绝的请求不会到达下层**。Gateway 返回 429 后，不消耗应用层计数器。

```
Gateway 429 (Layer 1) → 直接返回，不进入 Layer 2/3
App 429 (Layer 2)     → 直接返回，不触发 Redis 计数
Biz 429 (Layer 3)     → 返回 429，记录审计日志
```

---

## 2. 算法选型

### 2.1 决策矩阵

| 场景 | 算法 | 存储 | 理由 |
|------|------|------|------|
| Gateway 全局 QPS | **令牌桶** (Sentinel 默认) | 内存 | 允许突发，平滑流量，Sentinel 原生支持 |
| 用户级 API 限流 | **滑动窗口** (Sentinel 热点参数) | 内存 | 精确控制单用户频率，支持参数级规则 |
| 验证码发送 | **固定窗口 + 计数器** (Redis String + TTL) | Redis | 简单可靠，"60 秒 1 次"场景，TTL 自动清理 |
| 登录失败锁定 | **滑动窗口** (Redis Sorted Set) | Redis | 精确到毫秒，"5 分钟内 5 次"需要时间衰减 |
| 支付回调 | **不限制** | — | 幂等在业务层通过 `pay:processed:<txn_id>` 去重保证 |

### 2.2 算法详解

#### 令牌桶 (Token Bucket) — Sentinel QPS 模式

```
┌─────────────┐
│  Token Gen   │  以固定速率（QPS）往桶中放令牌
│  (rate/sec)  │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Bucket     │  最大容量 = burst size
│  (max N)     │  桶满则丢弃令牌
└──────┬──────┘
       │
       ▼
  请求到达 → 尝试获取令牌
       │
   ┌───┴───┐
   │有令牌？│──YES──▶ 放行，令牌数-1
   └───┬───┘
       │ NO
       ▼
     429 或排队（取决于 controlBehavior）
```

**参数说明：**
- `grade=QPS`: 按每秒请求数控制
- `count`: QPS 阈值
- `controlBehavior`:
  - `REJECT` (默认): 超过直接拒绝，返回 429
  - `WARM_UP`: 冷启动预热，初始阈值为 count/3，线性增长至 count
  - `RATE_LIMITER`: 匀速排队，请求在队列中等候（最大 `maxQueueingTimeMs`）

#### 滑动窗口 (Redis) — 登录锁定

```
时间轴 ──────────────────────────────────────────▶
       │          当前时间 T
       │          │
       │  ┌───────┼───────┐
       │  │ 窗口   │       │  窗口 = [T-5min, T]
       │  │[T-5min]│       │
       │  └───────┼───────┘
       │          │
       ▼          ▼
  ┌─────────────────────────────────┐
  │  Sorted Set: login:fail:<uid>   │
  │  Member = timestamp (score)     │
  │  Value  = "<ip>:<device_id>"    │
  └─────────────────────────────────┘
  
  每次检查:
  1. ZREMRANGEBYSCORE key 0 (now-5min*1000)  # 清理过期记录
  2. ZCARD key                                  # 统计窗口内失败次数
  3. 若 > 5 → 锁定 15 分钟 (SET login:lock:<uid> 1 EX 900)
  4. 若 ≤ 5 → ZADD key <now_ms> <ip:device>   # 记录本次失败
```

---

## 3. Redis Key 设计

### 3.1 Key 命名与 TTL

| 场景 | Key Pattern | 数据类型 | TTL | 说明 |
|------|-----------|:------:|:---:|------|
| 短信发送 (单手机) | `rate:sms:<phone>` | String | 60s | 计数器，达到上限返回 429 |
| 短信发送 (单手机日) | `rate:sms:daily:<phone>` | String | 到当日 23:59:59 | 日上限 |
| 短信发送 (单 IP) | `rate:sms:ip:<ip>` | String | 3600s | IP 上限 |
| 邮件发送 (单邮箱) | `rate:email:<email>` | String | 60s | 同手机逻辑 |
| 邮件发送 (单邮箱日) | `rate:email:daily:<email>` | String | 到当日 23:59:59 | |
| 邮件发送 (单 IP) | `rate:email:ip:<ip>` | String | 3600s | |
| 注册 (单 IP) | `rate:register:ip:<ip>` | String | 3600s | 3 次/小时 |
| 登录失败 | `login:fail:<uid>` | Sorted Set | 窗口过期自动清理 | 滑动窗口 |
| 登录锁定 | `login:lock:<uid>` | String | 900s (15min) | 锁定标志 |
| 密码重置 | `rate:reset-pwd:<email>` | String | 3600s | 3 次/小时 |
| 数据导出 | `rate:export:<uid>` | String | 600s | 1 次/10 分钟 |

### 3.2 Lua 脚本 — 短信发送示例

计数器模式使用原子 Lua 保证检查+递增的原子性：

```lua
-- rate_limit_sms.lua
-- KEYS[1]: rate:sms:<phone>
-- KEYS[2]: rate:sms:daily:<phone>
-- ARGV[1]: minute_limit (1)
-- ARGV[2]: daily_limit (10)
-- ARGV[3]: ttl_seconds (60)
-- ARGV[4]: daily_ttl_seconds (到当日结束)

local minute_count = redis.call('GET', KEYS[1])
if minute_count and tonumber(minute_count) >= tonumber(ARGV[1]) then
    return {0, minute_count, "MINUTE_LIMIT"}  -- 拒绝
end

local daily_count = redis.call('GET', KEYS[2])
if daily_count and tonumber(daily_count) >= tonumber(ARGV[2]) then
    return {0, daily_count, "DAILY_LIMIT"}  -- 拒绝
end

local new_minute = redis.call('INCR', KEYS[1])
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
local new_daily = redis.call('INCR', KEYS[2])
redis.call('EXPIREAT', KEYS[2], tonumber(ARGV[4]))

return {1, new_minute, new_daily}  -- 放行
```

---

## 4. Sentinel 规则架构

### 4.1 规则存储

Sentinel 规则存储在 Nacos 配置中心，支持运行时热更新，无需重启：

```
Nacos Namespace: release
Group: DEFAULT_GROUP
├── Data ID: sentinel-gateway-rules.json     (Gateway 层规则)
├── Data ID: sentinel-app-rules.json         (应用层规则)
└── Data ID: sentinel-degrade-rules.json     (降级规则)
```
> Nacos namespace/group 设计详见 [INFRASTRUCTURE.md §1.2](INFRASTRUCTURE.md#12-group-设计)。

### 4.2 Gateway 层规则

```json
[
  {
    "resource": "auth_login",
    "resourceMode": 0,
    "grade": "QPS",
    "count": 60,
    "limitApp": "default",
    "controlBehavior": "REJECT"
  },
  {
    "resource": "auth_register",
    "resourceMode": 0,
    "grade": "QPS",
    "count": 15,
    "limitApp": "default",
    "controlBehavior": "REJECT"
  },
  {
    "resource": "auth_send_code",
    "resourceMode": 0,
    "grade": "QPS",
    "count": 10,
    "limitApp": "default",
    "controlBehavior": "REJECT"
  },
  {
    "resource": "admin_all",
    "resourceMode": 0,
    "grade": "QPS",
    "count": 40,
    "limitApp": "default",
    "controlBehavior": "REJECT"
  }
]
```

### 4.3 应用层规则（用户维度）

```json
[
  {
    "resource": "POST:/auth/login",
    "grade": "QPS",
    "count": 20,
    "limitApp": "default",
    "controlBehavior": "WARM_UP",
    "warmUpPeriodSec": 5
  },
  {
    "resource": "POST:/auth/register",
    "grade": "QPS",
    "count": 10,
    "limitApp": "default",
    "controlBehavior": "REJECT"
  },
  {
    "resource": "GET:/user/profile",
    "grade": "QPS",
    "count": 50,
    "limitApp": "default",
    "controlBehavior": "WARM_UP",
    "warmUpPeriodSec": 3
  },
  {
    "resource": "GET:/admin/users",
    "grade": "QPS",
    "count": 20,
    "limitApp": "default",
    "controlBehavior": "REJECT"
  }
]
```

### 4.4 降级规则

```json
[
  {
    "resource": "POST:/sub/orders",
    "grade": "SLOW_REQUEST_RATIO",
    "count": 1000,
    "timeWindow": 30,
    "minRequestAmount": 5,
    "slowRatioThreshold": 0.5
  },
  {
    "resource": "GET:/user/profile",
    "grade": "EXCEPTION_RATIO",
    "count": 0.3,
    "timeWindow": 30,
    "minRequestAmount": 10
  }
]
```

**熔断策略：**

| 策略 | 条件 | 熔断时长 | 半开探测 |
|------|------|:------:|---------|
| 慢调用比例 | P99 > 1000ms 且比例 > 50% | 30s | 自动恢复 1 个请求探活 |
| 异常比例 | 异常率 > 30% | 30s | 同上 |
| 异常数 | 1 分钟内异常 > 10 | 30s | 同上 |

---

## 5. 429 响应规范

### 5.1 响应 Body

```json
{
  "code": 42902,
  "message": "请求频率过高，请稍后重试",
  "data": null,
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": 1716019200
}
```

### 5.2 响应头

| Header | 类型 | 必填 | 说明 | 示例 |
|--------|------|:--:|------|------|
| `Retry-After` | int | ✅ | 建议重试等待秒数 | `Retry-After: 60` |
| `X-RateLimit-Limit` | int | ✅ | 当前窗口的请求上限 | `X-RateLimit-Limit: 20` |
| `X-RateLimit-Remaining` | int | ✅ | 剩余可用次数 | `X-RateLimit-Remaining: 0` |
| `X-RateLimit-Reset` | int | ✅ | 窗口重置的 Unix timestamp | `X-RateLimit-Reset: 1716019260` |
| `X-RateLimit-Scope` | string | ❌ | 限流维度 (调试用) | `X-RateLimit-Scope: user:1001` |

### 5.3 错误码映射

| 错误码 | 限流层 | Retry-After | 用户提示 |
|--------|--------|:----------:|---------|
| 42901 | 业务级 — 配额用尽 | 窗口剩余时间 | "今日配额已用尽，明日 0:00 重置" |
| 42902 | 应用级 — Sentinel QPS | 动态（1-60s） | "请求过于频繁，请稍后再试" |
| 42903 | 业务级 — IP 限流 | 窗口剩余时间 | "操作频率过高，请 N 分钟后再试" |
| 42904 | 业务级 — 用户限流 | 窗口剩余时间 | "操作频率过高，请 N 分钟后再试" |

---

## 6. 降级策略

### 6.1 Redis 不可用时

Sentinel 在 Redis 不可用时自动降级为本地模式：

```java
@Component
public class RateLimitService {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    // 本地降级缓存 (Caffeine LRU)
    private final Cache<String, AtomicInteger> localCache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(60, TimeUnit.SECONDS)
        .build();
    
    private final Cache<String, Long> localWindowCache = Caffeine.newBuilder()
        .maximumSize(5_000)
        .expireAfterWrite(60, TimeUnit.SECONDS)
        .build();
    
    public boolean tryAcquire(String key, int limit, Duration window) {
        try {
            // 优先使用 Redis
            return redisTryAcquire(key, limit, window);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unreachable, falling back to local rate limiting: key={}", key);
            return localTryAcquire(key, limit, window);
        }
    }
    
    private boolean localTryAcquire(String key, int limit, Duration window) {
        // 本地降级：精度降低（单实例而非全局），但不影响主流程
        AtomicInteger counter = localCache.get(key, k -> new AtomicInteger(0));
        return counter.incrementAndGet() <= limit;
    }
}
```

**降级影响：**

| 场景 | 正常模式 | 降级模式 | 影响 |
|------|---------|---------|------|
| 短信发送 1/min | Redis 全局精确 | 本地单实例计数 | 多实例可能各自放行 1 次 |
| 登录失败锁定 | Redis Sorted Set 精确 | 本地 LRU 近似 | 攻击者可跨实例重试（低风险） |
| IP 注册限制 | Redis 全局精确 | 本地近似 | 同上 |

> 降级模式下允许精度损失（宽松放行），优先保证可用性。

### 6.2 Nacos 不可达时

Sentinel 使用内存中已加载的最后一份规则快照（`last-known-good`），新规则不生效但现有规则不丢失。

---

## 7. 预热策略

`WARM_UP` 模式用于防止冷启动时缓存未命中导致的流量尖峰打垮服务：

```
QPS
 │
 │ 预热曲线 (count=60, warmUpPeriodSec=10)
 │
60 ┤                              ┌────────────
   │                        ╱
   │                     ╱
40 ┤                  ╱   稳定阈值 = 60
   │               ╱
20 ┤            ╱
   │        ╱╱
   │    ╱╱   初始阈值 = 60/3 = 20
   │╱╱
 0 ┼────┬────┬────┬────┬────┬────┬────▶ 时间(s)
   0    2    4    6    8   10   12
        │                    │
        └── 预热阶段 ───────┘  └── 正常运行 ──
```

**适用接口：** 登录、用户信息查询、套餐列表（高频且依赖缓存）

```json
{
  "resource": "GET:/sub/plans",
  "grade": "QPS",
  "count": 30,
  "controlBehavior": "WARM_UP",
  "warmUpPeriodSec": 10
}
```

---

## 8. 监控与告警

### 8.1 指标采集

| 指标 | 数据源 | Prometheus 查询 |
|------|--------|---------------|
| Sentinel 拒绝总数 | Sentinel Metrics | `rate(sentinel_blocked_total[5m])` |
| 按资源拒绝 | Sentinel Metrics | `rate(sentinel_blocked_total{resource="POST:/auth/login"}[5m])` |
| 熔断触发次数 | Sentinel Metrics | `sentinel_degrade_open_total` |
| 业务限流拒绝 | 自定义 Counter | `rate(rate_limit_biz_reject_total[5m])` |

### 8.2 日志记录

每次限流触发时记录审计日志，级别为 WARN：

```java
// log format
log.warn("Rate limit triggered: resource={}, userId={}, ip={}, scope={}, " +
         "limitQps={}, currentQps={}, windowRemainingMs={}",
         resource, userId, ip, scope, limitQps, currentQps, windowRemainingMs);
```

### 8.3 告警

| 告警 | 条件 | 级别 | 说明 |
|------|------|:----:|------|
| Sentinel 拒绝率异常 | `rate(sentinel_blocked_total[5m]) > 5/s` | P2 | 可能有攻击或误配 |
| 熔断触发 | `sentinel_degrade_open_total increase > 0` | P1 | 下游异常需排查 |
| 单一用户高频限流 | 10 分钟内被拒 > 100 次 | P2 | 可能是自动化脚本/攻击 |

---

## 9. 开发实现清单

### 9.1 Maven 依赖

```xml
<!-- Sentinel -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
</dependency>
<!-- Sentinel 规则持久化到 Nacos -->
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-datasource-nacos</artifactId>
</dependency>
<!-- Caffeine (本地降级缓存) -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

### 9.2 配置文件

```yaml
# application.yml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: sentinel-dashboard.zhiyu.svc:8080
        port: 8719
      datasource:
        ds-gateway:
          nacos:
            server-addr: nacos.zhiyu.svc:8848
            namespace: ${spring.cloud.nacos.config.namespace}
            group-id: DEFAULT_GROUP
            data-id: sentinel-gateway-rules.json
            data-type: json
            rule-type: gw-flow
        ds-app:
          nacos:
            server-addr: nacos.zhiyu.svc:8848
            namespace: ${spring.cloud.nacos.config.namespace}
            group-id: DEFAULT_GROUP
            data-id: sentinel-app-rules.json
            data-type: json
            rule-type: flow
```

### 9.3 自定义注解

```java
// 业务限流注解
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    String key();          // Redis key 前缀
    int limit();           // 窗口内上限
    int windowSeconds();   // 窗口长度（秒）
    String message() default "请求频率过高";
}

// 使用示例
@PostMapping("/send-register-code")
@RateLimit(key = "rate:email", limit = 1, windowSeconds = 60, message = "验证码已发送，请60秒后重试")
public ApiResponse<?> sendCode(@Valid @RequestBody SendCodeRequest req) { ... }
```

---

> **引用索引**：
> - [OPS.md §11](OPS.md#11-接口限流阈值) — 运行时阈值配置
> - [DEVELOPMENT-STANDARDS.md §4.2](DEVELOPMENT-STANDARDS.md#42-错误码清单) — 429 错误码定义
> - [ARCHITECTURE.md §6.1](ARCHITECTURE.md#61-filter-chain-顺序) — Filter Chain 中的限流位置
> - [设计规格 §11.6](superpowers/specs/2026-05-17-zhiyu-backend-design.md#116-限流策略) — 限流策略概述
