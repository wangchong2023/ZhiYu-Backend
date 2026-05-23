# 第三方登录 + WebAuthn 通行认证 — 设计规格

> **目标**：为 ZhiYu 平台添加微信、Apple ID、Google 账号的 OAuth 第三方登录，以及 WebAuthn/Passkey 通行密钥认证支持。纯后端 API，不涉及前端。

## 1. 架构概览

```
zhiyu-auth (业务认证层)
  ├── OAuthController  ──→ OAuthService  ──→ OAuthProviderFactory
  │                                              ├── WechatOAuthProvider
  │                                              ├── AppleOAuthProvider
  │                                              └── GoogleOAuthProvider
  └── WebAuthnController ──→ WebAuthnService ──→ ufp-auth WebAuthn lib

ufp-auth (认证库)
  ├── OAuth Provider 抽象接口 + 通用 DTO
  └── WebAuthn 核心逻辑（yubico webauthn-server-core）
```

**关键决策**：
- OAuth Provider 抽象定义在 `ufp-auth`（库级别），具体实现在 `zhiyu-auth`（业务级别），因为不同 Provider 需要不同的 HTTP 客户端配置和业务特定逻辑
- WebAuthn 核心逻辑在 `ufp-auth`（基于 yubico 库），`zhiyu-auth` 仅提供 Controller 编排

## 2. OAuth 第三方登录

### 2.1 数据流

```
客户端                      后端 API                    第三方平台
  │                           │                           │
  ├─ 第三方授权 ──────────────────────────────────────▶│
  │                           │                           │
  ├─ POST /api/v1/auth/oauth/{provider}                  │
  │   {code, state}           │                           │
  │                           ├─ OAuthProvider.authorize(code)
  │                           │   ├─ 用 code 换 access_token
  │                           │   ├─ 用 access_token 换 userinfo
  │                           │   └─ 返回 OAuthUserInfo
  │                           │
  │                           ├─ 账户匹配策略（见 §2.3）
  │                           │
  │                           ├─ 签发 JWT Pair
  │                           │
  │◀── {accessToken, refreshToken, isNewUser} ──────────┤
```

### 2.2 API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/oauth/wechat` | 微信授权码登录 |
| POST | `/api/v1/auth/oauth/apple` | Apple ID 授权码登录 |
| POST | `/api/v1/auth/oauth/google` | Google 授权码登录 |

统一请求体：
```json
{
  "code": "authorization_code",
  "state": "optional_state",
  "idToken": "apple_or_google_id_token_when_applicable"
}
```

统一响应体：
```json
{
  "code": 0,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "expiresIn": 900,
    "tokenType": "Bearer",
    "isNewUser": false
  }
}
```

### 2.3 账户匹配策略（PRD §6.2 定义）

```
第三方登录 → 
  1. provider + openid 匹配已有 auth_user_identity？
     → YES: 直接用该 auth_user 登录
     → NO:  继续
  2. OAuth 返回 email + 该 email 匹配已有 auth_user_mail？
     → YES: 返回 needBind=true，提示用户用密码登录后绑定
     → NO:  继续
  3. 无匹配 → 创建新用户：
     - auth_user (scope = LIMITED)
     - auth_user_identity (provider, openid, nickname, avatar_url)
     - 返回 isNewUser=true，引导绑定邮箱升级 FULL
```

**错误码**：
| 错误码 | 含义 |
|--------|------|
| 40112 | 授权码无效或已过期 |
| 40113 | 第三方平台返回错误 |
| 40903 | 该邮箱已注册，请用密码登录后绑定第三方账号 |
| 40904 | 该第三方账号已绑定其他用户 |

### 2.4 OAuthProvider 接口设计

```java
// 定义在 ufp-auth
public interface OAuthProvider {
    String getProviderName();  // "WECHAT" | "APPLE" | "GOOGLE"
    OAuthUserInfo authorize(OAuthRequest request) throws BizException;
}

// 通用 DTO
public record OAuthUserInfo(String openid, String unionid,
                             String nickname, String avatarUrl,
                             String email, boolean emailVerified) {}
public record OAuthRequest(String code, String state, String idToken) {}
```

### 2.5 Provider 配置

```yaml
zhiyu:
  auth:
    oauth:
      wechat:
        app-id: ${WECHAT_APP_ID:}
        app-secret: ${WECHAT_APP_SECRET:}
        redirect-uri: https://zhiyu.local/oauth/wechat/callback
      apple:
        client-id: ${APPLE_CLIENT_ID:}
        team-id: ${APPLE_TEAM_ID:}
        key-id: ${APPLE_KEY_ID:}
        private-key: ${APPLE_PRIVATE_KEY:}
      google:
        client-id: ${GOOGLE_CLIENT_ID:}
        client-secret: ${GOOGLE_CLIENT_SECRET:}
```

## 3. WebAuthn/Passkey 通行认证

### 3.1 核心流程

```
注册通行密钥：
  POST /api/v1/auth/webauthn/register/begin
    → 返回 PublicKeyCredentialCreationOptions（challenge 存 Redis）
  POST /api/v1/auth/webauthn/register/finish
    → 验证 attestation，存储 credential_id + public_key + sign_count

认证（登录）：
  POST /api/v1/auth/webauthn/authenticate/begin
    → 返回 PublicKeyCredentialRequestOptions（含 allowCredentials）
  POST /api/v1/auth/webauthn/authenticate/finish
    → 验证 assertion，更新 sign_count，签发 JWT
```

### 3.2 API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/webauthn/register/begin` | 开始注册（需登录） |
| POST | `/api/v1/auth/webauthn/register/finish` | 完成注册 |
| POST | `/api/v1/auth/webauthn/authenticate/begin` | 开始认证（无需登录） |
| POST | `/api/v1/auth/webauthn/authenticate/finish` | 完成认证，返回 JWT |

### 3.3 数据存储

使用已有的 `auth_user_web_authn` 表（V1.5.0）：
- `credential_id`：唯一标识
- `public_key`：公钥（COSE 格式）
- `sign_count`：签名计数器，每次认证递增，值 ≤ 当前值的请求拒绝（防重放）

### 3.4 依赖

- `com.yubico:webauthn-server-core:2.5.0` — 已在架构文档中规划
- Redis：存储 challenge（5 分钟 TTL）

## 4. 模块分工

| 层 | 模块 | 内容 |
|----|------|------|
| 接口 + 通用 DTO | `ufp-auth` | `OAuthProvider`, `OAuthUserInfo`, `OAuthRequest`, `WebAuthnService` 接口 |
| 业务实现 | `zhiyu-auth` | `WechatOAuthProvider`, `AppleOAuthProvider`, `GoogleOAuthProvider`, `OAuthService`, `WebAuthnController` |
| 实体 + Mapper | `ufp-auth` | 已有 `AuthUserIdentity` + `AuthUserWebAuthn` 实体及 Mapper |

## 5. 测试策略

- **单元测试**：每个 Provider 的 authorize 逻辑（Mock HTTP 客户端）
- **集成测试**：OAuth 账户匹配策略的 3 条路径、WebAuthn ceremony 流程
- **覆盖率目标**：80%+
