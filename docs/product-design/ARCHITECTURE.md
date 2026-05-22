# ZhiYu-Backend 系统架构

> 本文档记录 ZhiYu-Backend 的系统架构视图、关键流程、部署拓扑和安全架构。架构决策记录已独立为 [ADR.md](ADR.md)。

## 1. 系统上下文 (C4 Level 1)

> **当前部署（Phase 1）：** kubeadm 单节点 K8s @ 10.211.55.4，MySQL/Redis/Nacos 以 StatefulSet/Deployment 运行在 K8s 内。下图展示**目标架构**（阿里云 ACK + RDS + Sentinel + OSS），当前尚未实施。Prometheus + Grafana 已部署；Loki/OSS/支付/推送等外部服务未接入。

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
│  │                        K8s Cluster (目标: ACK, 当前: kubeadm)          │    │
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
│ (K8s内)│ │(K8s内)│ │Registry│ │(未接入)      │ │(已部署)  │ │(未部署)   │
└────────┘ └──────┘ └────────┘ └──────────────┘ └──────────┘ └───────────┘
               │
     ┌─────────┼──────────────────────────────┐
     │         │                              │
┌────┴───┐ ┌───┴─────┐ ┌──────────┐ ┌───────┴──────┐
│ 微信    │ │ 支付宝   │ │Apple IAP │ │Google Play   │
│ Open    │ │ 支付     │ │StoreKit  │ │Billing       │
│ Platform│ │          │ │          │ │              │
│(未接入) │ │(未接入)  │ │(未接入)  │ │(未接入)      │
└─────────┘ └─────────┘ └──────────┘ └──────────────┘
     │
┌────┴─────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│阿里云    │ │阿里云 SMS │ │  APNs    │ │   FCM    │
│邮件推送   │ │短信服务  │ │Apple推送  │ │Google推送 │
│(未接入)  │ │(未接入)  │ │(未接入)  │ │(未接入)  │
└──────────┘ └──────────┘ └──────────┘ └──────────┘
```

**外部系统依赖：**

| 系统 | 用途 | 协议 | 容错策略 | 状态 |
|------|------|------|---------|:----:|
| MySQL 8.0 | 主数据库 | JDBC/HikariCP | 主从切换 + ProxySQL | K8s StatefulSet |
| Redis 7.x | 缓存/黑名单/配额 | Lettuce | Sentinel 自动故障转移 | K8s StatefulSet |
| Nacos | 配置中心 + 服务注册 | gRPC/HTTP | 本地快照缓存降级 | K8s Deployment |
| Prometheus + Grafana | 监控告警 | HTTP scrape | — | 已部署 |
| Aliyun OSS | 对象存储（头像/导出） | HTTPS | 重试 3 次 + 指数退避 | 未接入 |
| 微信 Open Platform | OAuth 登录 | HTTPS | 超时 5s + 重试 1 次 | 未接入 |
| 支付宝 | 支付下单/回调 | HTTPS | 回调排队 + 定时查单补偿 | 未接入 |
| Apple StoreKit | IAP 票据验证 | HTTPS | 重试 3 次 | 未接入 |
| Google Play Billing | 票据验证 | HTTPS | 重试 3 次 | 未接入 |
| 阿里云邮件推送 | 验证码/通知邮件 | SMTP/API | 异步队列 + 重试 | 未接入 |
| 阿里云 SMS | 短信验证码 | HTTPS | 异步队列 + 重试 | 未接入 |
| APNs | iOS 推送 | HTTP/2 | 失败标记无效 token | 未接入 |
| FCM / 个推 | Android 推送 | HTTP/2 | 失败标记无效 token | 未接入 |
| Loki | 日志聚合 | gRPC push | 本地缓冲 50MB | 未部署 |

---

## 2. 4+1 架构视图

> 采用 Philippe Kruchten 的 4+1 模型组织架构视图，每节引用对应详述章节。

### 2.1 逻辑视图 — 功能分解

系统按领域边界垂直切分为模块，按职责水平分层：

```
用户端 (iOS/Android/Web)         管理端 (React SPA)
        │                               │
   ┌────┴───────────────────────────────┴────┐
   │          zhiyu-server (装配入口)          │
   └────┬──────┬──────┬──────┬──────────────┘
        │      │      │      │
   ┌────┴──┐┌──┴──┐┌──┴──┐┌──┴────┐
   │admin ││sub  ││user ││auth   │
   └──┬───┘└──┬──┘└──┬─┘└──┬────┘
      └───────┴──────┴──────┘
              │
        ┌─────┴──────────┐
        │ zhiyu-common    │  (MyBatis-Plus/Redis 业务公共配置)
        └─────┬──────────┘
              │
        ┌─────┴──────────┐
        │ ufp-common      │  (工具/异常/Filter/DTO/i18n)
        └────────────────┘
              ↑
        ┌─────┴──────────┐
        │ ufp-auth        │  (JWT/BCrypt/TOTP/WebAuthn，library jar)
        └────────────────┘
```

| 模块 | 领域职责 | 数据库 |
|------|---------|--------|
| `zhiyu-auth` | 注册/登录/验证码/密码重置/OAuth Provider实现/TOTP(P1)/WebAuthn(P1)/设备管理(P1) | `zhiyu` + `ufp_auth` |
| `zhiyu-user` | 用户资料管理：个人信息/偏好/注销（纯资料，不含认证） | `zhiyu` |
| `zhiyu-notification` | 通知模块：邮件/SMS/Push 统一发送（Phase 1） | `zhiyu` |
| `zhiyu-subscription` | 套餐/订单/支付/配额/退款 | `zhiyu` |
| `zhiyu-admin` | 管理员认证/RBAC/用户管理/审计 | `zhiyu` + `ufp_auth` |
| `zhiyu-common` | MyBatis-Plus/Redis 业务公共配置 | — |
| `ufp-common` | 工具类/异常/Filter/DTO/i18n/缓存/验证码/校验/验证码抽象/审计/存储/FeatureFlag/幂等/事件/Provider注册/工厂/配置/API文档（零 ORM/缓存） | — |
| `ufp-auth` | 认证库：JWT/BCrypt/TOTP(P1)/WebAuthn(P1)/OAuth/Token黑名单/实体+Mapper+Service（library，含 MyBatis-Plus + Redis），Phase 2 独立部署 | `ufp_auth` |

> 详见 [§3 L0-L2 分层架构](#3-l0-l2-分层架构) 和 [§5 模块依赖关系](#5-模块依赖关系)。

### 2.2 进程视图 — 运行时交互

核心运行时流程：

| 流程 | 描述 | 详述 |
|------|------|------|
| 邮箱注册 | 验证码发送 → 注册 → JWT 签发 | [§6.1](#61-邮箱注册流程) |
| 第三方登录 | OAuth 授权 → identity 关联 → JWT 签发 | [§6.2](#62-第三方登录微信流程) |
| 支付下单 | 下单 → 第三方支付 → 回调幂等处理 | [§6.3](#63-支付下单回调流程) |
| Token 轮换 | Refresh Token 轮换 + 盗用检测 | [§6.5](#65-refresh-token-轮换与盗用检测) |
| Filter Chain | CORS → RateLimit → Log → IP Whitelist → JWT → Scope → ActionToken | [§8.1](#81-filter-chain-顺序) |

### 2.3 开发视图 — 代码组织

```
backend/
├── ufp/                       # UFP 平台模块
│   ├── ufp-common/            # 共享基础设施（工具/异常/Filter/DTO/i18n/缓存/验证码等，零ORM/缓存）
│   └── ufp-auth/              # 认证库：JWT/BCrypt/TOTP/WebAuthn/OAuth/实体+Mapper（library，含ORM+Redis，→ ufp-common）
├── zhiyu-common/              # 业务公共配置（MyBatis-Plus/Redis，→ ufp-common）
├── zhiyu-auth/                # 业务认证（注册/登录/验证码/密码重置/OAuth Provider实现/TOTP/WebAuthn/设备管理，→ ufp-auth + zhiyu-common）
├── zhiyu-user/                # 用户资料模块（profile/偏好/注销，纯资料不含认证）
├── zhiyu-notification/        # 通知模块（邮件/SMS/Push 统一发送）
├── zhiyu-subscription/        # 订阅支付模块
├── zhiyu-admin/               # 管理后台模块
└── zhiyu-server/              # Spring Boot 入口 + Flyway 迁移（Phase 1 含 ufp_auth 迁移）
```

单向依赖链：`server → admin → subscription → user → auth → zhiyu-common → ufp-common` 且 `auth → ufp-auth → ufp-common`，所有模块 → `zhiyu-notification`（通知发送）

> 详见 [§5 模块依赖关系](#5-模块依赖关系) 和 [ARCHITECTURE-UFP.md](ARCHITECTURE-UFP.md)。

### 2.4 物理视图 — 部署拓扑

| 环境 | 拓扑 | 详述 |
|------|------|------|
| kubeadm (当前) | 单节点 K8s，MySQL/Redis/Nacos 在 K8s 内 | [§7.1](#71-当前部署拓扑kubeadm-单节点--10211554) |
| ACK (目标) | 阿里云 ACK + RDS + Redis Sentinel + Nacos Cluster | [§7.2](#72-目标部署拓扑阿里云-ack规划中) |
| 网络通信 | 33 条通信路径（入口/路由/数据面/出站/监控） | [§7.3](#73-通信矩阵) |

### 2.5 场景视图 — 用例映射

核心用例按优先级分 P0/P1/P2，详见 [PRD.md](PRD.md)。关键场景的时序设计见 [§6](#6-关键流程时序)。

| 优先级 | 用例 |
|:---:|------|
| P0 | 密码注册/登录、邮箱验证码、JWT 签发/刷新、订阅购买、支付回调 |
| P1 | 第三方 OAuth 登录、TOTP 2FA、WebAuthn 通行密钥、管理后台 RBAC |
| P2 | 社交功能、内容推荐、AI 功能 |

---

## 3. L0-L2 分层架构

### 3.1 L0 — 系统级

```
┌─────────────────────────────────────────────────────────────┐
│                      ZhiYu Backend System                    │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ 微信平台  │  │ 支付宝    │  │ Apple    │  │ Google   │   │
│  │ OAuth/支付│  │ 支付     │  │ StoreKit │  │ Play     │   │
│  └─────┬────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘   │
│        └─────────────┴─────────────┴─────────────┘         │
│                          │ 外部 API                          │
└──────────────────────────┼──────────────────────────────────┘
                           │
              ┌────────────┴────────────┐
              │    ZhiYu Backend        │
              │    (Spring Boot 3.3.x)  │
              └────────────┬────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
   ┌────┴────┐       ┌─────┴─────┐      ┌────┴────┐
   │ MySQL   │       │  Redis    │      │ Nacos   │
   │ 8.0     │       │  7.x      │      │ 2.x     │
   └────┬────┘       └───────────┘      └─────────┘
        │
   ┌────┴────────┐
   │             │
┌──┴──┐    ┌────┴────┐
│zhiyu│    │ufp_auth │
│业务库│    │ 认证库   │
└─────┘    │ 23 张表  │
           └─────────┘
```

### 3.2 L1 — 容器级

```
┌─────────────────────────────────────────────────────────────┐
│                     Container View                           │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │   zhiyu-server (Spring Boot 单体)                      │   │
│  │                                                       │   │
│  │  ┌─────────────────┐                                  │   │
│  │  │ Gateway (嵌入式)  │                                  │   │
│  │  │ Filter Chain     │                                  │   │
│  │  └─────────────────┘                                  │   │
│  │                                                       │   │
│  │  业务模块组装：zhiyu-auth 依赖 ufp-auth (library jar)    │   │
│  │  zhiyu 库为主, ufp_auth 库为认证数据                     │   │
│  └──────────┬────────────────────────────────────────────┘   │
│             │                                                │
│             │  单进程，非远程调用                               │
│             │                                                │
│  ┌──────────┴──────────────────────────────────────────┐     │
│  │   MySQL 8.0 (同实例)                                  │     │
│  │  ┌──────┐ ┌────────┐                                 │     │
│  │  │zhiyu │ │ufp_auth│                                 │     │
│  │  └──────┘ └────────┘                                 │     │
│  └──────────────────────────────────────────────────────┘     │
│                                                             │
│  当前状态：zhiyu-server 单体，ufp-auth 作为 library 嵌入       │
│  目标状态：ufp-auth 独立部署为认证微服务，zhiyu-server 通过      │
│           Feign client 调用                                   │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 L2 — 模块级

```
┌─────────────────────────────────────────────────────────────┐
│                     Module View                               │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                   zhiyu-server                         │   │
│  │              (Spring Boot 入口 + Flyway)               │   │
│  └────┬──────────┬──────────┬──────────┬────────────────┘   │
│       │          │          │          │                     │
│  ┌────┴────┐ ┌───┴───┐ ┌───┴───┐ ┌───┴──────┐              │
│  │ admin   │ │ sub   │ │ user  │ │ auth     │              │
│  │ RBAC    │ │ 支付   │ │ 个人  │ │ 登录     │              │
│  │ 审计    │ │ 套餐   │ │ 设备  │ │ Token    │              │
│  └────┬────┘ └──┬────┘ └──┬───┘ └──┬───────┘              │
│       └─────────┴─────────┴────────┘                       │
│                     │                                       │
│              ┌──────┴──────────┐                            │
│              │  zhiyu-common    │  (MyBatis-Plus/Redis)      │
│              └──────┬──────────┘                            │
│                     │                                       │
│              ┌──────┴──────────┐                            │
│              │  ufp-common      │  (工具/异常/Filter/i18n)    │
│              └─────────────────┘                            │
│                     ↑                                       │
│              ┌──────┴──────────┐                            │
│              │  ufp-auth        │  (JWT/BCrypt/TOTP/        │
│              │  (library jar)   │   WebAuthn/Token黑名单)    │
│              └─────────────────┘                            │
│                                                             │
│  数据库双库：zhiyu（业务） + ufp_auth（认证），同 MySQL 实例     │
│  跨库引用：zhiyu.*.user_id → ufp_auth.auth_user.auth_user_id │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. 整体架构视图

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ZhiYu-Backend 全景架构                         │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                    业务层 (zhiyu-*)                             │ │
│  │                                                               │ │
│  │  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐  │ │
│  │  │  admin   │──▶│   sub    │──▶│   user   │──▶│   auth   │  │ │
│  │  │ 管理后台  │   │ 订阅支付  │   │ 用户中心  │   │ 业务认证  │  │ │
│  │  │ RBAC/审计 │   │ 套餐/配额 │   │ 设备/TOTP│   │ 登录/Token│  │ │
│  │  └────┬─────┘   └──────────┘   └──────────┘   └────┬─────┘  │ │
│  │       │                                             │        │ │
│  └───────┼─────────────────────────────────────────────┼────────┘ │
│          │                                             │          │
│          │              ┌──────────────────────────────┘          │
│          │              │                                         │
│  ┌───────┴──────────────┴──────────────────────────────────┐     │
│  │              基础设施层 (UFP)                               │     │
│  │                                                          │     │
│  │  ┌──────────────────────┐    ┌──────────────────────┐   │     │
│  │  │    ufp-common              │    │    ufp-auth                │   │     │
│  │  │    共享基础设施（零ORM/缓存） │    │    认证库（library，含ORM+Redis） │   │     │
│  │  │                           │    │                           │   │     │
│  │  │  • 工具类 + 异常           │    │  • JWT 签发/验签           │   │     │
│  │  │  • 安全 Filter (JWT/CORS) │    │  • BCrypt 密码哈希         │   │     │
│  │  │  • 通用 DTO + 枚举        │    │  • TOTP / WebAuthn         │   │     │
│  │  │  • i18n 国际化            │    │  • OAuth Provider 抽象     │   │     │
│  │  │  • Caffeine 本地缓存      │    │  • Token 黑名单 + 轮换     │   │     │
│  │  │  • 图片验证码生成          │    │  • 登录尝试追踪            │   │     │
│  │  │  • 通用校验注解            │    │  • 实体+Mapper+Service     │   │     │
│  │  │  • 验证码/审计/存储等抽象  │    │  • ufp_auth 库 23 张表     │   │     │
│  │  │  • 领域事件 + Provider注册│    │                           │   │     │
│  │  │  • API 文档（springdoc）   │    │                           │   │     │
│  │  └──────────────────────┘    └──────────────────────┘   │     │
│  └──────────────────────────────────────────────────────────┘     │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    数据层                                      │  │
│  │                                                              │  │
│  │  ┌──────────────────────┐    ┌──────────────────────┐       │  │
│  │  │  zhiyu (业务库)       │    │  ufp_auth (认证库)    │       │  │
│  │  │                      │    │                      │       │  │
│  │  │  • user_profile      │───▶│  • auth_user          │       │  │
│  │  │  • user_subscription │ FK │  • auth_role          │       │  │
│  │  │  • subscription_plan │    │  • auth_res           │       │  │
│  │  │  • subscription_order│    │  • auth_grant         │       │  │
│  │  │  • payment_record    │    │  • auth_operation_log │       │  │
│  │  │  • outbox_event      │    │  • auth_user_identity │       │  │
│  │  │  • notification_     │    │  • auth_user_totp     │       │  │
│  │  │    template           │    │  • auth_user_web_authn│       │  │
│  │  │  同 MySQL 8.0 实例    │    │  • auth_login_attempt │       │  │
│  │  └──────────────────────┘    │  • auth_user_device   │       │  │
│  │                               └──────────────────────┘       │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  外部系统：微信/支付宝/Apple/Google 支付 · OAuth 登录                    │
│           阿里云 SMS/邮件 · APNs/FCM 推送 · OSS 对象存储               │
└─────────────────────────────────────────────────────────────────────┘
```

**关键设计要点：**

| 层 | 组件 | 职责 | 状态 |
|---|------|------|:---:|
| 业务层 | `zhiyu-*` (5 模块) | 业务逻辑，依赖 zhiyu-common + ufp-auth | 当前 |
| 业务公共层 | `zhiyu-common` | MyBatis-Plus/Redis 配置，依赖 ufp-common | 当前 |
| 基础设施层 | `ufp-common` | 工具/异常/Filter/DTO/i18n/缓存/验证码/校验/多数据源等（零 ORM/缓存） | 当前 |
| 认证库层 | `ufp-auth` | JWT/BCrypt/TOTP/WebAuthn/OAuth（library jar，含 MyBatis-Plus + Redis），管理 ufp_auth 库 23 表 | 当前 |
| 通知层 | `zhiyu-notification` | 邮件/SMS/Push 统一发送（Phase 1 封装 SDK，Phase 2 提取接口至 UFP） | P1 |
| 数据层 | `zhiyu` + `ufp_auth` | 业务库 + 认证库，同 MySQL，跨库 FK | 当前 |
| 入口 | `zhiyu-server` | Spring Boot 装配 + Flyway 迁移 | 当前 |

### 4.1 技术选型总览

#### 业务层（zhiyu-*）

| 关注面 | 组件 | 版本 | 用途 |
|--------|------|------|------|
| ORM | MyBatis-Plus (`mybatis-plus-spring-boot3-starter`) | 3.5.10 | CRUD、分页、乐观锁、逻辑删除、自动填充 |
| 数据库迁移 | Flyway (`flyway-core`) | 10.18.2 | 版本化 SQL 迁移（zhiyu-server 入口） |
| 缓存 | `spring-boot-starter-data-redis` (Lettuce) | — | 验证码、Token 黑名单、幂等去重、配额计数 |
| 流控 | Sentinel (`spring-cloud-alibaba-sentinel`) | 2023.0.1.0 | [规划中] QPS/热点参数/熔断降级，未添加依赖 |
| 配置中心 | Nacos (`spring-cloud-alibaba-nacos-config`) | 2.x | 配置热更新 + 服务注册 |
| 支付 | `wechatpay-java` | 0.4.18 | 微信统一下单、回调验签 |
| | `alipay-sdk-java` | 4.39.152 | 支付宝下单、回调验证 |
| | (自研封装) | — | Apple IAP / Google Play 票据验证 |
| 消息 | `spring-boot-starter-mail` | — | 邮件验证码/通知 |
| | `aliyun-sms-sdk` | 2.0.24 | 短信验证码 |
| | (待引入) FCM/APNs/个推 | — | App 推送通知 |
| 存储 | `aliyun-sdk-oss` | 3.17.4 | 头像/文件对象存储 |
| 转换 | MapStruct (`mapstruct`) | 1.6.3 | Entity ↔ Resp DTO |
| 注解 | Lombok (`lombok`) | 1.18.36 | `@RequiredArgsConstructor` / `@Slf4j` |

#### 基础设施层（UFP）

| 关注面 | 组件 | 版本 | 用途 |
|--------|------|------|------|
| 工具库 | Hutool (`hutool-all`) | 5.8.35 | 集合/日期/加密/Bean/IO/网络 |
| | Commons Lang3 (`commons-lang3`) | 3.17.0 | 字符串/反射/Builder |
| 本地缓存 | Caffeine | (Spring Boot 托管) | Filter 层本地缓存、LRU |
| JWT | `jjwt-api/impl/jackson` | 0.12.6 | RS256 签发/验签 |
| 密码 | `spring-security-crypto` (BCrypt) | — | 密码哈希（strength=12） |
| TOTP | `aerogear-otp-java` | 1.0+ | 双因素认证（HMAC-SHA1） |
| WebAuthn | `yubico:webauthn-server-core` | — | FIDO2 通行密钥 |
| 验证码 | Hutool `ImgUtil` + `RandomUtil` | — | 图片验证码生成 |
| 校验 | `spring-boot-starter-validation` | — | JSR-303 Bean Validation |
| 文档 | `springdoc-openapi-starter-webmvc-ui` | — | Swagger UI + 全局 API 文档配置 |

详细依赖边界见 [ARCHITECTURE-UFP.md](ARCHITECTURE-UFP.md)。

---

## 5. 模块依赖关系

```
                    zhiyu-server
                   (入口 + 装配)
                   /    |    \    \
                  /     |     \    \
         zhiyu-admin  zhiyu-subscription  zhiyu-notification
              |              |              (所有业务模块 →)
         zhiyu-user    zhiyu-user
              |              |
         zhiyu-auth    zhiyu-auth    (注册/登录/TOTP/WebAuthn/设备)
              \         /
           zhiyu-common     ufp-auth
              |                |
           ufp-common     ufp-common
         (工具/Filter/DTO/i18n/多数据源等)
```

**依赖规则：**
- `ufp-common`：零业务依赖，纯基础设施（工具/异常/Filter/DTO/i18n/缓存/验证码/校验/多数据源路由等）
- `ufp-auth`：认证库（library jar），提供 JWT/BCrypt/TOTP(P1)/WebAuthn(P1)/OAuth/实体+Mapper+Service，含 MyBatis-Plus + Redis，依赖 ufp-common
- `zhiyu-common`：ZhiYu 业务公共配置（MyBatis-Plus/Redis），依赖 ufp-common
- `zhiyu-auth`：业务认证领域（注册/登录/验证码/密码重置/TOTP(P1)/WebAuthn(P1)/设备管理(P1)），依赖 ufp-auth + zhiyu-common
- `zhiyu-user`：纯用户资料管理（profile/偏好/注销），依赖 zhiyu-auth + zhiyu-common
- `zhiyu-notification`：通知模块（邮件/SMS/Push 统一发送），依赖 zhiyu-common。所有业务模块可注入使用
- `zhiyu-subscription` → `zhiyu-user` + `zhiyu-common`：订阅需要用户信息
- `zhiyu-admin` → `zhiyu-subscription` + `zhiyu-user` + `zhiyu-common`：管理后台可操作所有领域
- `zhiyu-server` → 所有模块：仅做装配和启动

**跨模块通信：**
- 仅通过 Service 接口注入（上层注入下层 Service）
- 禁止跨模块直接注入 Mapper
- 禁止循环依赖（maven-enforcer-plugin 检查）

---

## 6. 关键流程时序

### 6.1 邮箱注册流程

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

### 6.2 第三方登录（微信）流程

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
  │                │               ├──查询 auth_user_identity───▶│
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

### 6.3 支付下单+回调流程

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

### 6.4 密码重置流程

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

### 6.5 Refresh Token 轮换与盗用检测

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

## 7. 部署架构

### 7.1 当前部署拓扑（kubeadm 单节点 @ 10.211.55.4）

```
                              ┌──────────────────────┐
                              │  kubeadm 单节点       │
                              │  10.211.55.4          │
                              └──────────┬───────────┘
                                         │
                              ┌──────────┴───────────┐
                              │  NodePort / Ingress   │
                              │  (31340 / 31341)     │
                              └──────────┬───────────┘
                                         │
                    ┌────────────────────┼────────────────────┐
                    │                    │                    │
              ┌─────┴─────┐       ┌─────┴─────┐       ┌─────┴─────┐
              │ MySQL     │       │ Redis     │       │ Nacos     │
              │ (STS)     │       │ (STS)     │       │ (Deploy)  │
              │ 1 副本    │       │ 1 副本    │       │ 1 副本    │
              └───────────┘       └───────────┘       └───────────┘
                                         │
                              ┌──────────┴───────────┐
                              │  zhiyu-backend        │
                              │  (Deployment, 1 副本) │
                              │  Spring Boot + Gateway│
                              │  (嵌入式 Gateway)     │
                              └──────────┬───────────┘
                                         │
                              ┌──────────┴───────────┐
                              │  Prometheus + Grafana │
                              │  (监控栈, NodePort)   │
                              └──────────────────────┘
```

> 部署方式：`deploy/deploy-to-remote.sh` → rsync JAR + 镜像 → SSH 远程执行 `deploy.sh kubeadm all`。
> CI/CD：Woodpecker CI 在 Mac 本机 Docker 中运行，通过 SSH 触发远端部署。

### 7.2 目标部署拓扑（阿里云 ACK，规划中）

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

> **Phase 1 部署策略**：Spring Cloud Gateway 作为嵌入式库（`spring-cloud-starter-gateway`）运行在 zhiyu-backend 应用内部，不单独部署。上图中 Gateway 展示的是**逻辑分层**，物理上 Ingress 直接路由到 `Service: zhiyu-backend`。Gateway Filter Chain 在应用进程内执行（见 [§8 安全架构](#8-安全架构)）。

### 7.3 环境划分

| 环境 | 用途 | 副本数 | 资源/Pod | 数据库 | CI 触发 | 状态 |
|------|------|:----:|:-------:|--------|:------:|:----:|
| `kubeadm` | 本地开发 + 部署 | 1 | 1C/2Gi | K8s StatefulSet | main push (Woodpecker) | **当前** |
| `dev` (ACK) | 开发测试 | 1 | 1C/1Gi | K8s 共享实例 | 手动 | 规划 |
| `test` (ACK) | 集成测试 + E2E | 1 | 2C/2Gi | Testcontainers | PR 自动 | 规划 |
| `staging` (ACK) | 预发布验证 | 2 | 2C/4Gi | RDS 独立实例 | main 分支自动 | 规划 |
| `release` (ACK) | 生产环境 | 4+ | 2C/4Gi | RDS (主从) | Release 分支手动 | 规划 |

### 7.4 通信矩阵

下表列出系统中所有组件间的网络通信路径，用于网络策略配置、安全审计和故障排查。

#### 7.4.1 入口流量（外部 → 集群）

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 1 | 公网 (客户端/浏览器) | Ingress Controller | HTTPS | 443 | 入站 | 用户 API 请求、管理后台 |
| 2 | 公网 | Ingress Controller | HTTP | 80 | 入站 | HTTP→HTTPS 重定向（生产环境关闭） |

#### 7.4.2 集群路由（Ingress → 应用）

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 3 | Ingress Controller | `Service: zhiyu-backend` | HTTP | 8080 | → | API 路由 (`/api/v1`, `/actuator/health`) |
| 4 | Ingress Controller | `Service: zhiyu-backend` | HTTP | 8080 | → | Admin API (`/admin/*`，需 IP 白名单) |

#### 7.4.3 应用 → 基础设施（数据平面）

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 5 | Backend Pod (zhiyu-backend) | MySQL (RDS 或 StatefulSet) | TCP | 3306 | → | 业务数据持久化（HikariCP 连接池，max 20/Pod） |
| 6 | Backend Pod | Redis (Sentinel 或 StatefulSet) | TCP | 6379 | → | 缓存 + Token 黑名单 + 分布式锁 |
| 7 | Backend Pod | Nacos Server | HTTP | 8848 | → | 配置拉取 (`dataId` 订阅)、服务注册 |

#### 7.4.4 应用 → 基础设施（服务发现与 RPC）

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 8 | Backend Pod | Nacos Server | gRPC | 9848 | → | 服务注册/心跳、配置变更长轮询 (Nacos 2.x gRPC) |
| 9 | Backend Pod (Sentinel) | Sentinel Dashboard | HTTP | 8080 | ← | 流控规则推送（Dashboard→客户端，可选） |

#### 7.4.5 Init 容器依赖检查

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 10 | `init: wait-for-mysql` | MySQL | TCP | 3306 | → | `nc -z` 端口探测，确认 MySQL 监听后再启动 |
| 11 | `init: wait-for-nacos` | Nacos | HTTP | 8848 | → | `wget` 健康检查端点，确认 Nacos 就绪（可跳过） |

#### 7.4.6 外部出站（第三方 API）

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

#### 7.4.7 监控平面

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 20 | Prometheus | Backend Pod | HTTP | 8080 | → | 抓取 `/actuator/prometheus`（JVM + 业务指标） |
| 21 | Prometheus | node-exporter (DaemonSet) | HTTP | 9100 | → | 宿主机 CPU/内存/磁盘/网络指标 |
| 22 | Prometheus | kube-state-metrics | HTTP | 8080 | → | K8s 对象状态指标（Pod/Deploy/STS） |
| 23 | Prometheus | kube-state-metrics (telemetry) | HTTP | 8081 | → | kube-state-metrics 自身指标 |
| 24 | Grafana | Prometheus | HTTP | 9090 | → | 数据源查询（看板渲染） |
| 25 | 运维浏览器 | Grafana Service | HTTP | 30000 | 入站 | Grafana Web UI (NodePort) |

#### 7.4.8 Nacos 集群间通信（仅集群模式，非 standalone）

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 26 | Nacos Peer | Nacos Peer | gRPC | 9848 | ↔ | 服务注册表同步 (Distro 协议) |
| 27 | Nacos Peer | Nacos Peer | TCP | 7848 | ↔ | Raft 一致性协议 (CP 模式配置持久化) |

#### 7.4.9 Redis Sentinel 通信（仅生产环境，dev/staging 用单实例）

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 28 | Backend Pod (Lettuce) | Redis Sentinel | TCP | 26379 | → | 哨兵拓扑发现、主从切换通知 |

#### 7.4.10 K8s 控制平面（集群运维）

| # | 来源 | 目标 | 协议 | 端口 | 方向 | 用途 |
|---|------|------|:----:|:----:|:----:|------|
| 29 | kubelet / kubectl / Controller | API Server | HTTPS | 6443 | → | K8s API 入口 |
| 30 | API Server | etcd | HTTPS | 2379-2380 | → | 集群状态持久化 |
| 31 | API Server | kubelet | HTTPS | 10250 | → | Pod 日志/exec/指标 |
| 32 | Pod / Service | CoreDNS | UDP/TCP | 53 | → | 集群内 DNS 解析 |
| 33 | Node (Flannel) | Node (Flannel) | UDP | 8472 | ↔ | VXLAN 跨节点 Pod 网络 (Flannel) |

> **网络策略实施**：上表中标记 `入站` 的端口需在 Security Group / NetworkPolicy 中放行。标记 `→` 的方向为 Pod 出站流量，默认允许（K8s 默认出站放行），NetworkPolicy 可选约束。外部出站流量（#12-19）需确认 NAT 网关白名单包含相应域名。

---

## 8. 安全架构

### 8.1 Filter Chain 顺序

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

### 8.2 RBAC 权限模型

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

### 8.3 Token 流转全链路

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

## 相关文档

- [ADR.md](ADR.md) — 架构决策记录（ADR-001 ~ ADR-013）
- [ARCHITECTURE-UFP.md](ARCHITECTURE-UFP.md) — UFP 平台模块架构设计
- [PRD.md](PRD.md) — 产品需求文档
- [DATABASE.md](DATABASE.md) — 数据库设计（DDL、ER 关系、索引）
- [DEVELOPMENT-STANDARDS.md](../dev-test/DEVELOPMENT-STANDARDS.md) — 编码规范
- [SECURITY.md](../dev-test/SECURITY.md) — 安全测试与合规
