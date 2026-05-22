# ZhiYu-Backend 设计规格

> ⚠️ **历史参考文档** — 本文档为 2026-05-17 早期探索阶段产物。
> 其中部分技术选型已被后续架构决策覆盖，**当前事实源以以下文档为准**：
> - [ADR.md](../../product-design/ADR.md) — 13 条架构决策记录
> - [CLAUDE.md](../../../CLAUDE.md) — 项目技术栈与开发规范
> - [ARCHITECTURE.md](../../product-design/ARCHITECTURE.md) — 系统架构（4+1 视图）
>
> **主要差异**：纯 JWT Filter（非 Spring Security）、Maven（非 Gradle）、
> JDK 21（非 17）、多 Maven 模块（非单模块）、Woodpecker CI（非 GitHub Actions）。

> **配套文档索引**：
> - [API-SPEC.md](../../API-SPEC.md) — 接口规格（80+ 端点，请求/响应 Schema + 错误码）
> - [DATABASE.md](../../DATABASE.md) — 数据库设计（21 张表完整 DDL + Outbox + JSON Schema + 索引 + 初始数据）
> - [PRD.md](../../PRD.md) — 产品需求（功能矩阵 + 用户故事 + MVP 范围）
> - [ARCHITECTURE.md](../../ARCHITECTURE.md) — 架构决策（10 条 ADR + 5 个关键流程时序图）
> - [TEST-PLAN.md](../../TEST-PLAN.md) — 测试计划（50+ 单测 + 20+ 集成 + 5 E2E 用例）
> - [DEVELOPMENT-STANDARDS.md](../../DEVELOPMENT-STANDARDS.md) — 开发规范（分层/编码/API格式/错误码/日志）
> - [OPS.md](../../OPS.md) — 运维手册（SLO/告警/灾备/发布/应急预案）
> - [FRONTEND-DESIGN.md](../../FRONTEND-DESIGN.md) — 前端设计规格（14 页面组件树 + 交互状态 + 数据流）
> - [APP-DESIGN.md](../../APP-DESIGN.md) — 客户端 App 设计（iOS SwiftUI + Android Compose，9 页面流程 + 全局交互）
> - [SECURITY.md](../../SECURITY.md) — 安全测试与数据合规（SAST/SCA/DAST + 密钥管理 + PIPL 合规）
> - [RATE-LIMITING.md](../../RATE-LIMITING.md) — 接口限流设计（分层架构 + 算法选型 + Redis Key + Sentinel 规则）
> - [CI-CD.md](../../CI-CD.md) — CI/CD、部署与集成（GitHub Actions + Docker + K8s 清单 + 集成策略）
> - [INFRASTRUCTURE.md](../../INFRASTRUCTURE.md) — 基础设施设计（Nacos 配置中心 + Redis 数据结构 + 缓存策略）

## 1. 技术栈

### 基础框架

| 组件 | 选型 | 说明 |
|------|------|------|
| JDK | 17 | LTS，生态成熟 |
| 框架 | Spring Boot 3.x + Spring Cloud Alibaba 2023.x | 核心框架 |
| 注册/配置中心 | Nacos 2.x | 服务发现 + 配置管理 |
| 网关 | Spring Cloud Gateway | API 路由、限流、JWT 初步校验 |

### 数据存储

| 组件 | 选型 | 说明 |
|------|------|------|
| 数据库 | MySQL 8.x + Flyway 迁移 | 单库多表，日后可拆 |
| ORM | MyBatis-Plus 3.5+ | 灵活 SQL + 分页插件 |
| 缓存 | Redis 7.x（Lettuce 客户端） | 验证码、token 黑名单、配额计数、幂等去重 |
| 对象存储 | 阿里云 OSS | 用户头像等文件存储 |

### 身份认证

| 组件 | 选型 | 开源/自研 |
|------|------|-----------|
| 安全框架 | Spring Security 6.x | 开源（Spring 生态） |
| JWT | jjwt（io.jsonwebtoken）0.12+ | 开源，RS256 非对称签名 |
| WebAuthn | com.yubico:webauthn-server-core | 开源（Yubico 维护，FIDO2 标准实现） |
| TOTP | aerogear-otp-java 1.0+ | 开源（红帽项目） |
| 微信 OAuth | Spring Security OAuth2 Client（HTTP 手动调用微信 API） | 开源 |
| QQ OAuth | 同上，HTTP 调用 QQ 互联 API | — |
| Google Sign-In | Google API Client Library + Spring Security OAuth2 | 开源 |
| Apple Sign-In | apple-signin-auth 库 + 手动验证 identity_token | 开源 |
| CAPTCHA | 阿里云验证码（滑块）SDK | 商用，Spring 集成 |

### 授权与会话管理

| 组件 | 选型 | 说明 |
|------|------|------|
| API 鉴权 | Spring Security Filter Chain + 自定义 `@PreAuthorize` | 声明式注解 |
| Feature Flag 拦截 | 自定义 `@RequiresFeature` + Spring AOP 拦截器 | 自研，基于 Nacos 配置 |
| 配额限流 | 自定义 `@QuotaCheck` 拦截器 + Redis 计数器 | 自研 |
| Token 存储 | Redis（refresh_token）+ 内存（access_token 无状态） | — |
| 会话管理 | 自定义 TokenService + Redis | 自研封装 |

### 支付

| 组件 | 选型 | 说明 |
|------|------|------|
| 微信支付 | wechatpay-java 0.2+（官方 SDK） | 开源（微信官方） |
| 支付宝 | alipay-sdk-java 4.x | 开源（支付宝官方） |
| Apple IAP | HTTP 调用 App Store Server API | 自研封装 |
| Google Play | google-api-services-androidpublisher | 开源（Google 官方） |

### 消息与通知

| 组件 | 选型 | 说明 |
|------|------|------|
| 短信 | 阿里云 SMS SDK | 商用 |
| 邮件 | Spring Boot Mail（JavaMailSender） + 阿里云邮件推送 | 开源 + 商用 |
| 推送 | FCM（Firebase Admin SDK）+ APNs（java-apns）+ 个推（国内安卓） | 开源 + 商用 |

### 监控与日志

| 组件 | 选型 | 说明 |
|------|------|------|
| 指标暴露 | Spring Boot Actuator + Micrometer → Prometheus | 开源（Spring 生态） |
| 指标展示 | Prometheus + Grafana | 开源 |
| 限流/熔断 | Sentinel（Spring Cloud Alibaba Sentinel） | 开源 |
| 日志采集 | Filebeat → Loki（K8s 原生，轻量，无 ES 运维负担） | 开源（Grafana 生态） |
| 日志查询 | Grafana（运维）+ 管理后台日志页（运营） | — |
| 应用日志 | Logback + SLF4J（traceId：MDC 透传） | 开源（Spring Boot 默认） |

### 开发与部署

| 组件 | 选型 | 说明 |
|------|------|------|
| API 文档 | SpringDoc OpenAPI 3（Swagger UI） | 开源 |
| 容器化 | Docker + Kubernetes | 开源 |
| CI/CD | GitHub Actions（或 GitLab CI） | 开源 |
| 代码质量 | Checkstyle + SpotBugs（可选） | 开源 |
| 构建 | Gradle（Kotlin DSL）或 Maven | 开源 |

### 技术栈总览（按关注面）

| 关注面 | 核心依赖 |
|--------|---------|
| **身份认证** | Spring Security + jjwt + Yubico WebAuthn + aerogear-otp + 各 OAuth SDK |
| **授权** | Spring Security Filter Chain + 自定义 AOP（Feature/Quota） |
| **会话管理** | Redis + 自研 TokenService（JWT 签发/轮换/黑名单） |
| **用户生命周期** | MyBatis-Plus + Redis + 定时任务（Spring TaskScheduler） |
| **安全审计与防护** | Sentinel + Loki + Spring Security Audit Events + 自定义 AuditLogAspect |

全部基于开源方案，核心是 Spring 生态，零闭源依赖（仅阿里云/Apple/Google 商用 API 调用）。

## 2. 项目结构（单模块 + 领域隔离）

```
src/main/java/com/zhiyu/
├── common/          # 公共层：配置、异常、工具、安全过滤器、AOP
├── auth/            # 认证领域
├── user/            # 用户领域
├── subscription/    # 订阅/支付领域
├── admin/           # 管理后台领域
└── ZhiYuApplication.java
```

## 3. 认证领域 (auth/)

### 3.1 注册（用户名/密码）

```
POST /auth/send-register-code  → 向邮箱发送验证码（Redis，5分钟有效，60秒间隔限制）
                                → 需先通过 CAPTCHA 验证（滑块/图形）
POST /auth/register             → 提交 用户名+密码+邮箱+验证码+CAPTCHA token
                                → BCrypt 加密密码，创建用户，email_verified=true
POST /auth/check-availability   → 检查用户名/邮箱/手机号是否已被占用 {type, value}
```

**CAPTCHA 防机器人：**
- 注册、短信发送、忘记密码三个入口均需 CAPTCHA
- 选型：阿里云验证码（滑块验证）或 Google reCAPTCHA v3（无感），国内优先阿里云滑块
- 服务端验证 CAPTCHA token 后才允许发送验证码或提交注册
- 同一 IP 每小时最多 3 次注册，超过触发更严格 CAPTCHA

**密码强度规则：**
- 最小长度 8 位，最大 128 位
- 至少包含以下三类：大写字母、小写字母、数字、特殊字符
- 不允许与用户名或邮箱相同
- 前端实时强度指示 + 后端校验

**冲突处理：**
- 用户名全局唯一，占用时返回具体提示 + 建议可用名
- 邮箱全局唯一，已注册提示"该邮箱已被注册，是否去登录？"
- 手机号全局唯一，已注册提示"该手机号已被注册，是否使用短信登录？"

### 3.2 第三方登录（微信/QQ/Google/Apple）

```
POST /auth/third-party → 提交 {type, code, state}
  → 验证 state 参数（防 CSRF，Redis 存储，5分钟有效）
  → 换取 access_token，获取 openid/userId
  → 查询 user_auth_identity 是否存在
  → 存在：直接签发 JWT（email_verified 按用户实际状态）
  → 不存在：创建 user + identity，标记 email_verified=false，签发临时 JWT
  → 客户端检测 email_verified=false 时强制引导绑定邮箱

POST /auth/third-party/bind-email → 第三方用户首次绑邮箱
  → 发送验证码 → 验证 → 标记 email_verified=true
  → 未绑邮箱前，JWT 中 scope=LIMITED，只能访问 /auth/* 和 /user/bind-email
```

### 3.3 短信验证码登录

```
POST /auth/sms/send   → 发送短信验证码（Redis，5分钟有效，60秒间隔）
POST /auth/sms/login  → 验证码校验 → 查找/创建用户 → 签发 JWT
```

### 3.4 WebAuthn（FIDO2）

```
POST /auth/webauthn/register/begin  → 返回 PublicKeyCredentialCreationOptions + challenge
POST /auth/webauthn/register/finish → 验证 attestation → 存储 credential
POST /auth/webauthn/auth/begin      → 返回 PublicKeyCredentialRequestOptions + challenge
POST /auth/webauthn/auth/finish     → 验证 assertion → 签发 JWT
```

### 3.5 密码/用户名找回

```
POST /auth/forgot-password → 提交邮箱 + CAPTCHA
                           → 生成一次性重置 token → 发送重置链接邮件
                           → 链接格式: https://zhiyu.app/reset-password?token=xxx
                           → token 有效期 15 分钟，Redis 存储，使用后立即失效

POST /auth/reset-password  → 提交 token + 新密码
                           → 验证 token 有效性 → 更新密码 → 删除 token
                           → 该用户所有 refresh_token 立即失效（强制全设备重新登录）

POST /auth/forgot-username → 提交邮箱 → 发送用户名提醒邮件
```

**重置 Token 安全要求：**
- token 为随机 256-bit 熵值（UUID + SecureRandom 组合）
- 同一用户 5 分钟内仅允许申请 1 次重置
- 重置成功后清除该用户所有活跃会话（安全措施）

### 3.6 Token 管理

```
POST /auth/refresh  → refresh_token 换新 access_token（旧 refresh_token 失效，发放新对）
POST /auth/logout   → access_token 加入 Redis 黑名单，删除 refresh_token
GET  /auth/devices  → 当前用户已登录设备列表
DELETE /auth/devices/{deviceId} → 踢出指定设备
```

- access_token：短期（15分钟），无状态 JWT，payload 包含 `jti`、`user_id`、`device_id`、`scope`、`email_verified`
- refresh_token：长期（7天），存储于 Redis `refresh:<user_id>:<device_id>`，支持轮换
- 黑名单：Redis Set，key 为 `jwt:blacklist:<jti>`，过期时间对齐 token 剩余有效期
- 轮换策略：使用 refresh_token 时旧 token 立即失效，检测到旧 token 被重用则使该用户所有 refresh_token 全部失效（防盗用）

### 3.7 多设备登录策略

- 同一用户**最多 5 个设备**同时在线，超出时踢出最久未活动设备
- 每设备独立 refresh_token，device_id 由客户端生成并持久化（安装时生成 UUID）
- 设备列表查询：返回 `{device_id, device_name, platform, last_active_at, current}`
- 踢出设备：管理员可在后台强制踢出，用户可在个人中心管理自己设备
- 设备下线：logout 只下线当前设备的 token，不影响其他设备

### 3.8 绑定/解绑二次验证

已登录用户绑定/解绑认证方式时需验证身份：

```
POST /user/bind-*       → 需要请求头 X-Action-Token（通过 POST /auth/action-verify 获取）
POST /user/unbind/{id}  → 需要请求头 X-Action-Token

POST /auth/action-verify → 提交当前已有的认证凭证（密码/短信验证码/TOTP）
                         → 返回一次性 action_token（5分钟有效）
```

- 绑定新方式：先验证已有凭证 → 获得 action_token → 完成绑定
- 解绑：先验证已有凭证 → 检查不是最后一个身份 → 完成解绑
- TOTP 初始绑定：绑定后自动生成恢复码（一次性备用码，建议用户妥善保存）

### 3.9 API 权限矩阵

| URL 前缀 | 认证要求 | 特殊处理 |
|----------|---------|---------|
| `/auth/**` | 无需认证（login/register 相关） | 全局限流严格（IP 级别） |
| `/user/bind-email` | scope=LIMITED 即可 | 允许未验证邮箱用户访问 |
| `/user/**` | access_token + scope=FULL | 邮箱未验证返回 403 |
| `/sub/plans` | 无需认证 | 公开 |
| `/sub/status` | scope=FULL | |
| `/sub/**` | scope=FULL | |
| `/api/v1/admin/auth/**` | 无需认证（admin 登录相关） | IP 白名单限流 |
| `/api/v1/admin/**` | admin JWT + ROLE_ADMIN 以上 | CS 仅读权限需单独判断 |

- 资源级鉴权：用户只能访问自己的 profile、订阅、订单
- Admin 跨用户访问：所有 admin API 均记录操作审计

### 3.10 Feature Flag 与配额拦截器

**Feature Flag 拦截：**

```java
// 使用方式
@RestController
public class ImageController {
    @RequiresFeature("image_gen")  // 自动校验当前用户套餐是否包含此功能
    @PostMapping("/api/v1/image/generate")
    public Result generate(@RequestBody ImageRequest req) { ... }
}
```

- `@RequiresFeature` AOP 拦截器：
  - 从 JWT 获取 user_id → 查当前订阅 plan → 查 Nacos 中的 plan.features
  - 功能未开放 → 返回 403，错误码 `FEATURE_NOT_AVAILABLE`
  - Nacos 配置变更后实时生效（监听配置变更事件）

**配额限流拦截：**

```java
@QuotaCheck(key = "daily_chat", message = "今日对话次数已用完")
@PostMapping("/api/v1/chat")
public Result chat(@RequestBody ChatRequest req) { ... }
```

- `@QuotaCheck` AOP 拦截器：
  - Redis key: `quota:<user_id>:daily_chat:2026-05-17`（按天聚合）
  - 每次调用 INCR + TTL 对齐当天结束
  - 超限 → 返回 429，错误码 `QUOTA_EXCEEDED`，响应头 `X-RateLimit-Reset`
  - 配额值从 Nacos `subscription.plans.<plan>.quotas.<key>` 读取
  - 特殊值 -1 表示不限制

**拦截器执行顺序（Filter Chain）：**
```
1. JWT 认证 Filter（解析 token → SecurityContext）
2. Feature 拦截器（检查功能开关）
3. Quota 拦截器（检查配额余量）
4. Controller（业务逻辑）
```

### 3.11 账户恢复机制

所有认证方式均丢失时的兜底恢复流程：

```
POST /auth/account-recovery/apply
  → 提交已知信息：曾绑定的邮箱/手机号/用户名（至少一项）
  → 系统验证信息匹配 → 创建人工审核工单
  → 通知管理员审核（管理后台待办）
  → 管理员验证用户身份（如要求提供身份证件或联系客服）
  → 审核通过 → 发送临时恢复链接到用户提供的可信邮箱
  → 用户通过链接登录 → 强制重新绑定认证方式
```

- 该流程为人工介入，不支持自动恢复（防止社会工程攻击）
- 审核工单 7 天内有效，超时自动关闭

### 3.12 用户 TOTP 设置

用户端也可启用 TOTP 双因素认证，增强账户安全性：

```
POST /auth/totp/setup    → 初始化 TOTP（返回 secret + QR 码供 Authenticator 绑定）
POST /auth/totp/enable   → 启用 TOTP（验证一次 code，返回备用恢复码 × 5）
POST /auth/totp/disable  → 禁用 TOTP（验证当前 code + 密码/短信）
GET  /auth/totp/status   → 查询 TOTP 是否已启用
```

- 启用 TOTP 后，登录流程：密码/第三方 → JWT scope=TOTP_REQUIRED → 提交 TOTP → 完整 JWT
- 恢复码：一次性 8 位数字 × 5 个，建议打印或安全保存
- 设置 TOTP 的设备可标记"信任"，跳过后续 TOTP 验证

## 4. 用户领域 (user/)

### 4.1 数据模型

```
user (
  id, username, email, email_verified, phone, phone_verified,
  nickname, avatar_url, status (ACTIVE|DISABLED|DELETED),
  created_at, updated_at
)

user_auth_identity (
  id, user_id, identity_type (WECHAT|QQ|PHONE|EMAIL|GOOGLE|APPLE|WEBAUTHN|PASSWORD),
  identifier (openid/手机号/邮箱), credential (仅 PASSWORD 存 BCrypt hash),
  created_at
)
```

### 4.2 用户生命周期状态机

```
                    ┌────────┐
         注册/第三方  │ ACTIVE │  管理员禁用
       ────────────▶ │  正常   │ ◀───────────
                    └───┬────┘
           用户注销      │      管理员启用
           ┌────────────┼─────────────┐
           ▼            │             ▼
     ┌──────────┐       │      ┌──────────┐
     │ DELETED  │       │      │ DISABLED │
     │ 软删除    │       │      │  管理员禁  │
     │ (30天)   │       │      │ Token全失效│
     └────┬─────┘       │      └─────┬────┘
          │             │            │
     ┌────▼─────┐       │      ┌─────▼─────┐
     │ 30天内   │       │      │  启用     │
     │ 重新激活  │───────┘      │  →ACTIVE  │
     │ →ACTIVE  │              └───────────┘
     └──────────┘
          │ 30天后
          ▼
     ┌──────────┐
     │  永久清理  │
     │ 关联数据  │
     │ 匿名化处理 │
     └──────────┘
```

**状态说明：**
| 状态 | 含义 | 访问权限 |
|------|------|---------|
| ACTIVE | 正常使用 | 完整 API 访问 |
| DISABLED | 管理员封禁 | 返回 403，提示"账户已被禁用" |
| DELETED | 用户主动注销（30天冷却期） | 返回 403，提示"账户已注销，N天内可恢复" |

**注销（DELETED）处理：**
- 立即清空该用户所有 Redis 中的 refresh_token
- 将该用户所有活跃 access_token 的 jti 加入黑名单
- 用户表标记 status=DELETED + deleted_at 时间戳
- 30 天内可通过 POST `/user/reactivate` 恢复（需邮箱验证码）
- 30 天后定时任务永久清理：
  - 用户表匿名化（username/anonymized_xxx, email 脱敏, phone 清空）
  - 支付记录保留（财务审计需要），但 user_id 替换为匿名标识
  - 审计日志保留，操作人显示为"已注销用户(N)"
  - 删除头像等用户内容（OSS）

**禁用（DISABLED）处理：**
- 管理员操作，立即生效
- 立即清空该用户所有活跃 session（同注销逻辑）
- 与注销不同：保留 username/email，不进入 30 天清理流程
- 管理员可随时启用 → 恢复 ACTIVE

**注册欢迎流程：**
- 注册成功后发送欢迎邮件（确认邮箱 + 引导绑定更多认证方式）
- 内容包含：ZhiYu 功能简介、各套餐介绍链接、客服联系方式

### 4.3 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/user/bind-email` | 绑定/更换邮箱（需验证码） |
| POST | `/user/bind-phone` | 绑定手机号（需短信验证码） |
| POST | `/user/bind-wechat` | 绑定微信 |
| POST | `/user/bind-qq` | 绑定QQ |
| POST | `/user/bind-google` | 绑定Google |
| POST | `/user/bind-apple` | 绑定Apple ID |
| POST | `/user/unbind/{identityId}` | 解绑认证方式（至少保留一个） |
| GET  | `/user/profile` | 获取个人信息 |
| PUT  | `/user/profile` | 更新个人信息（昵称、头像等） |
| POST | `/user/deactivate` | 注销账户（软删除，立即失效所有 token） |
| POST | `/user/reactivate` | 30 天内重新激活（需邮箱验证码） |
| GET  | `/user/settings` | 获取账户设置（通知偏好、隐私设置） |
| PUT  | `/user/settings` | 更新账户设置 |

---

## 5. 管理后台领域 (admin/)

### 5.1 后台用户认证

后台用户独立于前台用户表：

```
admin_user (
  id, username, password (BCrypt), email, phone, wechat_openid, wecom_userid,
  totp_secret, totp_enabled, status, role, created_at
)
```

**登录方式：**

```
POST /api/v1/admin/auth/login
  支持 grant_type:
  ├── PASSWORD  → 用户名 + 密码 → 检查 TOTP → 签发 admin JWT
  ├── SMS       → 手机号 + 短信验证码 → 签发 admin JWT
  ├── WECHAT    → 微信授权码 → 验证 openid → 签发 admin JWT
  ├── WECOM_QR  → 返回企业微信扫码 URL + ticket
  └── TOTP      → 提交 TOTP 码（PASSWORD阶段未验证TOTP时）

POST /api/v1/admin/auth/totp/setup    → 初始化 TOTP（返回 secret + QR 码）
POST /api/v1/admin/auth/totp/enable   → 启用 TOTP（验证一次 code）
POST /api/v1/admin/auth/totp/disable  → 禁用 TOTP（验证当前 code）
POST /api/v1/admin/auth/wecom/callback → 企业微信扫码回调
POST /api/v1/admin/auth/refresh       → refresh_token 换新 access_token（含轮换）
POST /api/v1/admin/auth/logout        → token 加入黑名单，记录审计日志
GET  /api/v1/admin/auth/session       → 查询当前会话的剩余有效期和最后活跃时间
```

**权限角色：**
- `ROLE_SUPER_ADMIN`：所有权限 + 管理后台用户管理
- `ROLE_ADMIN`：用户管理 + 订阅管理
- `ROLE_CS`：仅查看用户信息（客服用）

### 5.1.1 RBAC 权限模型

采用 RBAC（Role-Based Access Control）：`后台用户 → 角色 → 权限`，支持菜单级和按钮级控制。

**数据模型：**

```sql
admin_role (                   -- 角色表
  id, role_code (SUPER_ADMIN|ADMIN|CS), role_name, description
)

admin_permission (             -- 权限表
  id, perm_code, perm_name, perm_type (MENU|BUTTON|API), parent_id
)

admin_role_permission (        -- 角色-权限关联
  role_id, permission_id
)

admin_user (                   -- 用户-角色直接关联（单角色）
  ..., role_id
)
```

**权限清单：**

```
菜单权限（MENU）：
  dashboard          仪表盘
  users              用户管理
  users.export       用户导出
  subscriptions      订阅管理
  payments           支付管理
  payments.reconcile 对账操作
  refunds            退款审核
  audit              审计日志
  audit.export       日志导出
  admins             后台用户管理（仅 SUPER_ADMIN）
  config             系统配置（仅 SUPER_ADMIN）
  monitor            运行监控
  logs               日志管理
  logs.settings      日志级别调整（仅 SUPER_ADMIN）
  notifications      通知模板管理

按钮/操作权限（BUTTON）：
  users.batch-disable   批量禁用用户
  users.view-identity   查看用户绑定身份详情
  subscriptions.modify  手动修改订阅
  payments.export       导出支付流水
  refunds.approve       退款审核通过
  config.edit           编辑配置
  config.rollback       回滚配置

API 权限：对应 POST/PUT/DELETE 等写操作，角色不满足则接口返回 403
```

**角色-权限映射：**

| 权限 | SUPER_ADMIN | ADMIN | CS |
|------|:-----------:|:-----:|:--:|
| dashboard | ✅ | ✅ | ✅ |
| users (查看) | ✅ | ✅ | ✅ |
| users (批量禁用/导出) | ✅ | ✅ | ❌ |
| subscriptions (查看) | ✅ | ✅ | ✅ |
| subscriptions (修改) | ✅ | ✅ | ❌ |
| payments (查看) | ✅ | ✅ | ✅ |
| payments (导出/对账) | ✅ | ✅ | ❌ |
| refunds (查看+审批) | ✅ | ✅ | ❌ |
| audit | ✅ | ✅ | ❌ |
| admins | ✅ | ❌ | ❌ |
| config (查看) | ✅ | ✅ | ❌ |
| config (编辑/回滚) | ✅ | ❌ | ❌ |
| monitor | ✅ | ✅ | ❌ |
| logs (查看) | ✅ | ✅ | ❌ |
| logs.settings | ✅ | ❌ | ❌ |
| notifications (查看) | ✅ | ✅ | ❌ |
| notifications (编辑) | ✅ | ❌ | ❌ |

**实现要点：**

- **后端**：Spring Security `@PreAuthorize("hasAuthority('payments.export')")` 注解控制 API 权限
- **前端路由**：根据当前用户权限列表动态生成菜单，无权限的路由不注册
- **前端按钮**：封装 `<PermissionGate permission="users.batch-disable">` 组件，无权限时按钮不渲染
- **权限缓存**：登录时返回用户权限列表，存入 JWT payload（或单独 API 获取）
- **角色可扩展**：新增角色（如"财务"只需 payments + refunds 权限）只需配置权限映射

### 5.2 用户管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/users?page=&size=&status=&keyword=` | 用户列表 |
| GET | `/admin/users/{id}` | 用户详情（含所有绑定身份） |
| PUT | `/admin/users/{id}/status` | 启用/禁用用户 |
| GET | `/admin/users/{id}/auth-identities` | 绑定的认证身份列表 |
| PUT | `/admin/users/batch/status` | 批量启用/禁用用户 `{user_ids, action}` |
| GET | `/admin/users/export?status=&start=&end=` | 导出用户数据（CSV/Excel） |

### 5.3 认证审计

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/audit/login-log?userId=&start=&end=&type=` | 登录日志 |
| GET | `/admin/audit/identity-changes?userId=&start=&end=` | 身份变更记录（绑定/解绑，含操作来源 IP） |
| GET | `/admin/audit/operations?operatorId=&action=&start=&end=` | 管理员操作日志 |
| GET | `/admin/audit/operations/export?start=&end=` | 导出操作日志 |

### 5.4 订阅/支付管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/subscriptions?page=&status=&plan=` | 订阅列表 |
| GET | `/admin/subscriptions/{id}` | 订阅详情（含支付流水） |
| PUT | `/admin/subscriptions/{id}` | 手动修改（赠予/补偿/退款） |
| GET | `/admin/payments?page=&start=&end=&channel=&status=` | 支付流水 |
| GET | `/admin/payments/{id}` | 支付详情 + 对账状态 |
| GET | `/admin/payments/export?start=&end=&channel=` | 导出支付流水 |
| GET | `/admin/payments/reconciliation?date=` | 对账结果查询 |
| POST | `/admin/payments/reconciliation/run` | 手动触发对账 |

### 5.5 退款审核

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/refunds?page=&status=&start=&end=` | 退款申请列表 |
| GET | `/admin/refunds/{id}` | 退款详情（原始订单+用户信息） |
| POST | `/admin/refunds/{id}/approve` | 审核通过 → 调支付渠道退款 API |
| POST | `/admin/refunds/{id}/reject` | 审核拒绝（填写原因） |

### 5.6 系统配置管理

管理后台可视化管理 Nacos 中的业务配置（仅 SUPER_ADMIN）：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/config` | 配置分组列表 |
| GET | `/admin/config/{groupId}/{dataId}` | 查看配置内容（格式化 YAML/JSON） |
| PUT | `/admin/config/{groupId}/{dataId}` | 更新配置（记录变更历史，支持回滚） |
| GET | `/admin/config/history/{groupId}/{dataId}` | 配置变更历史 |

**可管理的配置项：**

| 配置组 | 内容 | 风险等级 |
|--------|------|----------|
| `subscription.plans` | 套餐权益、价格、配额 | 高（发布前需二次确认） |
| `notification.templates` | 短信/邮件/推送模板 | 低 |
| `security.policy` | 登录失败锁定阈值、锁定时长、空闲超时 | 中 |
| `rate-limit` | 各接口限流阈值 | 中 |
| `payment.channels` | 支付渠道开关、沙箱/生产模式切换 | 高（发布前需二次确认） |

- 高风险配置修改需双因素验证（TOTP）
- 所有配置变更记录到审计日志
- 支持按 dataId 回滚到历史版本

### 5.8 通知模板管理

管理短信、邮件、推送通知模板（Nacos `notification.templates` 配置的可视化管理）。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/notifications?type=&page=` | 模板列表（类型筛选：SMS/EMAIL/PUSH） |
| GET | `/admin/notifications/{id}` | 查看模板详情（含变量说明和预览） |
| PUT | `/admin/notifications/{id}` | 编辑模板（仅 SUPER_ADMIN 可执行，变更审计） |
| POST | `/admin/notifications/{id}/test` | 发送测试消息到指定目标（邮箱/手机号） |

**可管理的模板：**

| 模板 key | 类型 | 变量 |
|-----------|------|------|
| register_welcome | EMAIL | `{username}`, `{app_name}` |
| email_verify_code | EMAIL | `{code}`, `{expire_minutes}` |
| password_reset | EMAIL | `{reset_link}`, `{expire_minutes}` |
| forgot_username | EMAIL | `{username}`, `{app_name}` |
| sms_verify_code | SMS | `{code}`, `{expire_minutes}` |
| sub_renewal_reminder | PUSH/EMAIL | `{plan_name}`, `{expire_date}` |
| sub_expired | PUSH/EMAIL | `{plan_name}` |
| sub_renew_success | PUSH/EMAIL | `{plan_name}`, `{new_expire_date}` |
| sub_renew_failed | PUSH/EMAIL | `{plan_name}`, `{reason}` |
| trial_ending | PUSH/EMAIL | `{days_left}`, `{plan_name}` |

### 5.7 日志管理

完整的日志采集、存储、检索和管理体系。

**日志层级架构：**

```
日志产生层（Spring Boot Pod × N）
  ├── 访问日志（Access Log）
  ├── 应用日志（Logback/SLF4J）
  ├── 慢查询日志（MySQL slow query）
  └── 审计日志（audit_log 表结构）
        │
        ▼ Filebeat / Logback Appender
日志收集层
  dev: Logback 写本地文件 + console
  test/release: Filebeat → Elasticsearch 或 Loki
        │
        ▼
日志存储与检索
  Elasticsearch / Loki + 索引策略
  按天索引，保留策略按环境：
    dev: 7天    test: 30天    release: 90天
        │
        ▼
日志查询层
  Grafana（运维）+ 管理后台日志页（运营）
```

**日志分类：**

| 日志类型 | 说明 | 存储后端 | 保留 |
|----------|------|---------|------|
| **访问日志** | 每个 HTTP 请求：来源 IP、路径、方法、响应码、耗时、User-Agent | ES/Loki | 90天 |
| **应用日志** | ERROR/WARN/INFO/DEBUG，按包路径区分级别，含 traceId | ES/Loki | 30天 |
| **慢查询日志** | 执行 > 200ms 的 SQL，含完整语句和参数 | MySQL + ES | 30天 |
| **审计日志** | 管理员操作、用户敏感行为（登录/绑定/解绑/注销/超时退出） | MySQL `audit_log` 表 | 永久 |
| **安全日志** | 登录失败、token 异常、越权尝试、限流触发 | ES/Loki | 180天 |

**审计日志表结构：**

```sql
audit_log (
  id, event_type, operator_id, operator_type (ADMIN|USER|SYSTEM),
  target_type, target_id, action, detail (JSON: 旧值、新值、变更字段),
  source_ip, user_agent, request_id, created_at
)
```

**管理后台日志 API：**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/logs/access?start=&end=&path=&status=&ip=&page=` | 访问日志检索 |
| GET | `/admin/logs/application?start=&end=&level=&keyword=&page=` | 应用日志检索 |
| GET | `/admin/logs/slow-sql?start=&end=&min_ms=&page=` | 慢查询日志 |
| GET | `/admin/logs/security?start=&end=&type=&ip=&page=` | 安全日志 |
| GET | `/admin/logs/stats` | 日志量统计（按类型/级别，近 7 天趋势） |

**管理后台日志页面（`/admin/logs/`）：**

| 页面 | 功能 |
|------|------|
| access | 访问日志：时间范围、路径模糊搜索、状态码筛选、IP 搜索 |
| application | 应用日志：级别筛选、关键字全文搜索、traceId 关联跳转 |
| slow-sql | 慢查询：按耗时排序、SQL 语句高亮、来源代码位置 |
| security | 安全日志：事件类型筛选、IP 地理位置展示 |
| audit | 审计日志（共用 `/admin/audit/*` API） |
| settings | 日志级别调整（仅 SUPER_ADMIN，Actuator loggers 封装） |

**运行时日志级别调整：**
- 基于 Spring Boot Actuator `/actuator/loggers/{name}`
- 管理后台封装：选择包路径 + 选择级别 → 立即生效，无需重启
- 记录操作审计：谁、什么时间、将哪个包级别从 X 改为 Y
- 自动回滚：调整后 30 分钟自动恢复为默认级别（防日志爆炸）

**日志清理策略：**
- 定时任务（每日凌晨 4:00）清理过期日志
- Elasticsearch：ILM（Index Lifecycle Management）自动滚动删除
- MySQL 审计日志：超过保留期归档冷存储（按合规需求确定）
- 清理操作本身也记录日志

---

## 6. 订阅/支付领域 (subscription/)

### 6.1 套餐模型

```
Nacos 配置（可动态调整，无需重启）：
subscription.plans:
  free:
    features: [basic_chat, text_search]
    quotas:
      daily_chat: 10
      file_upload_mb: 5
  lite:
    price_monthly: N
    price_yearly: N
    features: [basic_chat, text_search, file_upload, image_gen]
    quotas:
      daily_chat: 200
      file_upload_mb: 50
      image_gen_daily: 20
  pro:
    price_monthly: M
    price_yearly: M
    features: [*]
    quotas:
      daily_chat: -1      # 不限
      file_upload_mb: 500
      image_gen_daily: 200
      priority_queue: true
```

### 6.2 数据库

```
subscription_plan      套餐定义（id, plan_key, name, ...）
user_subscription       用户订阅（id, user_id, plan_id, status, start_date, end_date, auto_renew）
subscription_order     订阅订单（id, order_no, user_id, plan_id, amount, period[MONTHLY|YEARLY], status）
payment_record         支付记录（id, order_id, channel[WECHAT|ALIPAY|APPLE|GOOGLE], transaction_id, amount, currency, status, raw_notification）
user_quota_usage       用量记录（id, user_id, quota_key, used_count, period_start, period_end）
```

### 6.3 支付流程

**服务端下单类（微信支付、支付宝）：**
1. 客户端 POST `/sub/orders` → 服务端创建订单，调用支付 API 获取 prepay 信息
2. 客户端调起支付 SDK
3. 第三方 POST `/sub/callback/{channel}` → 服务端验签 → 更新订单 → 激活订阅

**客户端验证类（Apple IAP、Google Play）：**
1. 客户端在平台内发起购买
2. 客户端 POST `/sub/verify-receipt` 提交 receipt
3. 服务端向 Apple/Google 验证票据 → 创建订单 → 激活订阅

### 6.4 订阅状态机

```
ACTIVE → (手动取消) → CANCELING → (到期) → EXPIRED
ACTIVE → (到期未续费) → EXPIRED
EXPIRED → (重新购买) → ACTIVE
```

- Apple/Google 的 auto-renew 由平台侧管理，服务端定期轮询票据状态或接收 statusUpdateNotification
- 微信/支付宝通过签约代扣或用户主动续费
- 取消订阅（CANCELING）：当前周期仍可用，到期后降级为游客

### 6.5 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/sub/plans` | 获取所有套餐信息 |
| GET | `/sub/status` | 当前用户订阅状态和配额使用情况 |
| POST | `/sub/orders` | 创建订阅订单（微信支付/支付宝） |
| POST | `/sub/verify-receipt` | Apple IAP/Google Play 票据验证（含恢复购买） |
| POST | `/sub/callback/{channel}` | 支付渠道异步回调（签名验证 + 幂等） |
| POST | `/sub/cancel` | 取消自动续费 |
| POST | `/sub/upgrade` | 套餐升级（Lite→Pro），按剩余天数比例折价 |
| POST | `/sub/downgrade` | 套餐降级（Pro→Lite），当前周期结束后生效 |
| POST | `/sub/restore` | 恢复购买（换设备时恢复已购订阅） |
| GET | `/sub/history` | 订单/支付历史 |
| POST | `/sub/refund/apply` | 用户申请退款 |
| GET | `/sub/trial-status` | 查询试用期剩余天数 |
| POST | `/sub/trial/start` | 开始试用（从未试用过的用户） |

### 6.6 套餐升级/降级

```
升级（Lite → Pro）：
  计算剩余天数价值 = (原套餐价格 / 周期天数) × 剩余天数
  新订单金额 = 新套餐价格 - 剩余天数价值（最低为 0）
  立即生效，新周期从升级日开始计算

降级（Pro → Lite）：
  不退款，当前 Pro 周期继续有效直到到期
  到期后自动续费为 Lite 价格
  状态：ACTIVE + pending_downgrade=LITE
```

### 6.7 恢复购买（Restore Purchases）

- 客户端调用 `/sub/verify-receipt`，传入 `restore_mode=true`
- 服务端验证 Apple/Google 票据 → 查找历史有效订阅 → 重新激活
- 对于同一平台同一 original_transaction_id，不做重复激活
- 跨平台恢复：用户提供身份验证后，可将 iOS 的订阅迁移到 Android（限 1 次/年）

### 6.8 支付幂等处理

- 所有支付回调 `/sub/callback/{channel}` 基于 `out_trade_no`（微信/支付宝）或 `transaction_id`（Apple/Google）做幂等
- Redis key `pay:processed:<transaction_id>` 记录处理状态，TTL 30天
- 重复回调：返回 200（已处理），不再重复发货、不重复写支付记录
- 乱序回调：始终查询第三方订单状态作为最终仲裁（GET 请求查单接口），不以回调时序为准
- 未匹配回调：订单 PENDING 超过 30 分钟，定时任务主动查单

### 6.9 退款流程

- 用户端发起退款 → 创建退款单 → 管理员审核 → 调用支付渠道退款 API
- Apple/Google 退款由平台侧发起，服务端接收 `REFUND` 通知后：
  - 标记订阅为 REFUNDED → 立即降级为游客
  - 保留历史订阅记录用于审计
- 退款原因分类：重复购买、误购、功能不满足、其他
- 退款后用户在该周期内不可重复购买同套餐（防刷）

### 6.10 对账机制

- 定时任务（每日凌晨 3:00）：
  - 拉取各支付渠道前一日交易账单
  - 与本地 `payment_record` 逐笔对账
  - 差异标记：`MATCH` / `MISMATCH` / `ONLY_LOCAL` / `ONLY_REMOTE`
- 差异告警：对账不一致时发送告警通知
- 管理后台可查看对账结果和差异明细

### 6.11 试用期

- 新用户注册即获免费游客权限（永久）
- Lite 可配置 **7 天免费试用**（Nacos 配置，可关闭）
  - 试用开始记录 `trial_start_date`
  - 试用期间功能与正式 Lite 相同
  - 试用到期前 1 天发送推送提醒
  - 到期未订阅 → 降回免费游客
  - 每用户仅一次试用机会（按 user_id 去重，不是按设备）

### 6.12 到期提醒

- 到期前 7 天、3 天、1 天各发送一次提醒（推送通知 + 邮件）
- 到期当日发送："您的订阅已到期，功能已降级为免费游客"
- 自动续费成功发送："续费成功，订阅有效期至 YYYY-MM-DD"
- 自动续费失败（余额不足等）发送："续费失败，请检查支付方式"
- 提醒模板可通过管理后台配置

## 7. 多环境配置

Spring Boot profiles：`dev` / `test` / `release`

### 配置文件结构

```
src/main/resources/
├── application.yml                  # 公共配置
├── application-dev.yml              # 本地开发
├── application-test.yml             # 测试环境
├── application-release.yml          # 生产环境
```

### 各环境差异化配置

| 配置项 | dev | test | release |
|--------|-----|------|---------|
| Nacos 地址 | localhost:8848 | nacos-test:8848 | nacos-prod:8848 |
| MySQL | localhost:3306 | mysql-test:3306 | mysql-prod:3306（主从）|
| Redis | localhost:6379 | redis-test:6379 | redis-prod:6379（哨兵/集群）|
| 短信 | 不实际发送（mock） | 测试号段 | 阿里云生产模板 |
| 支付 | 沙箱模式 | 沙箱模式 | 生产商户号 |
| 日志级别 | DEBUG | INFO | WARN |
| mail | console mock | 测试邮箱 | 生产 SMTP |

### 敏感信息管理

所有密钥不写入 YAML，通过 K8s Secret 注入为环境变量：

```
# K8s Secret → Pod env → application.yml 中 ${VAR_NAME} 引用
spring.datasource.password: ${DB_PASSWORD}
jwt.private-key: ${JWT_PRIVATE_KEY}
wechat.app-secret: ${WECHAT_APP_SECRET}
alipay.app-private-key: ${ALIPAY_APP_PRIVATE_KEY}
apple.shared-secret: ${APPLE_SHARED_SECRET}
```

### Nacos 共享配置

跨环境通用的业务配置存放于 Nacos 的共享配置中（如套餐权益、支付渠道开关、限流阈值），环境变量只管理连接地址和密钥。

---

## 8. 基础设施可靠性

### 8.1 MySQL

- **主从架构**：一主一从（release），读写分离
  - 写操作走主库
  - 读操作用于查询列表、报表等非强一致性场景走从库
  - 认证、支付等强一致性场景读写均走主库
- **连接池（HikariCP）**：
  - maximumPoolSize: 20（按 Pod 副本数调整）
  - connectionTimeout: 3000ms
  - idleTimeout: 600000ms
  - leakDetectionThreshold: 60000ms
- **自动故障转移**：主库宕机时，通过 K8s Service 或数据库代理（如 ProxySQL）切换，应用层无需感知
- **备份策略**：每日全量备份 + binlog 增量，保留 30 天
- **Flyway 迁移**：启动时自动执行未应用的迁移脚本，迁移脚本纳入版本控制

### 8.2 Nacos

- **集群部署（release）**：3 节点 StatefulSet，避免单点
- **数据持久化**：Nacos 使用内嵌 Derby 仅限 dev；test 和 release 必须使用外部 MySQL 存储配置
- **健康检查**：K8s liveness/readiness probe
- **配置备份**：定期导出 Nacos 配置快照到 Git，确保配置可恢复
- **应用降级策略**：当 Nacos 不可达时，应用缓存上一次成功拉取的配置快照（本地文件缓存），确保 Pod 重启可用

### 8.3 Redis

- **dev**：单实例
- **test**：单实例 + AOF 持久化
- **release**：Redis Sentinel（哨兵模式）或 Cluster
  - Sentinel：3 节点，自动故障转移
  - 连接使用 `spring.redis.sentinel.nodes` 配置
- **缓存降级策略**：
  - Redis 不可用时，应用降级运行：
    - 验证码：直接拒绝请求返回 503，不降级（安全性）
    - JWT 黑名单：内存 LRU 缓存兜底，接受少量 token 失效延迟
    - 配额计数：写入本地队列，Redis 恢复后批量同步
    - 一般缓存：跳过缓存，直接查数据库（限流保护）
- **连接池（Lettuce）**：
  - 连接超时：2000ms
  - 命令超时：1000ms
  - 重连策略：指数退避，最大 30 秒

### 8.4 应用层容错

- **健康检查**：Actuator `/health` 检查 DB、Redis、Nacos 连通性
- **优雅关闭**：`server.shutdown=graceful`，spring 关闭超时 30s
- **限流**：Spring Cloud Alibaba Sentinel，保护下游 DB/Redis 不被突发流量打垮
- **全局异常处理**：统一错误响应格式，不泄露堆栈信息到客户端

---

## 9. 界面设计

### 9.1 客户端 App（独立仓库，原生开发）

ZhiYu 客户端 App 独立于本仓库，采用原生技术：
- **iOS**：Swift + SwiftUI
- **Android**：Kotlin + Jetpack Compose

客户端通过 HTTPS 调用本后端服务的 REST API（除 `/api/v1/admin/**` 外），主要交互页面：
- 登录/注册（多种方式选择、邮箱验证）
- 个人中心（绑定/解绑认证身份、查看订阅状态）
- 套餐选购与支付

本仓库不包含客户端代码，但需确保 API 契约对客户端友好（如清晰的错误码、一致的响应格式）。

### 9.2 管理后台 Web（zhiyu-admin-web）

**技术栈：**
| 项目 | 选型 |
|------|------|
| 框架 | React 18 + TypeScript |
| UI 组件库 | Ant Design 5.x |
| 状态管理 | Zustand（轻量）|
| HTTP 客户端 | Axios + React Query（缓存与请求管理）|
| 图表 | ECharts + echarts-for-react |
| 构建 | Vite |
| 部署 | 构建为静态文件，由 Nginx 托管，反向代理 `/api` 到后端 |

**仓库结构：**

```
zhiyu-admin-web/
├── src/
│   ├── layouts/          # 登录布局、主布局（侧边栏+顶栏）
│   ├── pages/
│   │   ├── login/        # 后台登录（5 种方式 + TOTP + 会话超时）
│   │   ├── dashboard/    # 总览仪表盘（核心指标卡片+趋势图）
│   │   ├── users/        # 用户管理（列表、详情、身份、批量操作、导出）
│   │   ├── subscriptions/# 订阅管理（列表、详情、手动操作）
│   │   ├── payments/     # 支付流水（详情、对账、导出）
│   │   ├── refunds/      # 退款审核（列表、审批）
│   │   ├── audit/        # 审计日志（登录、身份变更、管理员操作）
│   │   ├── admins/       # 后台用户 + 角色管理（仅 SUPER_ADMIN）
│   │   ├── notifications/# 通知模板管理（列表、编辑、测试发送）
│   │   ├── config/       # 系统配置管理（Nacos 可视化编辑）
│   │   ├── my-account/   # 我的账户（密码/TOTP/登录历史/会话管理）
│   │   └── monitor/      # 运行监控
│   │       ├── overview  # 服务健康 + 在线用户 + 今日指标
│   │       ├── metrics   # API QPS/延迟/错误率图表
│   │       ├── logs      # 五类日志检索（访问/应用/慢查询/安全/审计）
│   │       ├── alerts    # 告警面板
│   │       └── settings  # 日志级别调整（仅 SUPER_ADMIN）
│   ├── components/       # 公共组件（权限守卫、表格、搜索表单）
│   ├── hooks/            # 自定义 hooks
│   ├── stores/           # Zustand stores
│   ├── api/              # API 请求封装
│   └── router/           # 路由配置（含权限控制）
└── vite.config.ts
```

**页面设计要点：**

- **登录页**：根据后台用户配置的认证方式动态展示，支持 TOTP 二次验证
- **Dashboard**：实时数据轮询（10s），非关键指标（趋势图）手动刷新
- **用户管理**：表格支持搜索、筛选、分页；详情抽屉展示用户绑定身份列表
- **订阅/支付**：订单列表支持按状态/渠道筛选，支付详情展示原始回调日志
- **审计日志**：只读展示，支持时间范围和关键字搜索
- **监控**：健康状态指示灯（绿/黄/红），指标图表时间范围可切换（1h/6h/24h/7d）
- **权限控制**：前端路由基于 RBAC 权限列表动态注册，无权限的菜单项、按钮不渲染

**管理员"我的账户"页面：**

| 功能 | 说明 |
|------|------|
| 个人信息 | 查看/修改姓名、头像、联系方式 |
| 修改密码 | 需输入当前密码 + 新密码 |
| TOTP 设置 | 初始化/启用/禁用 Google Authenticator |
| 登录历史 | 查看自己的登录记录（时间、IP、设备、位置） |
| 操作日志 | 查看自己执行的管理操作记录 |
| 会话管理 | 查看当前活跃会话，可踢出其他设备的登录 |

**会话超时与自动退出：**

管理后台包含完善的会话生命周期管理，防止敏感操作被滥用和安全风险。

```
时间线：
  用户操作中 ──► 闲置 N 分钟 ──► 倒计时警告 ──► 超时强制退出
  (活跃)         (无鼠标/键盘/触摸)  (60秒倒计时弹窗)  (清空token+跳转登录页)
```

- **空闲检测**：
  - 监听 `mousemove`、`keydown`、`click`、`scroll`、`touchstart` 事件
  - 阈值：**30 分钟无操作**视为空闲（可通过 Nacos 动态调整）
  - 多标签页同步：通过 `BroadcastChannel` 或 `localStorage` 事件同步最后一个活跃时间
  - 排除输入框内连续输入、页面滚动等高频事件中的重复计时器重置

- **倒计时警告**：
  - 空闲 30 分钟后触发 60 秒倒计时弹窗（Modal，不可关闭）
  - 显示："会话即将超时，点击'继续使用'保持登录状态" + 倒计时秒数
  - 用户点击"继续使用"：重置空闲计时器，关闭弹窗，刷新 refresh token
  - 倒计时归零：强制退出

- **强制退出**：
  - 清空内存中的 access_token、refresh_token、用户信息
  - 调用 `POST /api/v1/admin/auth/logout` 将 token 加入黑名单
  - 保存当前页面路径到 `sessionStorage`，下次登录后可跳回（可选）
  - 跳转登录页，显示提示："会话已超时，请重新登录"

- **Token 过期处理**：
  - Axios 响应拦截器捕获 401：
    - 若是 access_token 过期 → 自动调用 refresh 接口换新 token → 重放原请求
    - 若是 refresh_token 也过期 → 视为会话失效，强制退出
  - refresh 期间暂停新请求，队列化避免并发 refresh

- **敏感操作二次验证**：
  - 进入用户管理、订阅管理、后台用户管理页面后，超时阈值缩短为 **15 分钟**
  - 执行关键操作（修改用户状态、手动退款、调整订阅）前验证登录状态

- **审计日志**：
  - 每次超时退出记录到审计日志（操作人、时间、原因：timeout）
  - 手动退出（点击退出按钮）同样记录，区分原因类型

---

## 10. 部署架构

```
┌──────────────────────────────────────────────────────────┐
│                      K8s Cluster                          │
│  ┌─────────────────────────────────────────┐              │
│  │  Ingress / Nginx                         │              │
│  │  /api/*  → zhiyu-backend                │              │
│  │  /admin  → zhiyu-admin-web (静态文件)     │              │
│  └──────────────────┬──────────────────────┘              │
│                     │                                     │
│  ┌──────────────────▼──────────────────────┐              │
│  │  zhiyu-backend (Deployment)             │              │
│  │  replicas: 2+ (HPA 支持)                │              │
│  └──────────────────┬──────────────────────┘              │
│                     │                                     │
│  ┌──────────────────┼───────────┬──────────────────────┐  │
│  ▼                  ▼           ▼                      │  │
│ Nacos Cluster   MySQL M-S   Redis Sentinel            │  │
│ (StatefulSet×3) (主从)      (哨兵×3)                  │  │
└──────────────────────────────────────────────────────────┘
```

## 11. 安全设计

### 11.1 传输安全

- **HTTPS 强制**：Ingress 层 TLS 终结，HSTS 头 `max-age=31536000; includeSubDomains`
- **CORS 策略**：仅允许已知客户端域名，管理后台仅允许管理域名
  - dev：`localhost:*`
  - release：白名单管理后台域名 + App 不涉及 CORS（原生 HTTP）
- **安全响应头**：
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `X-XSS-Protection: 0`（现代浏览器已弃用，不依赖）
  - `Referrer-Policy: strict-origin-when-cross-origin`
  - `Cache-Control: no-store`（含敏感数据的响应）

### 11.2 输入校验

- **Spring Validation**：所有 Controller 入参 DTO 使用 `@Valid` + Jakarta Bean Validation
- **请求体大小限制**：`spring.servlet.multipart.max-file-size=10MB`，普通 JSON body ≤ 1MB
- **参数化查询**：MyBatis-Plus 默认防 SQL 注入，禁止 `${}` 占位符
- **XSS 防护**：输出编码，富文本采用白名单标签过滤（OWASP Java HTML Sanitizer）
- **路径穿越防护**：文件操作必须规范化路径 + 白名单目录校验

### 11.3 暴力破解与账号锁定

```
登录失败计数：Redis key login:fail:<account_type>:<identifier>
              IP 维度计数：login:fail:ip:<ip>

锁定策略：
  同一账号 5 分钟内连续失败 5 次 → 临时锁定 15 分钟
  同一 IP 5 分钟内连续失败 10 次 → IP 级 CAPTCHA 强化
  临时锁定到期自动解除，用户可重试

永久锁定：
  仅管理员手动操作（PUT /admin/users/{id}/status → DISABLED）
  自动锁定均为临时，过期自动恢复

滑动窗口实现：
  使用 Redis Sorted Set，每次失败 ZADD 时间戳
  每次登录前 ZCOUNT 最近 5 分钟窗口内的失败次数
  登录成功后 ZREM 清除该账号/IP 的失败记录
```

### 11.4 登录审计

```
登录日志记录字段：
  user_id, login_type (WECHAT|QQ|SMS|GOOGLE|APPLE|WEBAUTHN|PASSWORD),
  status (SUCCESS|FAIL_WRONG_PWD|FAIL_LOCKED|FAIL_UNKNOWN_USER),
  source_ip, ip_geo (IP 地理位置，离线库),
  device_id, device_name, platform, user_agent,
  request_id, created_at
```

**异常登录检测：**
- 用户在新设备/新地理区域首次登录 → 发送安全通知邮件："您的账户在 XX 地区通过 XX 设备登录"
- 短时间内从不同地理位置登录 → 标记为可疑，发送告警通知
- IP 地理信息使用 GeoLite2 离线库（避免对外部 API 的依赖和隐私问题）

### 11.5 操作审计

**审计事件分类：**

| 事件类型 | 触发操作 | 记录内容 |
|----------|---------|---------|
| USER_LOGIN | 登录成功/失败 | 用户、方式、IP、设备、结果 |
| USER_LOGOUT | 退出/超时 | 用户、方式（手动/超时/被踢） |
| IDENTITY_BIND | 绑定认证方式 | 用户、绑定类型、来源 IP |
| IDENTITY_UNBIND | 解绑认证方式 | 用户、解绑类型、来源 IP |
| PASSWORD_CHANGE | 修改密码 | 用户、来源 IP |
| ACCOUNT_DEACTIVATE | 注销账户 | 用户、来源 IP |
| ACCOUNT_REACTIVATE | 重新激活 | 用户、来源 IP |
| ADMIN_ACTION | 管理员操作 | 操作人、目标用户、动作、变更前后值 |
| CONFIG_CHANGE | 系统配置变更 | 操作人、配置项、旧值→新值 |
| DATA_EXPORT | 数据导出 | 操作人、导出范围、记录数 |

### 11.6 限流策略

| 接口 | 限流维度 | 阈值 | 实现 |
|------|---------|------|------|
| 短信发送 | 手机号 + IP | 1/min，10/day | Redis 滑动窗口 |
| 邮件发送 | 邮箱 + IP | 1/min，20/day | Redis 滑动窗口 |
| 注册 | IP | 3/hour | Redis 计数 + 过期 |
| 登录（所有方式） | 账号 + IP | 见 11.3 锁定策略 | Redis Sorted Set |
| 通用 API | 用户 + API 路径 | 按角色差异化 | Sentinel |
| 支付回调 | 不做限流 | — | 幂等在业务层保证 |
| Admin API | IP 白名单 + 用户 | 按角色差异化 | Sentinel |

### 11.7 令牌安全

- 所有密码 BCrypt 加密（cost factor=12）
- JWT 使用 RS256 非对称签名（Nacos 管理公钥，私钥仅服务端持有）
- 支付回调 URL 使用渠道提供的签名验证，不信任原始数据
- admin 用户与应用用户表分离，权限完全隔离
- Refresh token 盗用检测：旧 token 被重用 → 全设备 session 失效 + 告警通知
- 敏感操作（绑定/解绑认证方式、修改密码、注销账户）需二次验证（action_token）

### 11.8 隐私与合规

- 用户注销 30 天后，个人数据匿名化处理（用户名/邮箱脱敏）
- 支付和审计记录按法律要求保留，但脱去个人标识
- 日志中不记录明文密码、完整手机号/邮箱（脱敏显示：`138****1234`、`u***@domain.com`）
- IP 地理位置仅使用离线库，不向第三方发送用户 IP
