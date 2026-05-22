# UFP 模块架构设计

> 本文档定义 UFP 统一基础平台的模块架构，基于 NTA 项目分析精简适配。
> 详细分析见附录引用的原始参考文档。Phase 1 模块（ufp-common / ufp-auth）已创建并投入使用。

## 1. 概述

UFP（Unified Foundation Platform）是基础设施层，提供认证授权、共享工具库、安全 Filter 等横切能力，供业务模块依赖。

**设计原则**：
- 单向依赖，无循环引用
- 与业务模块解耦，Phase 1 作为 library jar 嵌入
- 优先使用 Hutool/Commons Lang3 等成熟库，不重复造轮子
- 不加租户、不加用户域/权限域、不加 CAS IdSP

## 2. 模块全景

### 2.1 Phase 1（当前 — 单体嵌入）

```
ufp/
├── ufp-common/          # 平台基础设施（单模块，包级分层）
│   └── src/main/java/com/ufp/common/
│       ├── core/        # R<T> 响应模型、PageResult、BusinessException
│       ├── util/        # Hutool/Commons Lang3 封装补充
│       ├── filter/      # JWT Filter、CORS Filter、IP 白名单 Filter、RequestLog Filter
│       ├── dto/         # 通用 DTO（分页请求、校验分组）
│       ├── enums/       # 通用枚举（启用状态、性别等）
│       ├── i18n/        # 国际化资源（messages.properties + messages_zh_CN.properties）
│       ├── captcha/     # 图片验证码生成（Hutool ImgUtil + RandomUtil）
│       ├── validation/  # 通用校验注解（@StrongPassword, @PhoneNumber）
│       │
│       ├── verify/      # [DI+模板方法] 验证码发送/校验抽象
│       ├── audit/       # [AOP+DI] 审计日志抽象
│       ├── storage/     # [DI+适配器] 文件存储抽象
│       ├── feature/     # [AOP+DI] Feature Flag 抽象
│       ├── idempotent/  # [AOP+DI] 幂等控制抽象
│       │
│       ├── event/       # [事件驱动] UfpEvent 基类 + Publisher
│       ├── registry/    # [注册表] ProviderRegistry<T> 泛型注册/查找
│       ├── factory/     # [工厂] Token 工厂、凭证工厂
│       ├── datasource/  # [注解] @UfpDS 数据源标记注解 + UfpDSContextHolder (纯 Java，零框架依赖)
│       └── config/      # [配置属性] @ConfigurationProperties 聚合配置
│
└── ufp-auth/            # 认证库（library jar，依赖 ufp-common）
    └── src/main/java/com/zhiyu/ufp/auth/
        ├── jwt/         # JWT RS256 签发/验签/TokenService
        ├── password/    # BCrypt 哈希/密码历史/强度校验
        ├── totp/        # TOTP 双因素认证（aerogear-otp-java）
        ├── webauthn/    # WebAuthn 通行密钥（Yubico）
        ├── oauth/       # [DI+注册表+适配器] OAuth 第三方登录抽象（IOAuthProvider）
        ├── token/       # Token 黑名单/Refresh Token 轮换
        ├── event/       # [事件驱动] AuthEvent（登录/注册/登出/密码修改）
        ├── model/       # AuthUser/AuthRole/AuthRes/AuthGrant/AuthOrg 实体与 Mapper
        ├── service/     # IAuthUserService/IAuthRoleService 等
        └── config/      # JWT 密钥（RS256 KeyPair）+ BCrypt strength=12
```

包名旁标注 `[模式]` 表示该包采用的设计模式，详见 [§3.6 抽象能力设计模式](#36-抽象能力设计模式)。

### 2.2 Phase 2（微服务拆分后 — 子模块细化）

ufp-common 拆分为独立子模块（`ufp-common-core` / `ufp-common-util` / `ufp-common-captcha` / `ufp-common-datasource` / `ufp-common-autoconfigure` 等），ufp-auth 拆为 3 层（`ufp-auth-api` / `ufp-auth-client` / `ufp-auth-server`）。详细规划见 [附录：原始参考文档](#附录原始参考文档)。

Flyway 迁移拆分：Phase 2 时 `ufp-auth-server` 自带 `db/migration/`，仅包含 V1.4.x/V1.5.x (ufp_auth 表)，zhiyu-server 保留 V1.0.x-V1.3.x (zhiyu 业务表)。Phase 1 共用 zhiyu-server Flyway。

## 3. ufp-common — 平台基础设施

### 3.1 包分层设计（Phase 1 单模块）

| 包 | 设计模式 | 职责 | 关键内容 |
|------|:---:|------|---------|
| `com.ufp.common.core` | 模板方法 | 基石类型，零业务依赖 | `R<T>` 响应模型、`PageResult`、`BusinessException`、`@UfpController` |
| `com.ufp.common.util` | 策略 | 通用工具补充 | Hutool/Commons Lang3 封装补充，仅封装项目特有的工具 |
| `com.ufp.common.filter` | 模板方法 | 安全 Filter Chain | `JwtAuthFilter`、`CorsFilter`、`IpWhitelistFilter`、`RequestLogFilter`（traceId MDC） |
| `com.ufp.common.dto` | 适配器 | 通用 DTO | 分页请求/响应、通用枚举值 DTO、校验分组 |
| `com.ufp.common.enums` | 配置 | 通用枚举 | 启用状态、性别、平台类型等 |
| `com.ufp.common.i18n` | 配置 | 国际化 | `messages.properties`（英文兜底）+ `messages_zh_CN.properties`，错误码枚举 |
| `com.ufp.common.captcha` | 策略 | 图片验证码生成 | Hutool `ImgUtil` + `RandomUtil`，可配置字符长度/尺寸/背景色 |
| `com.ufp.common.validation` | 回调 | 通用校验注解 | `@StrongPassword`、`@PhoneNumber` + 自定义规则链注入 |
| `com.ufp.common.verify` | DI + 模板方法 | 验证码抽象 | `IVerifyCodeSender` + `AbstractVerifyCodeService`（发送/校验/频率/TTL 骨架） |
| `com.ufp.common.audit` | AOP + DI | 审计日志抽象 | `@AuditLog` 注解 + `AbstractAuditAspect` + `IAuditLogWriter` |
| `com.ufp.common.storage` | DI + 适配器 | 文件存储抽象 | `IFileStorage`（upload/download/delete/generateUrl）+ 统一 `FileInfo` |
| `com.ufp.common.feature` | AOP + DI | Feature Flag | `IFeatureFlagStore` + `@RequiresFeature` 注解 + AOP 拦截器 |
| `com.ufp.common.idempotent` | AOP + DI | 幂等控制 | `IIdempotentStore` + `@Idempotent` 注解 + AOP 拦截器 |
| `com.ufp.common.event` | 事件驱动 | 领域事件 | `UfpEvent` 基类 + `UfpEventPublisher`（用户注册/登录/登出/密码修改） |
| `com.ufp.common.registry` | 注册表 | Provider 管理 | `ProviderRegistry<T>` 泛型注册/查找/列表 |
| `com.ufp.common.factory` | 工厂 | 延迟创建 | `ITokenFactory`、`ICredentialFactory` |
| `com.ufp.common.datasource` | 注解 | 数据源标记 | `@UfpDS` 注解 + `UfpDSContextHolder` (ThreadLocal)，纯 Java 零依赖 |
| `com.ufp.common.config` | 配置属性 | 配置聚合 | `@ConfigurationProperties("ufp")` 聚合 JWT/Captcha/Password/DataSource 配置 |

### 3.2 依赖清单（ufp-common）

| 依赖 | 用途 | 作用域 |
|------|------|:---:|
| `spring-boot-starter-web` | Filter、DTO 校验、全局异常处理、响应包装 | compile |
| `spring-boot-starter-validation` | JSR-303 参数校验（Hibernate Validator） | compile |
| `spring-boot-starter-actuator` | 健康检查 + Micrometer 指标暴露 | compile |
| `hutool-all` 5.8.35 | 集合/日期/加密/Bean/IO/网络 | compile |
| `commons-lang3` 3.17.0 | 字符串/反射/Builder | compile |
| `lombok` 1.18.36 | `@RequiredArgsConstructor` / `@Slf4j` | provided |
| `mapstruct` 1.6.3 | Entity ↔ DTO 转换接口定义 | compile |

> **注意**：JWT、BCrypt、TOTP、WebAuthn 等认证相关依赖归属 `ufp-auth`（见 [§4.2](#42-技术选型)）。`ufp-common` 仅包含通用基础设施依赖，不含 ORM（MyBatis-Plus）和远程缓存（Redis）。

### 3.3 工具类策略

优先使用 Hutool 和 Commons Lang3 已有功能，仅在以下情况手写工具类：
- 项目特有的领域封装（如 JWT claims 提取、i18n 错误码格式化）
- Hutool/Commons Lang3 不覆盖的功能
- 需要在两个库之间做适配桥接

### 3.4 ufp-common 不引入的内容

| 类型 | 说明 | 归属 |
|------|------|------|
| MyBatis-Plus / ORM | 数据库访问 | `ufp-auth`（认证实体）+ `zhiyu-common`（业务实体） |
| Redis / 缓存中间件 | 远程缓存/分布式锁 | `ufp-auth`（Token 黑名单）+ `zhiyu-common`（业务缓存） |
| 支付 SDK | 微信/支付宝/Apple/Google | 各自 `zhiyu-*` 模块（Phase 2 → `ufp-integration-payment`） |
| 消息/推送 SDK | 短信/邮件/FCM/APNs | `zhiyu-*` 模块（Phase 2 → `ufp-integration-notification`） |
| Nacos / Sentinel | 配置中心/流控 | `zhiyu-server` 入口 |
| JWT / BCrypt / TOTP / WebAuthn | 认证相关库 | `ufp-auth` |

### 3.5 抽象能力设计模式

UFP 使用 10 种设计模式将通用能力抽象为接口层，ZhiYu 通过 DI / 回调 / 事件订阅 / 注解提供具体实现。

| # | 模式 | UFP 提供 | ZhiYu 提供 | 适用场景 |
|:---:|------|------|------|------|
| 1 | **DI / SPI** | 接口 | `@Component` 实现 | 渠道替换（邮件/短信/OAuth/存储） |
| 2 | **模板方法** | 抽象类 + 流程骨架 | 重写步骤方法 | 验证码校验、OAuth 授权标准流程 |
| 3 | **策略模式** | 策略接口 + 选择器 | 具体策略实现 | 加密算法、哈希强度、序列化格式 |
| 4 | **事件驱动** | `UfpEvent` 基类 + `UfpEventPublisher` | `@EventListener` | 注册→送套餐、密码修改→设备下线 |
| 5 | **回调 / 钩子** | `Function<T, R>` 钩子链 | `@Bean` 注入规则 | 密码强度规则、用户名合法性校验 |
| 6 | **AOP 注解驱动** | 注解 + 拦截器骨架 | 存储接口实现 | 审计日志、幂等控制、Feature Flag |
| 7 | **配置属性** | `@ConfigurationProperties` | `application.yml` 赋值 | JWT TTL、验证码长度、密码策略 |
| 8 | **注册表** | `ProviderRegistry<T>` 泛型注册/查找 | `@Component` 自动注册 | 支付渠道路由、通知渠道路由 |
| 9 | **适配器** | 标准化模型（`FileInfo`） | 转换第三方格式 | 各平台统一返回格式 |
| 10 | **工厂** | 工厂接口 | 具体创建逻辑 | Token 组装、凭证创建 |

**各模式与功能包映射：**

| 包 | 模式组合 | 关键接口/类 |
|------|------|------|
| `verify/` | DI + 模板方法 | `IVerifyCodeSender`, `AbstractVerifyCodeService` |
| `audit/` | AOP + DI | `@AuditLog`, `AbstractAuditAspect`, `IAuditLogWriter` |
| `storage/` | DI + 适配器 | `IFileStorage`, `FileInfo` |
| `feature/` | AOP + DI | `@RequiresFeature`, `IFeatureFlagStore` |
| `idempotent/` | AOP + DI | `@Idempotent`, `IIdempotentStore` |
| `event/` | 事件驱动 | `UfpEvent`, `UfpEventPublisher` |
| `registry/` | 注册表 | `ProviderRegistry<T>` |
| `factory/` | 工厂 | `ITokenFactory`, `ICredentialFactory` |
| `datasource/` | 注解 | `@UfpDS`, `UfpDSContextHolder` |
| `config/` | 配置属性 | `@ConfigurationProperties("ufp")` |
| `validation/` | 回调 | `Function<String, ValidationResult>` 规则链 |

**关键接口示例：**

```java
// 模板方法 — 验证码校验骨架
public abstract class AbstractVerifyCodeService {
    public VerifyResult verify(String target, String code) {
        if (!checkRateLimit(target)) return VerifyResult.RATE_LIMITED;  // 固定
        if (!doVerify(target, code)) {                                   // 子类
            recordFailure(target);                                       // 固定
            return VerifyResult.FAILED;
        }
        deleteCode(target);                                              // 固定
        return VerifyResult.SUCCESS;
    }
    protected abstract boolean doVerify(String target, String code);
}

// AOP — 幂等控制
@Idempotent(key = "#orderNo", ttl = 60)
public PaymentResult handleCallback(String orderNo, String body) { ... }

// 事件驱动 — 用户注册事件
eventPublisher.publish(new AuthEvent(userId, AuthEventType.REGISTERED));

// 注解 — 数据源标记（纯 Java 注解，消费模块自行实现 AOP 拦截）
@UfpDS("ufp_auth")
public AuthUser findUserByUsername(String username) { ... }
```

### 3.6 ufp-common-dict — 字典管理（P3）

> 运行时动态数据字典。业务层当前使用枚举类管理固定字典，完全满足需求。此模块仅在需要运行时动态管理字典项时启用。

| 字段 | 类型 | 说明 |
|------|------|------|
| `dict_category` | VARCHAR(50) | 分类 |
| `dict_code` | VARCHAR(100) | 编码 |
| `dict_label` | VARCHAR(200) | 显示名称 |
| `dict_value` | VARCHAR(500) | 值 |
| `dict_sort` | INT | 排序 |
| `dict_enable` | TINYINT | 启用 |

## 4. ufp-auth — 认证授权

> **当前状态（Phase 1）：** ufp-auth 作为 library jar 嵌入 zhiyu-server 单体，提供 JWT/BCrypt/TOTP/WebAuthn/Token 黑名单等平台级认证能力。zhiyu-auth（业务模块）依赖 ufp-auth 完成 ZhiYu 特有的注册/登录/OAuth 绑定流程。

### 4.1 包分层设计（Phase 1 library jar）

```
ufp-auth/src/main/java/com/zhiyu/ufp/auth/
├── jwt/             # JWT RS256 签发/验签/TokenService/RefreshToken 轮换
├── password/        # BCrypt 哈希/强度校验/密码历史（max 5）
├── totp/            # TOTP 双因素（aerogear-otp-java）
├── webauthn/        # WebAuthn 通行密钥（com.yubico:webauthn-server-core）
├── oauth/           # [DI+注册表+适配器] OAuth Provider 抽象（IOAuthProvider + ProviderRegistry + OAuthUserInfo）
├── token/           # Token 黑名单（Redis jti）+ 登录失败追踪
├── event/           # [事件驱动] AuthEvent（登录/注册/登出/密码修改）
├── model/           # AuthUser/AuthRole/AuthRes/AuthGrant/AuthOrg 实体+Mapper（MyBatis-Plus）
├── service/         # IAuthUserService/IAuthRoleService 等
└── config/          # JWT 密钥配置（RS256 KeyPair）+ BCrypt strength=12 + UfpAuthDataSourceConfig（多数据源自动配置）
```

### 4.2 技术选型

| 能力 | 选型 | 优先级 |
|------|------|:---:|
| JWT 签发/验签 | `jjwt` 0.12.6 + RS256 非对称签名（私钥签发，公钥验签） | P0 |
| 密码哈希 | `spring-security-crypto` BCrypt（strength=12） | P0 |
| ORM | MyBatis-Plus (`mybatis-plus-spring-boot3-starter`) 3.5.10 | P0 |
| 缓存 | `spring-boot-starter-data-redis` (Lettuce) — Token 黑名单/登录追踪 | P0 |
| 多数据源 | 自行实现 `AbstractRoutingDataSource` + AOP 切面，使用 `@UfpDS` 注解标记，绑定 `com.zhiyu.ufp.auth.model` → ufp_auth 库 | P0 |
| TOTP 双因素 | `aerogear-otp-java` 1.0+（HMAC-SHA1，Base32 密钥） | P1 |
| WebAuthn 通行密钥 | `com.yubico:webauthn-server-core`（FIDO2 标准） | P1 |
| OAuth 第三方登录 | `IOAuthProvider` + `ProviderRegistry`（接口在 ufp-auth，实现在 zhiyu-auth） | P1 |

> **依赖说明**：`ufp-auth` 作为数据密集的认证模块，允许直接依赖 MyBatis-Plus 和 Redis。`ufp-common` 保持零 ORM/缓存依赖，仅提供 `@UfpDS` 注解 + `UfpDSContextHolder`（纯 Java，零框架依赖）。Phase 2 `ufp-auth` 独立部署时，这些依赖无需变更。ufp-auth 自行实现 `AbstractRoutingDataSource` + AOP 切面完成 ufp_auth 数据源自动配置。

### 4.3 Phase 2 目标（独立微服务）

```
ufp-auth/
├── ufp-auth-api/                   # API 契约层（纯接口 + 模型，零业务逻辑）
│   ├── ufp-auth-basic-api          #   常量、枚举、错误码
│   ├── ufp-auth-model-api          #   模型 + DTO + Service 接口
│   │   ├── model/                  #     AuthUser, AuthRole, AuthRes, AuthGrant, ...
│   │   ├── dto/                    #     AuthUserDto, AuthRoleDto, ...
│   │   ├── enums/                  #     AuthGrantOwnerTypeEnum, ...
│   │   └── service/                #     IAuthUserService, IAuthRoleService, ...
│   └── ufp-auth-app-api           #   应用/Token 模型 + Service 接口
├── ufp-auth-client/                # 客户端 SDK（供业务模块引入）
│   ├── ufp-auth-client-metadata    #   AuthenticationToken, GrantedAuthority, 异常类
│   ├── ufp-auth-client-security    #   JwtDecoder, AuthSecurityFilter, Feign 拦截器, @RequirePermission
│   └── ufp-auth-client-sdk         #   Feign 客户端代理（管理操作）
└── ufp-auth-server/                # 服务实现（Spring Boot 3.3.x 独立应用）
    └── ufp-auth-server             #   全部 Service 实现 + Controller + Flyway (V1.4.x/V1.5.x)
```

Phase 2 时 `ufp-auth-server` 自带 Flyway 迁移（V1.4.x/V1.5.x），zhiyu-server Flyway 仅保留 V1.0.x-V1.3.x（zhiyu 业务表）。

### 4.4 核心模型

| 模型 | 说明 |
|------|------|
| `AuthUser` | 用户（含 personal/secure/field 扩展信息） |
| `AuthRole` | 角色（RBAC 核心） |
| `AuthRes` | 资源（菜单 + 按钮 + API，树形结构） |
| `AuthGrant` | 授权（角色 → 资源映射） |
| `AuthGrantPolicy` | 条件策略（IP/时间窗口/频率，P1） |
| `AuthOrg` | 组织机构（树形 + 用户归属） |

> 当前为 library jar 嵌入模式，不暴露 OIDC 端点、不独立部署。Phase 2 拆分微服务时再启用 OIDC Provider 端点。

### 4.5 数据库

`ufp_auth` 库，23 张表（与业务库同 MySQL 实例，通过 `REFERENCES ufp_auth.auth_user(auth_user_id)` 跨库关联）。详见 [DATABASE.md §3.2](DATABASE.md#32-ufp-auth-库)。

### 4.6 JWT Claims

```json
{
  "sub": "1001",
  "iss": "https://zhiyu.app",
  "aud": "zhiyu-backend",
  "exp": 1716100000,
  "iat": 1716092800,
  "jti": "uuid",
  "username": "alice",
  "scope": "openid profile"
}
```

## 5. Phase 1 集成方式（library jar 嵌入）

```
zhiyu-server (Spring Boot 单体)
│
├── zhiyu-common  ──── ufp-common (core / util / filter / dto / enums / i18n / cache /
│                                  captcha / validation / verify / audit / storage /
│                                  feature / idempotent / event / registry / factory /
│                                  datasource (注解) / config / openapi)
├── zhiyu-auth    ──── ufp-auth (JWT / BCrypt / TOTP / WebAuthn / OAuth /
│                                Token 黑名单 / model / service / event / config)
│                      ufp-auth 直接管理 ufp_auth 库（MyBatis-Plus + Redis）
│                      zhiyu-auth 负责：注册/登录/验证码/密码重置/TOTP/WebAuthn/设备管理
├── zhiyu-user    ──── zhiyu-auth (Service 注入)  # 纯用户资料：profile/偏好/注销
├── zhiyu-notification  # 通知模块：邮件/SMS/Push 统一发送
├── zhiyu-admin   ──── ufp-auth (管理员认证 + RBAC)
└── zhiyu-subscription
         │
         └── MySQL: zhiyu 库 ──FK──▶ ufp_auth 库 (同实例)
```

**集成规则：**
- `ufp-auth` 作为 library jar，所有调用为进程内方法调用
- `ufp-auth` 直接管理 `ufp_auth` 库（实体+Mapper+Service），不通过业务模块中转
- `ufp-common` 零 ORM/缓存依赖，仅提供 `@UfpDS` 注解 + `UfpDSContextHolder`（纯 Java），消费模块自行实现数据源路由
- `ufp-auth` 和 `zhiyu-common` 各自实现 `AbstractRoutingDataSource` + AOP 切面，绑定各自 Mapper
- 业务模块通过注入 Service 接口跨模块调用，禁止跨模块直接注入 Mapper
- **Flyway 拆分（Phase 2）**：V1.4.x/V1.5.x (ufp_auth 表) 从 zhiyu-server 迁移至 ufp-auth-server；Phase 1 共用 zhiyu-server Flyway
- Phase 2 拆分时，`ufp-auth` 独立部署为微服务，业务模块切换为 Feign client 调用

## 6. ufp-integration — 第三方集成扩展包（Phase 2）

### 6.1 设计目标

将支付、通知等第三方 SDK 封装为可复用扩展包，使不同业务项目可以按需引入，而不需要在各自代码中重复封装。OAuth 已纳入 `ufp-auth`，不在此扩展包中重复。

**原则：**
- 每个扩展包只定义 **接口 + 通用模型（DTO/Enum）**，不含业务逻辑
- 第三方 SDK 依赖只在具体 Provider 实现中引入，Consumer 只依赖接口
- Phase 1 在 ZhiYu 中直接使用 SDK → Phase 2 提取接口，ZhiYu 改为依赖接口

### 6.2 模块规划

```
ufp/integration/                     # Phase 2 — 可复用第三方集成适配层
├── ufp-integration-payment/         # 支付渠道抽象
│   ├── IPaymentProvider             #   统一下单/查询/退款/回调验签
│   └── model/                       #   PaymentOrder, PaymentResult, RefundRequest
└── ufp-integration-notification/    # 通知渠道抽象
    ├── INotificationProvider        #   发送短信/邮件/推送
    └── model/                       #   NotificationRequest, NotificationResult
```

### 6.3 核心接口示例

**支付：**
```java
public interface IPaymentProvider {
    String getChannel();                              // "WECHAT" | "ALIPAY"
    PrepayResult prepay(PrepayRequest req);           // 统一下单
    PaymentStatus query(String transactionId);        // 订单查询
    RefundResult refund(RefundRequest req);           // 退款
    boolean verifyCallback(String body, String sign); // 回调验签
}
```

**通知：**
```java
public interface INotificationProvider {
    String getChannel();                              // "SMS" | "EMAIL" | "PUSH"
    void send(NotificationRequest req);
    NotificationStatus query(String messageId);
}
```

### 6.4 实施时机

| 阶段 | 方式 |
|------|------|
| Phase 1（当前） | ZhiYu 创建 `zhiyu-notification` 模块统一封装邮件/SMS/Push 发送；其他模块调用 `zhiyu-notification` 而非直接依赖 SDK |
| Phase 2（第二项目需要复用） | 从 `zhiyu-notification` 提取接口 → 创建 `ufp-integration-notification` → ZhiYu 改为依赖接口 |

---

## 7. 实施路线

| 阶段 | 内容 | 优先级 |
|------|------|:---:|
| Phase 1a | `ufp-common` 单模块（22 个包：core/util/filter/DTO/enums/i18n/cache/captcha/validation/verify/audit/storage/feature/idempotent/event/registry/factory/datasource（仅注解）/config/openapi） | P0 |
| Phase 1b | `ufp-auth` library jar（JWT/BCrypt + AuthUser CRUD + Token 黑名单 + OAuth Provider 抽象 + 自行实现多数据源路由，含 MyBatis-Plus + Redis） | P0 |
| Phase 1c | `ufp-auth` RBAC（auth_role/auth_res/auth_grant CRUD） | P0 |
| Phase 1d | `ufp-auth` TOTP + WebAuthn + OAuth Provider 实现（aerogear-otp + Yubico + 第三方 SDK 集成） | P1 |
| Phase 1e | `ufp-auth` Token 轮换 + 设备管理 + 登录追踪 | P1 |
| Phase 1f | `zhiyu-notification` 通知模块（邮件/SMS/Push 统一发送） | P1 |
| Phase 2a | ufp-common 拆子模块 + ufp-auth 拆 3 层 + 独立部署 + Flyway 拆分（V1.4.x/V1.5.x → ufp-auth-server） | P2 |
| Phase 2b | `ufp-integration-payment/notification` 从 ZhiYu 提取接口 | P2 |
| P3 | `ufp-common-dict` 字典管理 | P3 |

## 附录：原始参考文档

以下文档包含基于 NTA 项目的完整分析，本文档为其精简合并版：

- [DESIGN-UFP-COMMON.md](reference/DESIGN-UFP-COMMON.md) — NTA `sfp-commons` 分析（10 个子模块）
- [DESIGN-UFP-AUTH.md](reference/DESIGN-UFP-AUTH.md) — NTA `sfp-auth` 分析（3 层架构、OIDC Provider）
- [DESIGN-UFP-META.md](reference/DESIGN-UFP-META.md) — NTA `sfp-meta` 分析（339 文件，95% 不适用）
