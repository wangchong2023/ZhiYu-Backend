# UFP Auth 模块设计

> 基于 NTA `sfp-auth` 分析，适配 ZhiYu UFP 平台。
> 参考 [DATABASE.md §3.2](../DATABASE.md#32-ufp-auth-库) UFP Auth 表结构（ufp_auth 库，18 张表）。
> 本文档为设计参考，暂不启动开发。

## 1. 概述

`ufp-auth` 是 UFP 统一基础平台的认证授权模块，提供用户身份认证、OIDC 协议、RBAC 授权、条件访问策略等能力。对应 NTA 项目 `sfp-auth`，目标数据库为 `ufp_auth`。

## 2. NTA sfp-auth 分析摘要

NTA `sfp-auth` 采用三层架构：

```
sfp-auth-api (契约层)
├── sfp-auth-basic-api          # 常量、枚举、ResultCode
├── sfp-auth-authorization-api  # 用户/角色/资源/授权/策略模型 + Service 接口
├── sfp-auth-application-api    # 租户/应用/自定义字段/字典模型 + Service 接口
└── sfp-auth-configuration-api  # 配置模型（跳过部署）

sfp-auth-client (客户端 SDK 层)
├── sfp-auth-client-metadata     # AuthenticationToken, GrantedAuthority
├── sfp-auth-client-management   # Feign 代理 + 管理端点 Controller
└── sfp-auth-client-authorization # JWT 解码/过滤器/方法安全/网关过滤器

sfp-auth-service (实现层)
└── sfp-auth-authorization-service  # Spring Boot 应用，全部 Service 实现 + Controller
```

| 能力 | NTA 实现 | ZhiYu 适配 |
|------|---------|:---:|
| 用户认证 | 多因子登录流（密码/短信/邮箱/验证码） | ✅ 保留 |
| OIDC Provider | 完整 OIDC Server（JWT RS256/授权/令牌/用户信息/JWKS/发现端点） | ✅ 保留（P0） |
| RBAC 授权 | auth_role → auth_grant → auth_res | ✅ 保留 |
| 条件访问策略 | auth_grant_policy（IP/时间窗口/频率/地理位置） | ✅ 保留（P1） |
| 组织机构 | 组织树 + 用户归属 | ✅ 保留 |
| API Token | Key/Secret 编程账号 | ✅ 保留 |
| CAS IdSP | 第三方 CAS 身份源代理 | ❌ 移除 |
| 多租户 | auth_tenant/auth_tenant_app 系列表（9 张） | ❌ 已移除 |
| 用户域 | auth_user_boundary/config | ❌ 已移除 |
| 权限域 | auth_res_boundary | ❌ 已移除 |
| 在线用户 | auth_user_online | ⚠️ 按需引入 |

## 3. ufp-auth 模块规划

```
ufp-auth/
├── ufp-auth-api                  # API 契约层
│   ├── ufp-auth-basic-api        #   常量、枚举、错误码
│   ├── ufp-auth-model-api        #   模型 + Service 接口
│   │   ├── model/                #     AuthUser, AuthRole, AuthRes, AuthGrant, ...
│   │   ├── dto/                  #     AuthUserDto, AuthRoleDto, ...
│   │   ├── enums/                #     AuthGrantOwnerTypeEnum, ...
│   │   └── service/              #     IAuthUserService, IAuthRoleService, ...
│   └── ufp-auth-app-api          #   应用/Token 模型 + Service 接口
├── ufp-auth-client               # 客户端 SDK 层
│   ├── ufp-auth-client-metadata  #   AuthenticationToken, GrantedAuthority
│   ├── ufp-auth-client-security  #   JWT 解码器、SecurityFilter、Feign 拦截器、方法安全
│   └── ufp-auth-client-sdk       #   Feign 客户端代理（管理操作）
└── ufp-auth-server               # 服务实现层
    └── ufp-auth-server           #   Spring Boot 应用，全部 Service 实现 + Controller
```

### 3.1 ufp-auth-api

**定位**：纯接口 + 模型定义，零业务逻辑，供客户端和服务端共同依赖。

**ufp-auth-basic-api**（叶子模块）：
- 模块常量 `UfpAuthModules`（URI 前缀、Service Name）
- 业务错误码 `AuthResultCode`
- 通用枚举定义

**ufp-auth-model-api**：
- 用户模型：`AuthUser`, `AuthUserDto`
- 角色模型：`AuthRole`, `AuthRoleDto`
- 资源模型：`AuthRes`, `AuthResDto`
- 授权模型：`AuthGrant`, `AuthGrantDto`
- 策略模型：`AuthGrantPolicy`, `AuthGrantPolicyDto`
- 组织模型：`AuthOrg`, `AuthOrgDto`
- Token 模型：`AuthAccessToken`, `AuthToken`
- 日志模型：`AuthUserLog`, `AuthOperationLog`
- Service 接口（`I` 前缀）：`IAuthUserService`, `IAuthRoleService`, `IAuthResService`, `IAuthGrantService`, `IAuthGrantPolicyService`, `IAuthOrgService`, `IAuthTokenService`, `IAuthLogService`

### 3.2 ufp-auth-client

**定位**：微服务客户端 SDK，供 `zhiyu-server` 等业务模块引入。

**ufp-auth-client-metadata**：
- `AuthenticationToken`：封装认证后的 JWT 信息
- `AuthorizationGrantedAuthority`：封装用户权限列表
- 异常类：`AuthException`, `TokenExpiredException`

**ufp-auth-client-security**：
- `JwtDecoder`：RS256 公钥验证（支持 Nacos 服务发现获取 JWKS）
- `AuthSecurityFilter`：URI 级别权限匹配拦截
- `AuthFeignInterceptor`：服务间调用 Bearer Token 透传
- `@RequirePermission("resCode")`：方法级权限注解
- `AuthGatewayFilterFactory`：Gateway 网关过滤器

**ufp-auth-client-sdk**：
- `AuthUserClient`：Feign 接口 → 远程调用 ufp-auth-server 用户管理 API
- `AuthRoleClient`：Feign 接口 → 角色管理
- `AuthResClient`：Feign 接口 → 资源管理

### 3.3 ufp-auth-server

**定位**：Spring Boot 3.3.x 应用，实现全部认证授权逻辑。

**核心 Controller**（`/api/v1/auth/`）：

| Controller | 端点 | 功能 |
|-----------|------|------|
| `AuthUserController` | `/user` | 用户 CRUD + 密码修改 |
| `AuthRoleController` | `/role` | 角色 CRUD |
| `AuthResController` | `/resource` | 资源树 CRUD |
| `AuthGrantController` | `/grant` | 授权管理（角色→资源映射） |
| `AuthPolicyController` | `/policy` | 条件访问策略管理 |
| `AuthOrgController` | `/org` | 组织架构管理 |
| `AuthTokenController` | `/token` | API Token 管理 |
| `LoginController` | `/login/**` | 登录流（密码/短信/邮箱） |
| `OidcController` | `/oidc/**` | OIDC 协议端点 |
| `CaptchaController` | `/captcha` | 图片/短信验证码 |
| `SessionController` | `/session` | 会话管理 |

**OIDC 协议端点**（无租户简化版）：

| 端点 | 功能 |
|------|------|
| `GET /.well-known/openid-configuration` | OIDC Discovery |
| `GET /oauth2/jwks` | JWKS 公钥端点 |
| `POST /oauth2/authorize` | 授权端点 |
| `POST /oauth2/token` | 令牌端点（返回 access_token + id_token） |
| `GET /oauth2/userinfo` | 用户信息端点 |
| `POST /oauth2/introspect` | 令牌内省端点 |
| `POST /oauth2/revoke` | 令牌吊销端点 |

**认证流程**（简化自 NTA）：

```
Client (前端/业务服务)
    │
    │  1. GET /login/settings    → 获取登录页配置（验证码开关/登录方式列表）
    │  2. POST /captcha/image    → 获取图片验证码
    │  3. POST /login/password   → 用户名+密码+验证码
    │  4. POST /oauth2/authorize → OIDC 授权（返回 authorization_code）
    │  5. POST /oauth2/token     → 换取 access_token (JWT RS256)
    │
    └──> 后续请求携带 Authorization: Bearer <token>
              │
              ├── Gateway Filter: 验证 JWT 签名 + 过期
              ├── AuthFilter: URI 匹配用户权限
              └── @RequirePermission: 方法级权限校验
```

**JWT Claims**（简化版，无 tenant/app）：

```json
{
  "sub": "1001",
  "iss": "https://auth.zhiyu.local",
  "aud": "zhiyu-backend",
  "exp": 1716100000,
  "iat": 1716092800,
  "jti": "uuid",
  "username": "admin",
  "email": "admin@zhiyu.local",
  "scope": "openid profile"
}
```

### 3.4 关键 Service 设计

**`AuthUserService`**：
- `createUser(AuthUserDto)` — 级联创建用户（含 personal/secure/field 扩展）
- `updatePassword(userId, oldPwd, newPwd)` — 密码修改 + 历史检查
- `enableUser(userId)` / `disableUser(userId)` — 启停用户
- `deleteUser(userId)` — 软删除
- 密码策略：BCrypt + 独立盐值 + 历史记录（不可重复 N 次）+ 过期策略

**`AuthGrantService`**：
- `assignGrants(roleId, List<resCode>)` — 批量授权角色→资源
- `revokeGrants(roleId)` — 撤销角色全部授权
- `getUserGrants(userId)` — 获取用户有效权限（通过角色间接）

**`AuthGrantPolicyService`**：
- `evaluatePolicies(owner, source, context)` — 评估策略是否触发
- 支持条件类型：IP 匹配（equals/regex）、时间窗口、频率限制
- 动作类型：允许/拒绝/锁定（时长可配）

## 4. 授权决策流程

```
请求进入
  │
  ├── 1. JWT Filter: 验证签名 + 过期，解析用户ID/用户名
  │
  ├── 2. URI Auth Filter:
  │     ├── 加载用户角色 → 角色关联的 auth_grant
  │     ├── 解析 auth_grant 中的 auth_res 列表
  │     ├── 过滤 resType=API(3) 的资源
  │     └── 匹配请求 URI 与 auth_res_code 模式
  │
  ├── 3. Method Security:
  │     └── @RequirePermission("users.export") → 校验用户是否有此资源授权
  │
  └── 4. Policy Interceptor (AOP):
        ├── 对登录/验证码请求：评估 auth_grant_policy
        └── 触发动作：允许/拒绝/锁定
```

## 5. 安全配置

```yaml
# ufp-auth-server application.yml
zhiyu:
  auth:
    jwt:
      algorithm: RS256
      key-size: 2048
      access-token-ttl: 7200      # 2 小时
      refresh-token-ttl: 604800   # 7 天
      issuer: https://auth.zhiyu.local
    password:
      algorithm: bcrypt
      strength: 10
      history-size: 5              # 不可重复最近 5 次密码
      max-age-days: 180            # 6 个月过期
    login:
      captcha-enabled: true
      max-attempts: 5              # 失败 5 次锁定
      lock-duration: 900           # 锁定 15 分钟
    session:
      max-concurrent: 3            # 最多 3 个并发会话
```

## 6. 与 ZhiYu 现有模块的关系

```
zhiyu-server (入口)
    │
    ├── zhiyu-auth (业务认证 — 调用 ufp-auth 验证用户身份)
    ├── zhiyu-admin (管理后台 — 管理员通过 ufp-auth 登录)
    ├── zhiyu-user (用户中心 — 用户信息通过 ufp-auth 管理)
    └── zhiyu-ufp-auth (基础认证平台 — 独立部署/独立数据库)
         │
         └── ufp_auth 库 (18 张表)
              ├── auth_user ←── zhiyu 库业务表 user_id FK 引用
              ├── auth_role / auth_res / auth_grant
              └── auth_operation_log / auth_user_log
```

## 7. 实施建议

1. **Phase 1**：用户认证 + OIDC Provider + 基础 JWT RS256
2. **Phase 2**：角色/资源/授权管理（auth_role/auth_res/auth_grant CRUD）
3. **Phase 3**：条件访问策略（auth_grant_policy）
4. **Phase 4**：客户端 SDK + Feign 集成
5. 不加租户、不加用户域/权限域、不加 CAS IdSP

## 相关文档

- [DATABASE.md §3.2](../DATABASE.md#32-ufp-auth-库) — UFP Auth 表结构（18 张表，`ufp_auth` 库）
- [DESIGN-UFP-COMMON.md](DESIGN-UFP-COMMON.md) — UFP 共享基础设施（ufp-auth 的底层依赖）
- [ARCHITECTURE.md](../ARCHITECTURE.md) — 系统架构与模块依赖方向
- [DATABASE.md](../DATABASE.md) — 完整数据库设计（含 `zhiyu` 库业务表）
