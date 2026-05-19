# ZhiYu-Backend 架构决策与关键流程

> 本文档记录架构决策 (ADR) 和关键业务流程的时序设计。每条 ADR 包含背景、决策、理由、后果和已考虑的替代方案。

## 1. 系统上下文 (C4 Level 1)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              用户 & 管理员                                     │
│                    ┌──────────┐              ┌──────────┐                     │
│                    │ iOS App  │              │ Android  │                     │
│                    └────┬─────┘              └────┬─────┘                     │
│                         │                         │                           │
│                    ┌────┴─────────────────────────┴────┐                      │
│                    │        Admin Web (React)           │                      │
│                    └─────────────────┬─────────────────┘                      │
└──────────────────────────────────────┼────────────────────────────────────────┘
                                       │ HTTPS (TLS 1.3)
                                       │
┌──────────────────────────────────────┼────────────────────────────────────────┐
│                              ZhiYu Backend                                     │
│                                      │                                         │
│  ┌───────────────────────────────────┼───────────────────────────────────┐    │
│  │                        K8s Cluster (Alibaba Cloud ACK)                 │    │
│  │  ┌────────────────────┐  ┌────────────────────┐                       │    │
│  │  │  zhiyu-backend × N │  │    Spring Cloud     │                       │    │
│  │  │  (Spring Boot 3.x) │  │     Gateway         │                       │    │
│  │  └────────┬───────────┘  └─────────┬──────────┘                       │    │
│  │           │                        │                                   │    │
│  └───────────┼────────────────────────┼───────────────────────────────────┘    │
│              │                        │                                        │
└──────────────┼────────────────────────┼────────────────────────────────────────┘
               │                        │
     ┌─────────┼─────────┬──────────────┼──────────────┬─────────────┐
     │         │         │              │              │             │
┌────┴───┐ ┌───┴──┐ ┌────┴───┐ ┌───────┴──────┐ ┌─────┴────┐ ┌─────┴─────┐
│ MySQL  │ │Redis │ │ Nacos  │ │Aliyun OSS    │ │Prometheus│ │   Loki    │
│ 8.0    │ │ 7.x  │ │Config/ │ │对象存储      │ │+Grafana  │ │日志聚合   │
│ (RDS)  │ │      │ │Registry│ │              │ │          │ │          │
└────────┘ └──────┘ └────────┘ └──────────────┘ └──────────┘ └───────────┘
               │
     ┌─────────┼──────────────────────────────┐
     │         │                              │
┌────┴───┐ ┌───┴─────┐ ┌──────────┐ ┌───────┴──────┐
│ 微信    │ │ 支付宝   │ │Apple IAP │ │Google Play   │
│ Open    │ │ 支付     │ │StoreKit  │ │Billing       │
│ Platform│ │          │ │          │ │              │
└─────────┘ └─────────┘ └──────────┘ └──────────────┘
     │
┌────┴─────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│阿里云    │ │阿里云 SMS │ │  APNs    │ │   FCM    │
│邮件推送   │ │短信服务  │ │Apple推送  │ │Google推送 │
└──────────┘ └──────────┘ └──────────┘ └──────────┘
```

**外部系统依赖：**

| 系统 | 用途 | 协议 | 容错策略 |
|------|------|------|---------|
| MySQL 8.0 (RDS) | 主数据库 | JDBC/HikariCP | 主从切换 + ProxySQL |
| Redis 7.x | 缓存/黑名单/配额 | Lettuce | Sentinel 自动故障转移 |
| Nacos | 配置中心 + 服务注册 | gRPC/HTTP | 本地快照缓存降级 |
| Aliyun OSS | 对象存储（头像/导出） | HTTPS | 重试 3 次 + 指数退避 |
| 微信 Open Platform | OAuth 登录 | HTTPS | 超时 5s + 重试 1 次 |
| 支付宝 | 支付下单/回调 | HTTPS | 回调排队 + 定时查单补偿 |
| Apple StoreKit | IAP 票据验证 | HTTPS | 重试 3 次 |
| Google Play Billing | 票据验证 | HTTPS | 重试 3 次 |
| 阿里云邮件推送 | 验证码/通知邮件 | SMTP/API | 异步队列 + 重试 |
| 阿里云 SMS | 短信验证码 | HTTPS | 异步队列 + 重试 |
| APNs | iOS 推送 | HTTP/2 | 失败标记无效 token |
| FCM / 个推 | Android 推送 | HTTP/2 | 失败标记无效 token |
| Prometheus + Grafana | 监控告警 | HTTP scrape | — |
| Loki | 日志聚合 | gRPC push | 本地缓冲 50MB |

---

## 2. 架构决策记录 (ADR)

### ADR-001: 单模块领域隔离 vs 多 Maven 模块

**状态：** 已决策

**背景：** 需要组织认证、用户、订阅、管理后台四个领域的代码。可选方案：(A) 单 Maven 模块 + 包隔离，(B) 多 Maven 模块（zhiyu-auth / zhiyu-user / zhiyu-subscription / zhiyu-admin / zhiyu-common / zhiyu-server）。

**决策：** 选择方案 B — 多 Maven 模块。

**理由：**
- 编译期强制依赖方向（上层 → 下层，不可逆），防止循环依赖
- 可独立构建和测试每个模块，缩短 CI 时间
- 日后拆分微服务时，模块可平滑升级为独立服务
- 公共代码（zhiyu-common）被所有模块共享，版本统一管理

**后果：**
- 模块间接口需明确定义（Service 接口在各自模块中，仅通过注入调用）
- 初期多 5 个 `pom.xml` 需要维护
- 横切关注点（如安全 Filter）放在 common 模块

**替代方案（已拒绝）：**
- 方案 A 过于灵活，团队扩张后容易产生循环依赖
- 直接多微服务：初期不需要分布式复杂度

---

### ADR-002: JWT RS256 非对称签名 vs HS256 对称签名

**状态：** 已决策

**背景：** 需要签发和验证 JWT access_token。可选方案：(A) HS256 共享密钥，(B) RS256 RSA 公私钥对。

**决策：** 选择方案 B — RS256 非对称签名。

**理由：**
- 私钥仅认证服务持有，公钥可分发——若日后拆微服务，其他服务仅需公钥即可验签
- 泄露公钥不导致 token 可被伪造（与 HS256 泄露密钥 = 全破坏不同）
- jjwt 库对 RS256 的支持成熟

**后果：**
- 密钥管理：私钥通过 K8s Secret 注入为环境变量 `JWT_PRIVATE_KEY`
- 比 HMAC 略慢（但可忽略，RSA 签名微秒级）
- 公钥存储在 Nacos 共享配置中

**替代方案（已拒绝）：**
- HS256 在单模块场景下可行但无扩展空间，切换成本高

---

### ADR-003: Redis 黑名单 + Refresh Token 轮换 vs 纯有状态 Session

**状态：** 已决策

**背景：** access_token 无状态，退出登录时需要失效机制。可选方案：(A) Redis 黑名单（jti），(B) 纯 Redis Session（access_token 仅存标识符）。

**决策：** 选择方案 A — Redis 黑名单 + Refresh Token 轮换。

**理由：**
- access_token 短期（15min），无状态直接验签，不查 Redis（低延迟）
- 仅退出时 jti 进黑名单（key 过期时间对齐剩余有效期，自动清理）
- refresh_token 轮换机制防止长期 token 被盗用

**后果：**
- 正常请求不依赖 Redis（高可用性）
- Redis 不可用时黑名单降级为内存 LRU 缓存（接受少量失效延迟）
- 需维护黑名单 TTL 对齐 access_token 剩余时间

**替代方案（已拒绝）：**
- 纯 Session：每次请求都查 Redis，增加延迟和 Redis 依赖
- 纯无状态（无可注销）：不支持即时退出

---

### ADR-004: Testcontainers vs H2 内存数据库

**状态：** 已决策

**背景：** 集成测试需要数据库和 Redis。可选方案：(A) H2 内存数据库 + 内嵌 Redis，(B) Testcontainers Docker 容器。

**决策：** 选择方案 B — Testcontainers。

**理由：**
- H2 与 MySQL 行为差异多（SQL 方言、索引策略、JSON 类型），曾导致生产问题
- Testcontainers 使用真实 MySQL 8.0 + Redis 7 镜像，测试环境与生产一致
- Docker 已成开发标配，CI 环境（GitHub Actions）原生支持

**后果：**
- 集成测试启动慢 5-15 秒（容器拉取镜像）
- CI 需 Docker 环境
- 首次拉取镜像需网络

**替代方案（已拒绝）：**
- H2 适合纯单元测试场景，但本项目的 MyBatis XML 和 JSON 类型依赖 MySQL

---

### ADR-005: Maven vs Gradle

**状态：** 已决策

**背景：** 需要选择 Java 构建工具。

**决策：** 选择 Maven。

**理由：**
- 团队更熟悉 Maven 的 POM 模式和插件生态
- Spring Boot 对 Maven 的支持最成熟
- 多模块项目 Maven 的 `<dependencyManagement>` 模式清晰

**后果：**
- XML 冗长但可预测
- 构建速度略慢于 Gradle（但增量编译可缓解）

---

### ADR-006: 构造器注入 vs 字段注入

**状态：** 已决策

**背景：** Spring DI 有三种方式：字段 `@Autowired`、Setter、构造器。

**决策：** 强制构造器注入，配合 Lombok `@RequiredArgsConstructor`。

**理由：**
- 字段注入导致依赖隐藏（一个类注入 10 个字段时无人察觉）
- 构造器注入在编译期保证依赖不缺失
- 方便单元测试（无需 Spring 容器即可手动注入 Mock）
- `@RequiredArgsConstructor` 省去手写构造器

**后果：**
- 类注入超过 5 个时构造器过长 → 触发重构（拆分类）
- 禁止 `@Autowired` 字段注入（在 CI 中通过 Checkstyle/ArchUnit 检查）

---

## 3. 关键流程时序

### 2.1 邮箱注册流程

```
客户端          CAPTCHA服务      后端API          数据库          Redis          邮件服务
  │                │               │               │               │               │
  ├──滑块验证──────▶│               │               │               │               │
  │◄─captchaToken──┤               │               │               │               │
  │                │               │               │               │               │
  ├──POST /auth/send-register-code─▶               │               │               │
  │                │               ├──验证captcha──▶│               │               │
  │                │               │               ├──SET verify:email:<email>──▶│
  │                │               │               │◄──OK─────────│               │
  │                │               │◄──发送成功─────│               ├──发送邮件────▶│
  │◄──200 {expireMinutes:5}───────┤               │               │               │
  │                │               │               │               │               │
  │──输入验证码────────────────────────────────────────────                       │
  │                │               │               │               │               │
  ├──POST /auth/register──────────▶               │               │               │
  │                │               ├──GET verify:email─────────────▶               │
  │                │               │◄──验证码正确──│               │               │
  │                │               ├──校验用户名/邮箱唯一性───────▶               │
  │                │               │◄──无冲突──────│               │               │
  │                │               ├──INSERT user───────────────▶               │
  │                │               ├──INSERT user_auth_identity─▶               │
  │                │               ├──签发 access_token + refresh_token           │
  │                │               ├──SET refresh:<uid>:<did>──▶│               │
  │◄──201 {accessToken,refreshToken}┤               │               │               │
  │                │               │               │               │               │
  │                │               └──async 发送欢迎邮件──────────────────────────▶│
```

### 2.2 第三方登录（微信）流程

```
客户端          微信平台          后端API          数据库           Redis
  │                │               │               │               │
  ├──微信授权─────▶│               │               │               │
  │◄─code+state────┤               │               │               │
  │                │               │               │               │
  ├──POST /auth/third-party───────▶               │               │
  │                │               ├──验证 state───────────────▶│
  │                │               │◄──state有效──│               │
  │                ├──用code换access_token───────▶│               │
  │                │◄─access_token+openid────────┤               │
  │                │               ├──查询 user_auth_identity───▶│
  │                │               │◄──不存在(新用户)或存在─────│
  │                │               │                              │
  │                │               │    [若不存在: 创建user+identity]              │
  │                │               ├──INSERT user───────────────▶│
  │                │               ├──INSERT identity───────────▶│
  │                │               │                              │
  │                │               ├──签发 JWT                    │
  │                │               │  scope=LIMITED (新用户)      │
  │                │               │  scope=FULL (已有用户)       │
  │                │               ├──SET refresh:─────────────▶│
  │◄──200 {accessToken,scope}──────┤               │               │
```

### 2.3 支付下单+回调流程

```
客户端          后端API          第三方支付        数据库           Redis
  │                │               │               │               │
  ├──POST /sub/orders─────────────▶               │               │
  │                ├──校验 plan───────────────────▶               │
  │                ├──创建 order (PENDING)────────▶               │
  │                ├──调支付API───────────────────▶│               │
  │                │◄─prepay_id───────────────────┤               │
  │                ├──SET pay:processed:<txn_id>──────────────────▶│
  │◄──201 {orderNo, prepayInfo}──┤               │               │
  │                │               │               │               │
  │──调起支付─────────────────────▶│               │               │
  │◄─支付结果──────────────────────┤               │               │
  │                │               │               │               │
  │                │               ├──POST /sub/callback/WECHAT───▶│
  │                │               │               ├──验签─────────│
  │                │               │               ├──EXISTS pay:processed────▶│
  │                │               │               │◄──存在→200(幂等)│
  │                │               │               ├──UPDATE order→PAID──────▶│
  │                │               │               ├──INSERT payment_record───▶│
  │                │               │               ├──UPSERT user_subscription─▶│
  │                │               │               │               │
  │                │               │◄──200 (SUCCESS)┤               │
```

### 2.4 密码重置流程

```
客户端          后端API          数据库           Redis           邮件服务
  │                │               │               │               │
  ├──POST /auth/forgot-password───▶               │               │
  │                ├──验证 CAPTCHA──────────────▶│               │
  │                ├──生成重置 token──────────────────────────▶│
  │                │               │               │  (15min TTL) │
  │                │               │               │               │
  │                │               │               ├──发送重置邮件─▶│
  │◄──200 (始终返回)──────────────┤               │               │
  │                │               │               │               │
  │──点击邮件中的重置链接─────────────────────────────────────────│
  │  https://zhiyu.app/reset-password?token=xxx                  │
  │                │               │               │               │
  ├──POST /auth/reset-password────▶               │               │
  │                ├──验证 token────────────────▶│               │
  │                │◄──token有效──│               │               │
  │                ├──校验新密码与用户名/邮箱不同──│               │
  │                ├──UPDATE user.password_hash──▶               │
  │                ├──DELETE 该用户所有 refresh──▶│               │
  │                ├──DELETE reset token─────────▶│               │
  │◄──200 (密码重置成功)──────────┤               │               │
```

### 2.5 Refresh Token 轮换与盗用检测

```
客户端          后端API          Redis
  │                │               │
  ├──POST /auth/refresh───────────▶│
  │  {refreshToken:"old-r1"}       │
  │                ├──GET refresh:<uid>:<did>──▶│
  │                │◄──value="old-r1"──│
  │                ├──验证匹配───────│
  │                ├──生成 new access_token
  │                ├──生成 new refresh_token "new-r2"
  │                ├──SET refresh:<uid>:<did>="new-r2"──▶│
  │◄──200 {accessToken:"new",refreshToken:"new-r2"}───│
  │                │               │
  │──恶意者使用已被替换的"old-r1"──▶│
  │                ├──GET refresh:<uid>:<did>──▶│
  │                │◄──value="new-r2" (不匹配!)──│
  │                ├──检测到重用!────│
  │                ├──DELETE 该用户所有 refresh──▶│
  │                ├──记录安全审计────│
  │◄──40104 (全设备登出)───────────│
```

---

## 4. 模块依赖关系

```
                    zhiyu-server
                   (入口 + 装配)
                   /    |    \
                  /     |     \
         zhiyu-admin  zhiyu-subscription
              |              |
         zhiyu-user    zhiyu-user
              |              |
         zhiyu-auth    zhiyu-auth
              \         /
           zhiyu-common
         (不依赖任何业务模块)
```

**依赖规则：**
- `common`：零业务依赖，纯工具/配置/异常/安全 Filter
- `auth` → `common`：认证领域
- `user` → `auth` + `common`：用户领域依赖认证模块的 TokenService
- `subscription` → `user` + `common`：订阅需要用户信息
- `admin` → `subscription` + `user` + `common`：管理后台可操作所有领域
- `server` → 所有模块：仅做装配和启动

**跨模块通信：**
- 仅通过 Service 接口注入（上层注入下层 Service）
- 禁止跨模块直接注入 Mapper
- 禁止循环依赖（maven-enforcer-plugin 检查）

---

## 5. 部署架构

### 5.1 K8s 拓扑

```
                              ┌──────────────┐
                              │  Cloud LB    │
                              │  (SLB/NLB)   │
                              └──────┬───────┘
                                     │
                              ┌──────┴───────┐
                              │  Nginx       │
                              │  Ingress     │
                              │  Controller  │
                              └──────┬───────┘
                                     │
                    ┌────────────────┼────────────────┐
                    │                │                │
              ┌─────┴─────┐   ┌─────┴─────┐   ┌─────┴─────┐
              │ /api/v1/* │   │ /admin/*  │   │ /actuator │
              │ (public)  │   │ (internal)│   │ (health)  │
              └─────┬─────┘   └─────┬─────┘   └─────┬─────┘
                    │               │                │
              ┌─────┴───────────────┴────────────────┴─────┐
              │          Spring Cloud Gateway               │
              │          (限流 / 路由 / 日志)                 │
              └─────────────────┬──────────────────────────┘
                                │
              ┌─────────────────┴──────────────────────────┐
              │            Service: zhiyu-backend           │
              │            (ClusterIP, port 8080)            │
              └─────────────────┬──────────────────────────┘
                                │
              ┌─────────────────┼──────────────────────────┐
              │                 │                           │
        ┌─────┴─────┐    ┌─────┴─────┐            ┌───────┴──────┐
        │  Pod 1    │    │  Pod 2    │   ...      │   Pod N      │
        │  (Spring  │    │  (Spring  │            │   (Spring    │
        │   Boot)   │    │   Boot)   │            │    Boot)     │
        │  2C/4Gi   │    │  2C/4Gi   │            │   2C/4Gi     │
        └─────┬─────┘    └─────┬─────┘            └───────┬──────┘
              │                │                           │
              └────────────────┼───────────────────────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
    ┌─────┴─────┐       ┌─────┴─────┐       ┌─────┴─────┐
    │ MySQL RDS │       │Redis      │       │ Nacos     │
    │ (主从)     │       │Sentinel   │       │ Cluster   │
    │           │       │ 3 节点     │       │ 3 节点     │
    └───────────┘       └───────────┘       └───────────┘
```

> **Phase 1 部署策略**：Spring Cloud Gateway 作为嵌入式库（`spring-cloud-starter-gateway`）运行在 zhiyu-backend 应用内部，不单独部署。上图中 Gateway 展示的是**逻辑分层**，物理上 Ingress 直接路由到 `Service: zhiyu-backend`。Gateway Filter Chain 在应用进程内执行（见 [§6 安全架构](#6-安全架构)）。后续可按需将 Gateway 提取为独立微服务，K8s Deployment 和 CI-CD 清单无需变更。

### 5.2 环境划分

| 环境 | 用途 | 副本数 | 资源/Pod | 数据库 | CI 触发 |
|------|------|:----:|:-------:|--------|:------:|
| `dev` | 本地开发 | 1 | 1C/1Gi | K8s 共享实例 | 手动 |
| `test` | 集成测试 + E2E | 1 | 2C/2Gi | Testcontainers | PR 自动 |
| `staging` | 预发布验证 | 2 | 2C/4Gi | RDS 独立实例 | main 分支自动 |
| `release` | 生产环境 | 4+ | 2C/4Gi | RDS (主从) | Release 分支手动 |

### 5.3 通信矩阵

下表列出系统中所有组件间的网络通信路径，用于网络策略配置、安全审计和故障排查。

#### 5.3.1 入口流量（外部 → 集群）

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 1 | 公网 (客户端/浏览器) | Ingress Controller | HTTPS | 443 | 入站 | 用户 API 请求、管理后台 |
| 2 | 公网 | Ingress Controller | HTTP | 80 | 入站 | HTTP→HTTPS 重定向（生产环境关闭） |

#### 5.3.2 集群路由（Ingress → 应用）

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 3 | Ingress Controller | `Service: zhiyu-backend` | HTTP | 8080 | → | API 路由 (`/api/v1`, `/actuator/health`) |
| 4 | Ingress Controller | `Service: zhiyu-backend` | HTTP | 8080 | → | Admin API (`/admin/*`，需 IP 白名单) |

#### 5.3.3 应用 → 基础设施（数据平面）

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 5 | Backend Pod (zhiyu-backend) | MySQL (RDS 或 StatefulSet) | TCP | 3306 | → | 业务数据持久化（HikariCP 连接池，max 20/Pod） |
| 6 | Backend Pod | Redis (Sentinel 或 StatefulSet) | TCP | 6379 | → | 缓存 + Token 黑名单 + 分布式锁 |
| 7 | Backend Pod | Nacos Server | HTTP | 8848 | → | 配置拉取 (`dataId` 订阅)、服务注册 |

#### 5.3.4 应用 → 基础设施（服务发现与 RPC）

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 8 | Backend Pod | Nacos Server | gRPC | 9848 | → | 服务注册/心跳、配置变更长轮询 (Nacos 2.x gRPC) |
| 9 | Backend Pod (Sentinel) | Sentinel Dashboard | HTTP | 8080 | ← | 流控规则推送（Dashboard→客户端，可选） |

#### 5.3.5 Init 容器依赖检查

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 10 | `init: wait-for-mysql` | MySQL | TCP | 3306 | → | `nc -z` 端口探测，确认 MySQL 监听后再启动 |
| 11 | `init: wait-for-nacos` | Nacos | HTTP | 8848 | → | `wget` 健康检查端点，确认 Nacos 就绪（可跳过） |

#### 5.3.6 外部出站（第三方 API）

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 12 | Backend Pod | 微信支付 API (`api.mch.weixin.qq.com`) | HTTPS | 443 | → | 统一下单、支付回调、退款 |
| 13 | Backend Pod | 支付宝 API (`openapi.alipay.com`) | HTTPS | 443 | → | 支付、回调验证 |
| 14 | Backend Pod | Apple App Store API | HTTPS | 443 | → | IAP 收据验证、Server Notifications |
| 15 | Backend Pod | Google Play Developer API | HTTPS | 443 | → | 订阅状态查询、RTDN 回调 |
| 16 | Backend Pod | 微信/QQ OAuth API | HTTPS | 443 | → | 第三方登录（openid + access_token） |
| 17 | Backend Pod | Google/Apple OAuth API | HTTPS | 443 | → | 第三方登录（需 PIA 数据出境评估） |
| 18 | Backend Pod | 阿里云邮件推送/SMS API | HTTPS | 443 | → | 验证码邮件、短信通知 |
| 19 | Backend Pod | FCM / APNs / 个推 API | HTTPS | 443 | → | 推送通知 |

#### 5.3.7 监控平面

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 20 | Prometheus | Backend Pod | HTTP | 8080 | → | 抓取 `/actuator/prometheus`（JVM + 业务指标） |
| 21 | Prometheus | node-exporter (DaemonSet) | HTTP | 9100 | → | 宿主机 CPU/内存/磁盘/网络指标 |
| 22 | Prometheus | kube-state-metrics | HTTP | 8080 | → | K8s 对象状态指标（Pod/Deploy/STS） |
| 23 | Prometheus | kube-state-metrics (telemetry) | HTTP | 8081 | → | kube-state-metrics 自身指标 |
| 24 | Grafana | Prometheus | HTTP | 9090 | → | 数据源查询（看板渲染） |
| 25 | 运维浏览器 | Grafana Service | HTTP | 30000 | 入站 | Grafana Web UI (NodePort) |

#### 5.3.8 Nacos 集群间通信（仅集群模式，非 standalone）

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 26 | Nacos Peer | Nacos Peer | gRPC | 9848 | ↔ | 服务注册表同步 (Distro 协议) |
| 27 | Nacos Peer | Nacos Peer | TCP | 7848 | ↔ | Raft 一致性协议 (CP 模式配置持久化) |

#### 5.3.9 Redis Sentinel 通信（仅生产环境，dev/staging 用单实例）

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 28 | Backend Pod (Lettuce) | Redis Sentinel | TCP | 26379 | → | 哨兵拓扑发现、主从切换通知 |

#### 5.3.10 K8s 控制平面（集群运维）

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 29 | kubelet / kubectl / Controller | API Server | HTTPS | 6443 | → | K8s API 入口 |
| 30 | API Server | etcd | HTTPS | 2379-2380 | → | 集群状态持久化 |
| 31 | API Server | kubelet | HTTPS | 10250 | → | Pod 日志/exec/指标 |
| 32 | Pod / Service | CoreDNS | UDP/TCP | 53 | → | 集群内 DNS 解析 |
| 33 | Node (Flannel) | Node (Flannel) | UDP | 8472 | ↔ | VXLAN 跨节点 Pod 网络 (Flannel) |

> **网络策略实施**：上表中标记 `入站` 的端口需在 Security Group / NetworkPolicy 中放行。标记 `→` 的方向为 Pod 出站流量，默认允许（K8s 默认出站放行），NetworkPolicy 可选约束。外部出站流量（#12-19）需确认 NAT 网关白名单包含相应域名。

---

## 6. 安全架构

### 6.1 Filter Chain 顺序

```
Request
  │
  ▼
┌────────────────────┐
│ 1. CorsFilter      │  CORS 预检处理
├────────────────────┤
│ 2. RateLimitFilter │  Sentinel 入口限流 (429)
├────────────────────┤
│ 3. RequestLogFilter│  请求日志 + traceId 注入 (MDC)
├────────────────────┤
│ 4. IpWhitelistFilter│ Admin API IP 白名单校验
├────────────────────┤
│ 5. JwtAuthFilter   │  Bearer Token 验签 → SecurityContext
│   (except permitAll)│  提取 userId, scope, jti
├────────────────────┤
│ 6. ScopeCheckFilter│  scope=LIMITED 拒绝 FULL-only 端点
├────────────────────┤
│ 7. ActionTokenFilter│  X-Action-Token 二次验证 (敏感操作)
├────────────────────┤
│ 8. Controller      │  业务处理
└────────────────────┘
  │
  ▼
Response → RequestLogFilter 记录耗时 + 状态码
```

### 6.2 RBAC 权限模型

```
                    ┌──────────────┐
                    │  SUPER_ADMIN  │
                    │  所有权限      │
                    └──────┬───────┘
                           │ 管理
                    ┌──────┴───────┐
                    │    ADMIN      │
                    │  分配权限集    │
                    └──────┬───────┘
                           │ 管理
                    ┌──────┴───────┐
                    │      CS       │
                    │  只读 + 退款   │
                    │  审核权限      │
                    └──────────────┘

权限粒度三级:
  Level 1: 菜单可见性   (dashboard, users, subscriptions, payments, ...)
  Level 2: 操作权限     (users.export, subscriptions.modify, refunds.approve, ...)
  Level 3: 数据范围     (CS 只能查看自己处理的工单，ADMIN 可看全部)
```

### 6.3 Token 流转全链路

```
┌─────────┐     ┌──────────────┐     ┌─────────────┐     ┌──────────┐
│  Client  │     │  Gateway     │     │  Backend     │     │  Redis   │
└────┬────┘     └──────┬───────┘     └──────┬───────┘     └────┬─────┘
     │                 │                    │                  │
     │ 1. Login Request│                    │                  │
     ├────────────────▶│                    │                  │
     │                 ├───────────────────▶│                  │
     │                 │                    │ 2. 验证凭证       │
     │                 │                    │ 3. 生成 token 对  │
     │                 │                    ├─────────────────▶│
     │                 │                    │ 4. SET refresh    │
     │                 │                    │    <uid>:<did>    │
     │                 │                    │◄─────────────────┤
     │                 │◄───────────────────┤                  │
     │◄──access (15m)──┤                    │                  │
     │    refresh (30d) │                    │                  │
     │                 │                    │                  │
     │ 5. API Request  │                    │                  │
     │  (Bearer AT)    │                    │                  │
     ├────────────────▶│                    │                  │
     │                 ├───────────────────▶│                  │
     │                 │                    │ 6. RS256 验签     │
     │                 │                    │ (不查 Redis)      │
     │                 │                    │ 7. 提取 claims    │
     │                 │◄───────────────────┤                  │
     │◄──Response──────┤                    │                  │
     │                 │                    │                  │
     │ 8. Token 过期   │                    │                  │
     ├────────────────▶│                    │                  │
     │                 ├───────────────────▶│                  │
     │                 │                    │ 9. 轮换 token     │
     │                 │                    ├─────────────────▶│
     │                 │                    │ 10. 旧→新         │
     │◄──new AT+RT─────┤◄───────────────────┤◄─────────────────┤
```

---

## 7. 补充 ADR

### ADR-007: MyBatis-Plus vs JPA/Hibernate

**状态：** 已决策

**背景：** 需要选择 Java ORM 框架。

**决策：** 选择 MyBatis-Plus。

**理由：**
- 团队对 MyBatis SQL 可控性更有信心，复杂查询不依赖 Hibernate 生成的 SQL
- MyBatis-Plus 提供 ActiveRecord 风格的 CRUD 开箱即用，减少重复 Mapper XML
- 手写 SQL 在分页、多表关联、JSON 字段操作等场景更透明
- 中国互联网行业 MyBatis 生态最成熟，招聘匹配度高

**后果：**
- 部分简单查询仍需手写 Mapper（不如 JPA 方法命名推导方便）
- 需额外注意 N+1 查询（JPA 的 `@EntityGraph` 不存在，需手动 JOIN）
- 乐观锁、逻辑删除、自动填充等通过 MyBatis-Plus 注解完成

**替代方案（已拒绝）：**
- JPA/Hibernate：自动 DDL 与 Flyway 冲突，复杂查询 SQL 不可控

---

### ADR-008: Flyway vs Liquibase

**状态：** 已决策

**背景：** 需要数据库迁移工具。

**决策：** 选择 Flyway。

**理由：**
- SQL-first 方式，迁移脚本就是标准 SQL 文件，DBA 可直接审查
- Spring Boot 内建 `flyway-core` 自动配置，零代码集成
- 版本号命名清晰（`V<major>.<minor>.<patch>__<desc>.sql`），支持回滚（需 Enterprise 版或手写 undo）
- 社区版功能对本项目完全够用

**后果：**
- 不支持 XML/JSON/YAML 格式的变更定义（Liquibase 支持），但本项目不需要
- 社区版无 undo 功能，回滚依赖备份恢复
- 迁移文件必须按版本号顺序执行，不可跳过

**替代方案（已拒绝）：**
- Liquibase：学习成本更高，changeSet XML 冗长，SQL 审查不直观

---

### ADR-009: Sentinel vs Guava RateLimiter / Bucket4j

**状态：** 已决策

**背景：** 需要接口限流和熔断降级能力。

**决策：** 选择 Sentinel。

**理由：**
- 与 Spring Cloud Alibaba 生态原生集成，配置可通过 Nacos 动态下发
- 支持 QPS/线程数/热点参数/授权等多种限流模式
- 提供 Dashboard 可视化监控，支持实时规则调整
- 内置降级策略（慢调用比例/异常比例/异常数）与 @SentinelResource 注解

**后果：**
- 引入额外 Sentinel Dashboard 部署（轻量，1C/512Mi 即可）
- 规则存储在 Nacos，需保证 Nacos 可用（降级：本地快照）
- 比 Guava RateLimiter 重但能力更强

**替代方案（已拒绝）：**
- Guava RateLimiter：无分布式协调能力，无 Dashboard，规则修改需重启
- Bucket4j：类似 Guava，无控制台

---

### ADR-010: Outbox + 补偿 vs Seata / Saga

**状态：** 已决策

**背景：** 支付场景涉及 DB 写入和第三方 API 调用（微信/支付宝），需要保证数据一致性。

**决策：** 选择 **Outbox + 补偿** 模式，不引入 Seata 或 Saga 框架。

**理由：**

1. **当前是单体应用，不是微服务。** Seata 解决的核心问题是跨服务、各自独立 DB 的分布式事务。ZhiYu-Backend 所有业务表在同一 MySQL，`@Transactional` 已覆盖本地事务。引入 Seata 是 30-50% 性能损失换一个当前不存在的需求。

2. **真正的痛点不是跨库，是外部 API 不可回滚。** 支付渠道（微信/支付宝/Apple/Google）的预支付订单在 DB 回滚后无法撤回——Seata AT 模式的 undo_log 也解决不了外部系统的问题。这个场景只能用补偿（标记订单 CANCELLED + 15 分钟后微信侧自动过期）。

3. **支付回调的并发风险是幂等，不是事务。** 支付渠道可能重复推送回调，这不属于分布式事务范畴。三层幂等（Redis + DB 唯一约束 + 订单状态检查）足以处理。

4. **Seata 的代价超过收益。** AT 模式需要为每张写表配置 undo_log 表（20+ 张额外表）、部署 Seata Server（1-2 Pod + 独立 DB）、全局锁在高并发下退化严重。当前场景下这些代价无回报。

5. **Outbox 模式解决核心痛点：事务后异步通知。** 支付成功后发送邮件/推送不能在 `@Transactional` 内执行（违反耗时操作禁令），Outbox 通过在事务提交后写入事件表，由定时 Job 轮询发送，保证 at-least-once。

**当前策略映射：**

| 场景 | 策略 | 工具 |
|------|------|------|
| 单 DB 内多表写入 | 本地事务 | `@Transactional` |
| 调用支付渠道 API | 事务外调用 + 失败补偿 | 补偿方法 |
| 回调/通知幂等 | 三层幂等 | Redis + DB UK + 状态检查 |
| 事务后异步通知 | Outbox 模式 | `outbox_event` 表 + 定时 Job |
| 并发写冲突 | 乐观锁 | MyBatis-Plus `@Version` |

**后果：**

- 补偿逻辑需手工编写（每个外部 API 调用点配一个补偿方法），但量很小——当前仅 4 个支付渠道的下单接口需要
- Outbox 事件由定时 Job 轮询（每 5s），非实时（可接受：邮件/推送延迟 < 10s）
- 后续如果拆成微服务，需重新评估 Seata/Saga 的适用性（届时仍是单 DB，但跨服务调用需要协调）

**替代方案（已拒绝）：**

- **Seata AT 模式**：性能损失 30-50%，需 20+ undo_log 表 + Seata Server 集群，无法回滚外部 API，收益为零
- **Seata Saga 模式**：适合跨微服务的长事务编排，当前不需要；在单体中引入状态机编排框架是过度设计
- **纯手工补偿（无 Outbox）**：回调处理 + 通知发送耦合在事务内，耗时操作阻塞 DB 连接池

> 事务设计细节详见 [DEVELOPMENT-STANDARDS.md §2.4](../DEVELOPMENT-STANDARDS.md#24-事务管理)。
