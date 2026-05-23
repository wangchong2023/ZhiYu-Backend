# ZhiYu P0 认证系统设计规格

> 基于 [brainstorming session] 输出。目标：快速打通用户名+密码注册登录全链路，供 Apple 客户端对接，Admin 管理后台查看注册和登录信息。

**目标：** P0 交付用户名+密码注册/登录/登出/Token刷新 + Admin 仪表盘/用户列表/登录日志

**架构：** ufp-auth（认证基础设施库）→ zhiyu-auth（业务认证 Controller/Service）→ zhiyu-admin（管理后台 API），垂直切片，分批交付

**技术栈：** Java 21 + Spring Boot 3.3.7 + MyBatis-Plus 3.5.10 + Redis + BCrypt + JWT RS256 + Hutool（验证码）+ SpringDoc OpenAPI + Flyway（已有表结构）

---

## 1. 分批交付

| 批次 | 内容 | 产出 |
|------|------|------|
| **P0** | 用户名+密码 注册/登录/登出/Token刷新 | Apple 客户端可调 API + Admin 仪表盘/用户列表/登录日志 |
| **P1** | Apple ID / Google ID / 短信验证码 | 三大主流登录方式就位 |
| **P2** | 微信登录 / WebAuthn 通行密钥 | 全认证方式覆盖 |

---

## 2. 模块架构

```
ufp-auth (平台认证库)           zhiyu-auth (业务认证)           zhiyu-admin (管理后台)
┌─────────────────────┐      ┌─────────────────────┐      ┌─────────────────────┐
│ JwtService           │      │ AuthController       │      │ AdminStatsController  │
│ ├─ issue(payload)    │      │ ├─ POST /register    │      │ ├─ GET /stats/overview│
│ └─ verify(token)     │      │ ├─ POST /login       │      │ ├─ GET /stats/trends  │
│                       │      │ ├─ POST /logout      │      │ └─ GET /stats/dist    │
│ PasswordService      │      │ └─ POST /refresh      │      │                      │
│ ├─ hash(raw)         │      │                       │      │ AdminUserController   │
│ └─ verify(raw,hash)  │      │ CaptchaController     │      │ ├─ GET /users         │
│                       │      │ ├─ GET /config        │      │ ├─ GET /users/{id}    │
│ TokenBlacklist        │      │ └─ GET /image         │      │ ├─ POST /users/{id}   │
│ ├─ add(token,ttl)    │      │                       │      │ │   /enable           │
│ └─ isBlacklisted(tk) │      │ AuthService           │      │ └─ POST /users/{id}   │
│                       │      │ ├─ register(dto)      │      │     /disable          │
│ Entity + Mapper      │      │ ├─ login(dto)         │      │                      │
│ ├─ AuthUser           │      │ ├─ logout(token)      │      │ AdminLogController    │
│ ├─ AuthUserLog        │      │ └─ refresh(token)     │      │ └─ GET /logs/login   │
│ ├─ AuthUserDevice     │      │                       │      │                      │
│ └─ AuthLoginAttempt   │      │ AuthValidator         │      │ AdminAuthController   │
│                       │      │ CaptchaService        │      │ └─ POST /login       │
└─────────────────────┘      └─────────────────────┘      └─────────────────────┘
         ↓ 依赖                        ↓ 依赖                        ↓ 依赖
    ufp-common                    ufp-auth                    zhiyu-subscription
                                  zhiyu-common                zhiyu-user
```

---

## 3. P0 API 端点

所有端点前缀：`/api/v1`

### 3.1 验证码

```
GET /api/v1/auth/captcha/config?sceneId=zhiyu_login
→ { "captchaEnabled": true, "sceneId": "zhiyu_login" }

GET /api/v1/auth/captcha/image?sceneId=zhiyu_login
→ { "captchaToken": "uuid", "captchaImage": "data:image/png;base64,..." }
```

- 验证码 4 位字母数字，130×48，Hutool LineCaptcha
- CaptchaToken + Code 存入 Redis，TTL 5 分钟
- 校验时一次性消费（校验通过即删除 key）

### 3.2 注册 POST /api/v1/auth/register

```
Request:                              Response 200:
{                                     {
  "username": "zhangsan",               "code": 0,
  "password": "Abc123!@#",              "message": "success",
  "email": "z@example.com",             "data": {
  "captchaToken": "xxx",                  "userId": 1001,
  "captchaCode": "A3x9"                   "username": "zhangsan"
}                                       },
                                          "requestId": "uuid",
                                          "timestamp": 1716019200
                                        }
```

| 校验 | 错误码 |
|------|--------|
| 用户名 4-32 位字母数字下划线 | 40001 |
| 密码 8-128 位，至少含大小写字母+数字 | 40001 |
| 邮箱格式合法 | 40001 |
| 验证码正确 | 40109 |
| 用户名/邮箱不重复 | 40901 / 40902 |
| 同 IP 每小时最多 3 次注册 | 42903 |

### 3.3 密码登录 POST /api/v1/auth/login

```
Request:                              Response 200:
{                                     {
  "username": "zhangsan",               "code": 0,
  "password": "Abc123!@#",              "data": {
  "captchaToken": "xxx",                  "accessToken": "eyJ...",
  "captchaCode": "A3x9"                   "refreshToken": "eyJ...",
}                                         "expiresIn": 900,
                                           "tokenType": "Bearer"
Response 200 (需TOTP，P0可返回null):    },
{                                         "requestId": "uuid"
  "code": 0,                            }
  "data": {
    "totpRequired": true,
    "tempToken": "eyJ..."
  }
}
```

| 安全规则 | 阈值 |
|----------|------|
| 连续 3 次密码错误 → 强制验证码 | per account |
| 连续 5 次错误 → 锁定 15 分钟 | per account |
| 单账号 20 次/分钟 | 429 限流 |
| 单 IP 10 次/分钟 | 429 限流 |
| 登录成功 → 计数器重置 | — |

### 3.4 Token 刷新 POST /api/v1/auth/refresh

```
Request:               Response 200:
{                      {
  "refreshToken": "x"    "accessToken": "new",
}                        "refreshToken": "new",
                         "expiresIn": 900
                       }
```

- Refresh Token 7 天有效，一次性使用（轮换后旧 Token 入 Redis 黑名单）

### 3.5 登出 POST /api/v1/auth/logout

```
Request:
Authorization: Bearer <token>

Response 200:
{ "code": 0, "data": { "message": "已登出" } }
```

- accessToken + refreshToken 加入 Redis 黑名单（TTL=各自剩余有效期）

### 3.6 Admin 统计 API

| 端点 | 用途 |
|------|------|
| `GET /api/v1/admin/stats/overview` | 今日注册数/登录数/DAU/登录成功率 |
| `GET /api/v1/admin/stats/register-trend?days=30` | 注册趋势折线图 |
| `GET /api/v1/admin/stats/dau-trend?days=30` | DAU 趋势折线图 |
| `GET /api/v1/admin/stats/login-method-dist?days=30` | 登录方式分布饼图 |

### 3.7 Admin 用户管理 API

| 端点 | 用途 |
|------|------|
| `GET /api/v1/admin/users?page=&size=&keyword=&status=` | 用户分页列表 |
| `GET /api/v1/admin/users/{id}` | 用户详情（含最近 10 条登录记录+设备列表） |
| `POST /api/v1/admin/users/{id}/enable` | 启用用户 |
| `POST /api/v1/admin/users/{id}/disable` | 禁用用户 |

### 3.8 Admin 登录日志 API

| 端点 | 用途 |
|------|------|
| `GET /api/v1/admin/logs/login?page=&size=&timeRange=&username=&type=&result=` | 登录日志分页，支持导出 CSV |

### 3.9 Admin 认证 API

| 端点 | 用途 |
|------|------|
| `POST /api/v1/admin/login` | 管理员登录（用户名+密码，scope=ADMIN 的用户才可登录） |

> P0 不要求 TOTP，TOTP 属于 P2。管理员由 V1.4.1 种子数据创建（admin@zhiyu.local），scope 默认为 `ADMIN`。

### 3.10 SpringDoc OpenAPI

所有 Controller 方法必须带完备注解：
- `@Tag(name = "认证", description = "注册登录相关接口")`
- `@Operation(summary = "用户注册", description = "创建新用户账号")`
- `@Parameter(description = "验证码token", required = true)`
- `@ApiResponse(responseCode = "200", description = "注册成功")`
- `@ApiResponse(responseCode = "40901", description = "用户名已被占用")`

Swagger UI 路径：`/swagger-ui.html`（dev profile 启用）

---

## 4. ufp-auth 核心库

包路径：`com.zhiyu.ufp.auth`

### 4.1 类清单

```
com.zhiyu.ufp.auth
├── jwt/
│   ├── JwtProperties          ← @ConfigurationProperties("zhiyu.auth.jwt")
│   │                             algorithm=RS256, key-size=2048, access-ttl=15m,
│   │                             refresh-ttl=7d, issuer=https://auth.zhiyu.local
│   ├── JwtKeyLoader           ← 从文件系统加载 PEM 私钥/公钥
│   │                             （密钥由 ensure-secrets.sh 预生成，路径由 JwtProperties.key-dir 配置）
│   ├── JwtService             ← issue(userId,username,scope)→JwtPair
│   │                             verify(token)→JwtClaims
│   │                             getUserId(token)→Long
│   └── JwtClaims              ← record(sub, iss, aud, exp, iat, jti, username, scope)
├── password/
│   └── PasswordService        ← hash(raw)→String (BCrypt cost=12)
│                                 verify(raw,hashed)→boolean
├── token/
│   ├── TokenBlacklist         ← add(token,ttlSeconds), isBlacklisted(token)→boolean
│   └── TokenType              ← enum ACCESS, REFRESH
├── entity/
│   ├── AuthUser               ← MyBatis-Plus Entity → auth_user 表（已有 23 列，全映射）
│   ├── AuthUserLog            ← Entity → auth_user_log 表
│   ├── AuthUserDevice         ← Entity → auth_user_device 表
│   └── AuthLoginAttempt       ← Entity → auth_login_attempt 表
├── mapper/
│   ├── AuthUserMapper         ← BaseMapper<AuthUser>
│   ├── AuthUserLogMapper      ← BaseMapper<AuthUserLog>
│   ├── AuthUserDeviceMapper   ← BaseMapper<AuthUserDevice>
│   └── AuthLoginAttemptMapper ← BaseMapper<AuthLoginAttempt>
├── enums/
│   ├── AuthGrantType          ← PASSWORD, SMS, APPLE, GOOGLE, WECHAT, WEB_AUTHN
│   ├── AuthUserStatus         ← ENABLED, DISABLED, DELETED
│   └── LoginResult            ← SUCCESS, FAILED, LOCKED, DISABLED
└── config/
    └── UfpAuthAutoConfiguration ← @AutoConfiguration, 组件扫描 + JwtProperties 绑定
```

### 4.1.1 基础设施组件（zhiyu-common 中）

```
com.zhiyu.common
├── web/
│   ├── ApiResponse<T>         ← 统一响应体 {code, message, data, requestId, timestamp}
│   └── ApiResponseBuilder     ← 静态工厂: success(data), fail(code,msg)
├── exception/
│   ├── BizException           ← extends RuntimeException(code,message)
│   ├── ErrorCode              ← 错误码常量接口
│   └── GlobalExceptionHandler ← @RestControllerAdvice, 统一异常→ApiResponse 映射
└── filter/
    └── JwtAuthFilter          ← OncePerRequestFilter, 从 Authorization Header 解析 JWT,
                                  验证 + 黑名单检查 + 设置 SecurityContext
                                  permitUrls: /auth/register, /auth/login, /auth/captcha/**,
                                  /auth/refresh, /actuator/health, /swagger-ui/**
```

### 4.2 关键接口

```java
// JwtService
JwtPair issue(long userId, String username, String scope);
JwtClaims verify(String token);
Long getUserId(String token);
record JwtPair(String accessToken, String refreshToken, long expiresIn) {}

// PasswordService
String hash(String rawPassword);
boolean verify(String rawPassword, String hashedPassword);

// TokenBlacklist (Redis)
void add(String token, long ttlSeconds);
boolean isBlacklisted(String token);
```

### 4.3 Entity 核心字段（auth_user）

对应 Flyway V1.4.0 DDL，全部 23 列 MyBatis-Plus 自动映射。关键字段：
`auth_user_id`, `auth_user_code`, `auth_user_username`, `auth_user_mail`, `auth_user_mobile`, `auth_user_password`, `auth_user_enable`, `auth_user_deleted`, `auth_user_scope`, `created_user`, `created_time`, `updated_time`

---

## 5. zhiyu-auth 业务认证层

包路径：`com.zhiyu.auth`

### 5.1 类清单

```
com.zhiyu.auth
├── controller/
│   ├── AuthController          ← POST /register, /login, /logout, /refresh
│   └── CaptchaController       ← GET /captcha/config, /captcha/image
├── service/
│   ├── AuthService             ← 注册/登录/登出/刷新 核心编排
│   ├── CaptchaService          ← Hutool LineCaptcha + Redis 存储
│   └── LoginAttemptService     ← 失败计数 + 锁定判断 + 成功重置
├── dto/
│   ├── RegisterRequest         ← username, password, email, captchaToken, captchaCode
│   ├── LoginRequest            ← username, password, captchaToken, captchaCode
│   ├── RefreshRequest          ← refreshToken
│   ├── LoginResponse           ← accessToken, refreshToken, expiresIn, tokenType, totpRequired
│   ├── RegisterResponse        ← userId, username
│   └── CaptchaResponse         ← captchaToken, captchaImage (base64)
├── converter/
│   └── AuthConverter           ← MapStruct: RegisterRequest → AuthUser
├── validator/
│   └── AuthValidator           ← 用户名/密码/邮箱 格式 + 业务规则
└── config/
    └── AuthSecurityConfig      ← Spring Security FilterChain（permit login/register, protect others）
```

### 5.2 关键流程

```
Register:
  1. AuthValidator.validate(request)
  2. CaptchaService.verify(token, code)         ← Redis 一次性消费
  3. AuthUserMapper.selectOne(username) → 查重
  4. AuthUserMapper.selectOne(email) → 查重
  5. Redis INCR("register:ip:"+ip) → 限流 3/hour
  6. hashed = PasswordService.hash(password)
  7. AuthUserMapper.insert(newUser)
  8. → RegisterResponse

Login:
  1. LoginAttemptService.checkLocked(username)  ← 15min 锁定检查
  2. 连续失败≥3 则要求验证码                      ← Redis 计数器
  3. CaptchaService.verify(token, code)          ← 仅当 required
  4. AuthUserMapper.selectOne(username)
  5. PasswordService.verify(raw, hashed)
  6. 失败 → LoginAttemptService.record(FAILED) → 40105
  7. 成功 → LoginAttemptService.clear(username)
  8. JwtService.issue() → JwtPair
  9. AuthUserLogMapper.insert(log)
  10. → LoginResponse

Refresh:
  1. TokenBlacklist.isBlacklisted(refreshToken) → 防重放
  2. JwtService.verify(refreshToken) → claims
  3. TokenBlacklist.add(refreshToken, remainingTTL) ← 旧 token 作废
  4. JwtService.issue() → 新 pair
  5. → LoginResponse

Logout:
  1. 从 Authorization Header 提取 token
  2. TokenBlacklist.add(accessToken, TTL)
  3. TokenBlacklist.add(refreshToken, TTL)
  4. → success
```

### 5.3 验证码实现

```java
// CaptchaService.generate(sceneId)
LineCaptcha lineCaptcha = CaptchaUtil.createLineCaptcha(130, 48, 4, 20);
String code = lineCaptcha.getCode();
String token = UUID.randomUUID().toString();
redis.set("captcha:" + token, code, Duration.ofMinutes(5));
String image = "data:image/png;base64," + lineCaptcha.getImageBase64Data();
return new CaptchaResponse(token, image);
```

### 5.4 SpringDoc OpenAPI 注解

```java
@Tag(name = "认证", description = "注册登录相关接口")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Operation(summary = "用户注册", description = "创建新用户账号，需先获取验证码")
    @ApiResponse(responseCode = "200", description = "注册成功")
    @ApiResponse(responseCode = "40901", description = "用户名已被占用")
    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(
        @Valid @RequestBody RegisterRequest request) { ... }
}
```

---

## 6. zhiyu-admin 管理后台

包路径：`com.zhiyu.admin`

### 6.1 类清单

```
com.zhiyu.admin
├── controller/
│   ├── AdminAuthController     ← POST /admin/login（管理员用户名+密码，scope=ADMIN）
│   ├── AdminStatsController    ← GET /stats/overview, /stats/register-trend,
│   │                               /stats/dau-trend, /stats/login-method-dist
│   ├── AdminUserController     ← GET /users, GET /users/{id},
│   │                               POST /users/{id}/enable, POST /users/{id}/disable
│   └── AdminLogController      ← GET /logs/login（分页+筛选+导出CSV）
├── service/
│   ├── AdminAuthService        ← 管理员认证（verify admin role + TOTP）
│   ├── AdminStatsService       ← 聚合查询（JdbcTemplate/Mapper 统计SQL）
│   ├── AdminUserService        ← 用户启停 + 详情查询
│   └── AdminLogService         ← 登录日志查询
├── dto/
│   ├── StatsOverviewResponse   ← todayRegistrations, todayLogins, dau, loginSuccessRate
│   ├── TrendPoint              ← date, count
│   ├── DistributionItem        ← method, count, percentage
│   ├── AdminUserDto            ← 用户列表行
│   ├── AdminUserDetailDto      ← 用户详情（含最近 10 条登录+设备）
│   └── LoginLogDto             ← 登录日志行
└── converter/
    └── AdminConverter           ← MapStruct: AuthUser → AdminUserDto
```

### 6.2 统计数据查询

```
StatsOverviewService (Redis 缓存 + DB 查询):
  todayRegistrations = COUNT(auth_user WHERE DATE(created_time)=CURDATE())
  todayLogins        = COUNT(auth_user_log WHERE DATE(log_time)=CURDATE() AND action='LOGIN')
  dau                = COUNT(DISTINCT user_id FROM auth_user_log WHERE DATE(log_time)=CURDATE())
  loginSuccessRate   = SUCCESS / TOTAL * 100 (30 day window)

TrendService:
  SELECT DATE(created_time) as date, COUNT(*) as cnt
  FROM auth_user WHERE created_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
  GROUP BY DATE(created_time) ORDER BY date

LoginMethodDistService:
  SELECT log_type, COUNT(*) as cnt
  FROM auth_user_log WHERE log_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
  GROUP BY log_type
```

### 6.3 Admin 认证

管理员登录独立于普通用户，使用 `auth_user` 表中 `scope=ADMIN` 的用户：
1. POST /admin/login { username, password }
2. 验证密码 + scope=ADMIN（P0 不要求 TOTP）
3. 返回 admin JWT（scope=admin）

---

## 7. 前端 Admin 页面（P0）

路由：
- `/admin/login` — 管理员登录
- `/admin/dashboard` — 仪表盘（StatCardRow + ChartRow + PieChart）
- `/admin/users` — 用户列表（SearchBar + DataTable + DetailDrawer）
- `/admin/audit` — 登录日志（FilterBar + DataTable + Export CSV）

四个页面状态统一：Loading（骨架屏）→ Error（重试按钮）→ Empty（引导文案）→ Normal

技术栈：React 18 + Vite + Ant Design 5 + ECharts（图表）+ Axios（API 调用）

---

## 8. 数据流（Apple客户端 → 后端）

```
Apple 客户端                      ZhiYu 后端
    │                               │
    │  GET /auth/captcha/image      │
    │ ──────────────────────────>   │ CaptchaController → Redis 存储 code
    │ <──────── {token, image} ─    │
    │                               │
    │  POST /auth/register          │
    │ ──────────────────────────>   │ AuthController → AuthService.register()
    │ <────── {userId, username} ─   │   └─ BCrypt → INSERT auth_user
    │                               │
    │  POST /auth/login             │
    │ ──────────────────────────>   │ AuthController → AuthService.login()
    │ <── {accessToken, refresh} ─   │   └─ verify → JWT → INSERT log
    │                               │
    │  GET /user/profile            │
    │  Authorization: Bearer <jwt>  │
    │ ──────────────────────────>   │ JwtFilter → JwtService.verify() → userId
    │ <──────── {user profile} ──   │
```

---

## 9. 错误码（P0）

| 错误码 | 含义 |
|--------|------|
| 40001 | 参数校验失败 |
| 40101 | 未认证（Token 缺失或无效） |
| 40102 | Token 已过期 |
| 40103 | Token 已被吊销（在黑名单中） |
| 40105 | 用户名或密码错误 |
| 40106 | 账号已被临时锁定 |
| 40107 | 账号已被禁用 |
| 40108 | 账号已注销 |
| 40109 | 验证码错误 |
| 40110 | 验证码已过期 |
| 40111 | 需要验证码 |
| 40301 | 无权限（非 ADMIN scope） |
| 40901 | 用户名已被占用 |
| 40902 | 邮箱已被注册 |
| 42901 | 请求过于频繁（通用） |
| 42902 | 登录频率超限 |
| 42903 | 注册频率超限 |

---

## 10. 配置

```yaml
zhiyu:
  auth:
    jwt:
      algorithm: RS256
      key-size: 2048
      access-token-ttl: 15m
      refresh-token-ttl: 7d
      issuer: https://auth.zhiyu.local
    password:
      bcrypt-cost-factor: 12
    login:
      max-attempts: 5
      lock-duration: 15m
      window-duration: 5m
      captcha-after-failures: 3
    register:
      max-per-ip-per-hour: 3
```

---

## 11. 测试策略

| 层级 | 工具 | 覆盖目标 |
|------|------|---------|
| 单元测试 | JUnit 5 + Mockito | AuthService, CaptchaService, LoginAttemptService, PasswordService, JwtService (≥80%) |
| 集成测试 | Spring Boot Test + Testcontainers (MySQL + Redis) | AuthController, AdminUserController, 完整注册→登录→刷新→登出流程 |
| API 文档 | SpringDoc OpenAPI | 所有端点，供 Apple 客户端团队查阅 |

测试文件命名：`*Test.java`（单元）、`*IT.java`（集成）
