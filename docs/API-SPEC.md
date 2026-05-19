# ZhiYu-Backend API 接口规格

> 本文档是从 [设计规格](superpowers/specs/2026-05-17-zhiyu-backend-design.md) 提取并补全的接口级规格，定义每个 API 的请求/响应格式、校验规则和可能的错误码。开发时必须严格按本文档实现。

## 通用约定

### 认证

```
Authorization: Bearer <access_token>
```

access_token 通过登录接口获取，15分钟有效。过期后用 `/auth/refresh` 换新，无需重新登录。

### 响应格式

所有接口返回统一 envelope（详见 [DEVELOPMENT-STANDARDS.md](../DEVELOPMENT-STANDARDS.md#4-api-响应格式规范)）：

```json
{ "code": 0, "message": "success", "data": {...}, "requestId": "uuid", "timestamp": 1716019200 }
```

### 分页参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| page | int | 1 | 页码，从 1 开始 |
| size | int | 20 | 每页条数，最大 100 |

分页响应 `data` 格式：

```json
{ "records": [...], "total": 1523, "page": 1, "size": 20, "pages": 77 }
```

### 错误码速查

完整错误码定义见 [DEVELOPMENT-STANDARDS.md](../DEVELOPMENT-STANDARDS.md#5-错误码体系)。各接口可能返回的错误码见每节末尾。

### 公共校验规则

| 字段 | 规则 |
|------|------|
| username | 3-32 位，仅允许 `[a-zA-Z0-9_-]` |
| password | 8-128 位，至少含大写字母、小写字母、数字、特殊字符中的 3 类 |
| email | RFC 5322 格式，最长 254 字符 |
| phone | E.164 格式 `+8613812345678` 或国内格式 `13812345678` |
| captchaToken | 服务端下发的验证码 token，5 分钟有效 |

---

## 1. 认证接口 `/auth/**`

### 1.1 发送邮箱验证码

```
POST /api/v1/auth/send-register-code
权限: 无（需先通过 CAPTCHA）
```

**请求体：**
```json
{
  "email": "user@example.com",
  "captchaToken": "xxx",
  "captchaType": "ALIYUN_SLIDE"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| email | string | ✅ | `@Email`，最长 254 |
| captchaToken | string | ✅ | 非空 |
| captchaType | string | ✅ | 枚举: `ALIYUN_SLIDE` |

**成功响应** `200`：
```json
{
  "code": 0,
  "message": "success",
  "data": { "expireMinutes": 5, "retryAfterSeconds": 60 }
}
```

**可能错误：** 40001 参数校验 | 40111 CAPTCHA 未通过 | 42903 IP 限流（1/min per email, 20/day per email）

---

### 1.2 注册

```
POST /api/v1/auth/register
权限: 无
```

**请求体：**
```json
{
  "username": "testuser",
  "password": "SecureP@ss1",
  "email": "user@example.com",
  "verifyCode": "123456",
  "captchaToken": "xxx",
  "captchaType": "ALIYUN_SLIDE"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| username | string | ✅ | 公共规则 |
| password | string | ✅ | 公共规则 |
| email | string | ✅ | 公共规则 |
| verifyCode | string | ✅ | 6 位数字 |
| captchaToken | string | ✅ | 非空 |
| captchaType | string | ✅ | 枚举: `ALIYUN_SLIDE` |

**成功响应** `201`：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": 1001,
    "username": "testuser",
    "email": "user@example.com",
    "emailVerified": true,
    "accessToken": "eyJ...",
    "refreshToken": "d4e5f6...",
    "expiresIn": 900
  }
}
```

**可能错误：** 40001 | 40109 验证码错误 | 40111 | 40901 用户名占用 | 40902 邮箱已注册 | 42903 IP 注册限流（3/hour per IP）

---

### 1.3 密码登录

```
POST /api/v1/auth/login
权限: 无
```

**请求体：**
```json
{
  "account": "testuser",
  "password": "SecureP@ss1",
  "captchaToken": "xxx",
  "captchaType": "ALIYUN_SLIDE"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| account | string | ✅ | 用户名/邮箱/手机号 |
| password | string | ✅ | 公共规则 |
| captchaToken | string | ❌ | 连续 3 次失败后必填 |
| captchaType | string | ❌ | 与 captchaToken 配对 |

**成功响应** `200`：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": 1001,
    "username": "testuser",
    "email": "user@example.com",
    "phone": "+8613812345678",
    "accessToken": "eyJ...",
    "refreshToken": "d4e5f6...",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

**可能错误：** 40001 参数校验 | 40105 密码错误 | 40106 账号临时锁定（连续 5 次失败后锁定 15 分钟）| 40107 账号已禁用 | 40108 账号已注销 | 40111 CAPTCHA 未通过 | 42903 登录频率限制（10/min per IP, 20/min per account）

**登录结果响应头：**
| 响应头 | 说明 |
|--------|------|
| `X-Login-Attempts-Remaining` | 锁定前剩余尝试次数（失败时返回） |
| `X-Login-Locked-Until` | 锁定解除时间 Unix 时间戳（锁定时返回） |

> **安全机制**：连续 3 次密码错误触发 CAPTCHA，连续 5 次锁定 15 分钟。成功登录后计数器重置。登录失败/成功均写入 `login_attempt` 表和 `audit_log`（event_type=`USER_LOGIN`）。

---

### 1.4 检查可用性

```
POST /api/v1/auth/check-availability
权限: 无
```

**请求体：**
```json
{
  "type": "EMAIL",
  "value": "user@example.com"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| type | string | ✅ | 枚举: `USERNAME` / `EMAIL` / `PHONE` |
| value | string | ✅ | 按 type 校验格式 |

**成功响应** `200`：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "available": true,
    "suggestions": []
  }
}
```

不可用时：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "available": false,
    "message": "该邮箱已被注册，是否去登录？",
    "suggestions": ["testuser_01", "testuser_42"]
  }
}
```

---

### 1.5 第三方登录

```
POST /api/v1/auth/third-party
权限: 无
```

**请求体：**
```json
{
  "type": "WECHAT",
  "code": "081abc...",
  "state": "csrf-state-token"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| type | string | ✅ | 枚举: `WECHAT` / `QQ` / `GOOGLE` / `APPLE` |
| code | string | ✅ | 平台返回的授权码 |
| state | string | ✅ | 服务端下发的 CSRF state（Redis 存储） |

**成功响应** `200`（已有用户）：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": 1001,
    "accessToken": "eyJ...",
    "refreshToken": "d4e5f6...",
    "expiresIn": 900,
    "emailVerified": true,
    "scope": "FULL",
    "isNewUser": false
  }
}
```

新用户（未绑邮箱）：
```json
{
  "code": 0,
  "data": {
    "userId": 1002,
    "accessToken": "eyJ...",
    "refreshToken": "d4e5f6...",
    "expiresIn": 900,
    "emailVerified": false,
    "scope": "LIMITED",
    "isNewUser": true
  }
}
```

**可能错误：** 40001 | 40115 第三方授权失败（code 无效/过期）| 40116 state 参数无效或过期（防 CSRF）

---

### 1.6 第三方用户绑定邮箱

```
POST /api/v1/auth/third-party/bind-email
权限: access_token (scope=LIMITED 即可)
```

**请求体：**
```json
{
  "email": "user@example.com",
  "verifyCode": "123456"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| email | string | ✅ | 公共规则 |
| verifyCode | string | ✅ | 6 位数字 |

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "emailVerified": true,
    "scope": "FULL"
  }
}
```

**可能错误：** 40001 | 40101 | 40109 | 40902 邮箱已被绑定 | 40302 scope 不满足（非 LIMITED 用户调用时诡异但无害，返回 400）

---

### 1.7 短信验证码发送

```
POST /api/v1/auth/sms/send
权限: 无
```

**请求体：**
```json
{
  "phone": "13812345678",
  "captchaToken": "xxx",
  "captchaType": "ALIYUN_SLIDE"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| phone | string | ✅ | 公共规则 |
| captchaToken | string | ✅ | |
| captchaType | string | ✅ | |

**成功响应** `200`：
```json
{ "code": 0, "data": { "expireMinutes": 5, "retryAfterSeconds": 60 } }
```

**可能错误：** 40001 | 40111 | 42901(IP) 短信发送频率超限（1/min per phone, 10/day per phone）

---

### 1.8 短信验证码登录

```
POST /api/v1/auth/sms/login
权限: 无
```

**请求体：**
```json
{
  "phone": "13812345678",
  "verifyCode": "123456"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| phone | string | ✅ | |
| verifyCode | string | ✅ | 6 位数字 |

**成功响应** `200`（已有用户/自动创建用户）：
```json
{
  "code": 0,
  "data": {
    "userId": 1001,
    "accessToken": "eyJ...",
    "refreshToken": "d4e5f6...",
    "expiresIn": 900,
    "isNewUser": false
  }
}
```

**可能错误：** 40001 | 40109 | 40114 短信验证码错误 | 40106 账号锁定

---

### 1.9 WebAuthn 注册 — 开始

```
POST /api/v1/auth/webauthn/register/begin
权限: access_token (scope=FULL)
```

**请求体：** 无（用户信息从 JWT 提取）

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "challenge": "base64url-challenge",
    "rp": { "name": "ZhiYu", "id": "zhiyu.app" },
    "user": { "id": "base64url-userId", "name": "testuser", "displayName": "testuser" },
    "pubKeyCredParams": [{ "type": "public-key", "alg": -7 }],
    "timeout": 60000,
    "attestation": "none"
  }
}
```

---

### 1.10 WebAuthn 注册 — 完成

```
POST /api/v1/auth/webauthn/register/finish
权限: access_token (scope=FULL)
```

**请求体：** WebAuthn `AuthenticatorAttestationResponse` JSON

```json
{
  "id": "credential-id",
  "rawId": "base64url...",
  "response": {
    "clientDataJSON": "base64url...",
    "attestationObject": "base64url..."
  },
  "type": "public-key"
}
```

**成功响应** `200`：
```json
{ "code": 0, "data": { "credentialId": "cred-xxx", "createdAt": "2026-05-18T10:30:00Z" } }
```

**可能错误：** 40001 | 40101 | 40116 WebAuthn 验证失败（attestation 不合法）

---

### 1.11 WebAuthn 认证 — 开始

```
POST /api/v1/auth/webauthn/auth/begin
权限: 无
```

**请求体：**
```json
{ "username": "testuser" }
```

| 字段 | 类型 | 必填 |
|------|------|:--:|
| username | string | ❌ (无用户名则返回通用 challenge) |

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "challenge": "base64url-challenge",
    "allowCredentials": [{ "id": "cred-xxx", "type": "public-key" }],
    "timeout": 60000
  }
}
```

---

### 1.12 WebAuthn 认证 — 完成

```
POST /api/v1/auth/webauthn/auth/finish
权限: 无
```

**请求体：** WebAuthn `AuthenticatorAssertionResponse` JSON，结构同 1.9 但 response 含 `authenticatorData` + `signature`

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "userId": 1001,
    "accessToken": "eyJ...",
    "refreshToken": "d4e5f6...",
    "expiresIn": 900
  }
}
```

**可能错误：** 40001 | 40116 WebAuthn 认证失败

---

### 1.13 忘记密码

```
POST /api/v1/auth/forgot-password
权限: 无
```

**请求体：**
```json
{
  "email": "user@example.com",
  "captchaToken": "xxx",
  "captchaType": "ALIYUN_SLIDE"
}
```

| 字段 | 类型 | 必填 |
|------|------|:--:|
| email | string | ✅ |
| captchaToken | string | ✅ |
| captchaType | string | ✅ |

**成功响应** `200`：
```json
{ "code": 0, "data": { "message": "重置链接已发送至邮箱" } }
```

> 无论邮箱是否存在，始终返回 200（防止邮箱枚举攻击）。仅存在时才发邮件。

**可能错误：** 40001 | 40111 | 42901(IP) 5 分钟内仅允许 1 次

---

### 1.14 重置密码

```
POST /api/v1/auth/reset-password
权限: 无
```

**请求体：**
```json
{
  "token": "abc-def-123...",
  "newPassword": "NewSecureP@ss2"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| token | string | ✅ | 重置 token（来自邮件链接） |
| newPassword | string | ✅ | 公共规则，不可与用户名/邮箱相同 |

**成功响应** `200`：
```json
{ "code": 0, "data": { "message": "密码重置成功，请重新登录" } }
```

**可能错误：** 40001 | 40117 token 无效/过期 | 40118 密码与用户名或邮箱相同

---

### 1.15 找回用户名

```
POST /api/v1/auth/forgot-username
权限: 无
```

**请求体：**
```json
{ "email": "user@example.com" }
```

**成功响应** `200`：
```json
{ "code": 0, "data": { "message": "用户名提醒已发送至邮箱" } }
```

> 与忘记密码相同，始终返回 200。

---

### 1.16 刷新 Token

```
POST /api/v1/auth/refresh
权限: 无（校验 refresh_token）
```

**请求体：**
```json
{ "refreshToken": "d4e5f6..." }
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "accessToken": "eyJ...new...",
    "refreshToken": "d4e5f6...new...",
    "expiresIn": 900
  }
}
```

> 使用后旧 refresh_token 立即失效（轮换）。检测到旧 token 被重用 → 全设备失效。

**可能错误：** 40001 | 40103 refresh_token 过期 | 40104 refresh_token 被重用（盗用检测触发，全设备登出）

---

### 1.17 退出登录

```
POST /api/v1/auth/logout
权限: access_token
```

**请求体：** 无

**成功响应** `200`：
```json
{ "code": 0, "data": { "message": "已退出" } }
```

> 仅下线当前设备，不影响同用户其他设备。access_token 加入 Redis 黑名单。

---

### 1.18 设备列表

```
GET /api/v1/auth/devices
权限: access_token (scope=FULL)
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "devices": [
      {
        "deviceId": "ab12cd34-ab12...",
        "deviceName": "iPhone 16 Pro",
        "platform": "IOS",
        "lastActiveAt": "2026-05-18T10:30:00Z",
        "current": true
      }
    ]
  }
}
```

---

### 1.19 踢出设备

```
DELETE /api/v1/auth/devices/{deviceId}
权限: access_token (scope=FULL)
```

**路径参数：** `deviceId` — 设备 UUID

**成功响应** `200`：
```json
{ "code": 0, "data": { "message": "设备已下线" } }
```

**可能错误：** 40401 设备不存在

---

### 1.20 二次验证 — 获取 Action Token

```
POST /api/v1/auth/action-verify
权限: access_token
```

**请求体：**
```json
{
  "type": "PASSWORD",
  "credential": "CurrentP@ss1"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| type | string | ✅ | 枚举: `PASSWORD` / `SMS` / `TOTP` |
| credential | string | ✅ | 按 type |

**成功响应** `200`：
```json
{ "code": 0, "data": { "actionToken": "xy12zw34...", "expiresIn": 300 } }
```

**可能错误：** 40001 | 40105（PASSWORD 错误）| 40112（TOTP 错误）| 40114（SMS 错误）

---

### 1.21 账户恢复申请

```
POST /api/v1/auth/account-recovery/apply
权限: 无
```

**请求体：**
```json
{
  "email": "user@example.com",
  "phone": "13812345678",
  "username": "testuser",
  "reason": "丢失所有设备，无法登录"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| email | string | ❌ | 至少一项 |
| phone | string | ❌ | 至少一项 |
| username | string | ❌ | 至少一项 |
| reason | string | ✅ | 最长 500 字符 |

**成功响应** `200`：
```json
{ "code": 0, "data": { "ticketId": "ticket-001", "expiresAt": "2026-05-25T00:00:00Z" } }
```

**可能错误：** 40001 三项至少填一项 | 40401 信息不匹配任何用户

---

### 1.22 TOTP 初始化

```
POST /api/v1/auth/totp/setup
权限: access_token (scope=FULL)
```

**请求体：** 无

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "secret": "JBSWY3DPEHPK3PXP",
    "qrCodeUrl": "otpauth://totp/ZhiYu:testuser?secret=JBSWY3DPEHPK3PXP&issuer=ZhiYu",
    "qrCodeDataUrl": "data:image/png;base64,..."
  }
}
```

---

### 1.23 TOTP 启用

```
POST /api/v1/auth/totp/enable
权限: access_token (scope=FULL)
```

**请求体：**
```json
{ "code": "123456" }
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| code | string | ✅ | 6 位数字 TOTP 码 |

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "enabled": true,
    "recoveryCodes": ["12345678", "23456789", "34567890", "45678901", "56789012"]
  }
}
```

> 恢复码仅返回一次，建议用户妥善保存。

**可能错误：** 40001 | 40112 TOTP 码错误

---

### 1.24 TOTP 禁用

```
POST /api/v1/auth/totp/disable
权限: access_token (需要 action_token)
```

**请求头：** `X-Action-Token: xy12zw34...`

**请求体：**
```json
{ "code": "123456" }
```

**成功响应** `200`：
```json
{ "code": 0, "data": { "enabled": false } }
```

**可能错误：** 40001 | 40112 | 40304 action_token 无效

---

### 1.25 TOTP 状态查询

```
GET /api/v1/auth/totp/status
权限: access_token
```

**成功响应** `200`：
```json
{ "code": 0, "data": { "enabled": true } }
```

---

## 2. 用户接口 `/user/**`

> 所有 `/user/**` 接口（除 `/user/bind-email` 外）要求 access_token + scope=FULL。

### 2.1 绑定邮箱

```
POST /api/v1/user/bind-email
权限: access_token (scope=LIMITED 即可)
```

**请求体：**
```json
{
  "email": "new@example.com",
  "verifyCode": "123456"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| email | string | ✅ | 公共规则 |
| verifyCode | string | ✅ | 6 位数字 |

**成功响应** `200`：
```json
{ "code": 0, "data": { "email": "new@example.com", "emailVerified": true } }
```

**可能错误：** 40001 | 40109 | 40902

---

### 2.2 绑定手机号

```
POST /api/v1/user/bind-phone
权限: access_token (scope=FULL, 需要 action_token)
```

**请求头：** `X-Action-Token: xy12zw34...`

**请求体：**
```json
{
  "phone": "13812345678",
  "verifyCode": "123456"
}
```

**成功响应** `200`：
```json
{ "code": 0, "data": { "phone": "138****5678", "phoneVerified": true } }
```

**可能错误：** 40001 | 40109 | 40304 | 40903

---

### 2.3 绑定微信 / QQ / Google / Apple

```
POST /api/v1/user/bind-wechat
POST /api/v1/user/bind-qq
POST /api/v1/user/bind-google
POST /api/v1/user/bind-apple
权限: access_token (scope=FULL, 需要 action_token)
```

**请求头：** `X-Action-Token: xy12zw34...`

**请求体（以微信为例）：**
```json
{
  "code": "081abc...",
  "state": "csrf-state-token"
}
```

**成功响应** `200`：
```json
{ "code": 0, "data": { "identityType": "WECHAT", "identifier": "wx_openid_***", "createdAt": "..." } }
```

**可能错误：** 40001 | 40115 | 40304 | 40904 该认证方式已绑定

---

### 2.4 解绑认证方式

```
POST /api/v1/user/unbind/{identityId}
权限: access_token (scope=FULL, 需要 action_token)
```

**路径参数：** `identityId` — 认证身份 ID

**请求头：** `X-Action-Token: xy12zw34...`

**成功响应** `200`：
```json
{ "code": 0, "data": { "message": "已解绑" } }
```

**可能错误：** 40401 identity 不存在 | 40031 不能解绑最后一个认证方式 | 40304

---

### 2.5 获取个人信息

```
GET /api/v1/user/profile
权限: access_token (scope=FULL)
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "userId": 1001,
    "username": "testuser",
    "email": "u***@example.com",
    "emailVerified": true,
    "phone": "138****5678",
    "phoneVerified": false,
    "nickname": "Test User",
    "avatarUrl": "https://oss.zhiyu.app/avatars/1001.jpg",
    "status": "ACTIVE",
    "createdAt": "2026-01-15T08:30:00Z"
  }
}
```

> 敏感字段（email, phone）在响应中脱敏。

---

### 2.6 更新个人信息

```
PUT /api/v1/user/profile
权限: access_token (scope=FULL)
```

**请求体：**
```json
{
  "nickname": "New Nick",
  "avatarUrl": "https://oss.zhiyu.app/avatars/1001-new.jpg"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| nickname | string | ❌ | 最长 64 字符 |
| avatarUrl | string | ❌ | 最长 512 字符，必须为 OSS URL |

**成功响应** `200`：
```json
{ "code": 0, "data": { "nickname": "New Nick", "avatarUrl": "..." } }
```

---

### 2.7 注销账户

```
POST /api/v1/user/deactivate
权限: access_token (scope=FULL, 需要 action_token)
```

**请求头：** `X-Action-Token: xy12zw34...`

**成功响应** `200`：
```json
{ "code": 0, "data": { "message": "账户已注销，30 天内可恢复", "reactivateDeadline": "2026-06-17T00:00:00Z" } }
```

> 立即清空所有 token。

**可能错误：** 40304 | 40033 已注销（30 天内重复操作）

---

### 2.8 重新激活账户

```
POST /api/v1/user/reactivate
权限: 无
```

**请求体：**
```json
{
  "email": "user@example.com",
  "verifyCode": "123456"
}
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": { "message": "账户已恢复", "userId": 1001, "status": "ACTIVE" }
}
```

**可能错误：** 40001 | 40109 | 40034 超过 30 天恢复期 | 40401 用户不存在

---

### 2.9 获取账户设置

```
GET /api/v1/user/settings
权限: access_token
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "notificationPrefs": { "email": true, "push": true, "sms": false },
    "privacy": { "showOnlineStatus": true },
    "language": "zh-CN",
    "timezone": "Asia/Shanghai"
  }
}
```

---

### 2.10 更新账户设置

```
PUT /api/v1/user/settings
权限: access_token
```

**请求体：** (与 GET 返回结构相同，部分更新)

```json
{
  "notificationPrefs": { "email": true, "push": false, "sms": false },
  "privacy": { "showOnlineStatus": false }
}
```

**成功响应** `200`：返回更新后的完整 settings。

---

## 3. 订阅/支付接口 `/sub/**`

### 3.1 套餐列表

```
GET /api/v1/sub/plans
权限: 无
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "plans": [
      {
        "planKey": "free",
        "name": "免费游客",
        "priceMonthly": 0,
        "priceYearly": 0,
        "features": ["basic_chat", "text_search"],
        "quotas": { "daily_chat": 10, "file_upload_mb": 5 },
        "trialDays": 0
      },
      {
        "planKey": "lite",
        "name": "Lite",
        "priceMonthly": 29,
        "priceYearly": 290,
        "features": ["basic_chat", "text_search", "file_upload", "image_gen"],
        "quotas": { "daily_chat": 200, "file_upload_mb": 50, "image_gen_daily": 20 },
        "trialDays": 7
      },
      {
        "planKey": "pro",
        "name": "Pro",
        "priceMonthly": 99,
        "priceYearly": 990,
        "features": ["basic_chat", "text_search", "file_upload", "image_gen", "priority_queue"],
        "quotas": { "daily_chat": -1, "file_upload_mb": 500, "image_gen_daily": 200, "priority_queue": true },
        "trialDays": 0
      }
    ]
  }
}
```

---

### 3.2 当前订阅状态

```
GET /api/v1/sub/status
权限: access_token (scope=FULL)
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "currentPlan": "lite",
    "status": "ACTIVE",
    "startDate": "2026-05-01",
    "endDate": "2026-06-01",
    "autoRenew": true,
    "pendingDowngrade": null,
    "trialUsed": true,
    "quotas": {
      "daily_chat": { "used": 45, "limit": 200, "resetAt": "2026-05-19T00:00:00Z" },
      "file_upload_mb": { "used": 12, "limit": 50, "resetAt": "..." },
      "image_gen_daily": { "used": 3, "limit": 20, "resetAt": "2026-05-19T00:00:00Z" }
    }
  }
}
```

---

### 3.3 创建订单（微信/支付宝）

```
POST /api/v1/sub/orders
权限: access_token (scope=FULL)
```

**请求体：**
```json
{
  "planKey": "lite",
  "period": "MONTHLY",
  "channel": "WECHAT",
  "successUrl": "zhiyu://pay/success",
  "cancelUrl": "zhiyu://pay/cancel"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| planKey | string | ✅ | 枚举: `lite` / `pro` |
| period | string | ✅ | 枚举: `MONTHLY` / `YEARLY` |
| channel | string | ✅ | 枚举: `WECHAT` / `ALIPAY` |
| successUrl | string | ❌ | App 回调 URL |
| cancelUrl | string | ❌ | App 回调 URL |

**成功响应** `201`：
```json
{
  "code": 0,
  "data": {
    "orderNo": "ZY20260518000001",
    "channel": "WECHAT",
    "amount": 2900,
    "currency": "CNY",
    "prepayInfo": {
      "appId": "wx123...",
      "partnerId": "1234567890",
      "prepayId": "wx...",
      "packageValue": "Sign=WXPay",
      "nonceStr": "abc...",
      "timeStamp": "1716019200",
      "sign": "signature..."
    },
    "expiresIn": 300
  }
}
```

**可能错误：** 40001 | 40041 套餐不存在 | 40942 退款后不可重复购买 | 50341 支付服务不可用

---

### 3.4 验证票据（Apple IAP / Google Play）

```
POST /api/v1/sub/verify-receipt
权限: access_token (scope=FULL)
```

**请求体：**
```json
{
  "channel": "APPLE",
  "receipt": "base64-encoded-receipt...",
  "restoreMode": false,
  "productId": "com.zhiyu.lite.monthly"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| channel | string | ✅ | 枚举: `APPLE` / `GOOGLE` |
| receipt | string | ✅ | 平台 receipt 或 purchaseToken |
| restoreMode | boolean | ❌ | 默认 false，恢复购买时设 true |
| productId | string | ❌ | 产品 ID（APPLE 需要） |

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "orderNo": "ZY20260518000002",
    "status": "PAID",
    "subscription": {
      "plan": "lite", "status": "ACTIVE", "startDate": "2026-05-18", "endDate": "2026-06-18"
    }
  }
}
```

**可能错误：** 40001 | 42242 票据无效 | 40941 重复验证

---

### 3.5 支付回调（各渠道）

```
POST /api/v1/sub/callback/{channel}
权限: 无（签名验证，不走用户认证）
```

**路径参数：** `channel` = `WECHAT` / `ALIPAY` / `APPLE` / `GOOGLE`

**请求体：** 按各渠道回调原始格式接收

**成功响应** `200`：
```json
{ "code": 0, "message": "success" }
```

> 幂等处理：基于 `out_trade_no` / `transaction_id` 去重。重复回调返回 200 不重复发货。
> 签名验证失败返回 42241，不暴露内部细节。

---

### 3.6 取消自动续费

```
POST /api/v1/sub/cancel
权限: access_token (scope=FULL)
```

**请求体：** 无

**成功响应** `200`：
```json
{ "code": 0, "data": { "message": "已取消自动续费，当前周期至 2026-06-18 仍可用" } }
```

---

### 3.7 套餐升级

```
POST /api/v1/sub/upgrade
权限: access_token (scope=FULL)
```

**请求体：**
```json
{
  "targetPlan": "pro",
  "period": "MONTHLY",
  "channel": "WECHAT",
  "successUrl": "...",
  "cancelUrl": "..."
}
```

**成功响应** `201`：（同 3.3 创建订单，金额已折价）

```json
{
  "code": 0,
  "data": {
    "orderNo": "ZY20260518000003",
    "amount": 4500,
    "originalAmount": 9900,
    "proratedDiscount": 5400,
    "proratedDays": 20,
    "...": "..."
  }
}
```

**可能错误：** 40001 | 40041 | 40042 目标套餐与当前相同

---

### 3.8 套餐降级

```
POST /api/v1/sub/downgrade
权限: access_token (scope=FULL)
```

**请求体：**
```json
{ "targetPlan": "lite" }
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "message": "降级预约成功，当前 Pro 周期至 2026-06-18，到期后自动切换至 Lite",
    "effectiveDate": "2026-06-18"
  }
}
```

**可能错误：** 40001 | 40041 | 40042

---

### 3.9 恢复购买

```
POST /api/v1/sub/restore
权限: access_token (scope=FULL)
```

**请求体：** 无（或由客户端先调 `/sub/verify-receipt` 加 `restoreMode=true`）

> 实际推荐客户端调用 `/sub/verify-receipt` 且 `restoreMode=true`，本接口保留为备用。

---

### 3.10 订单/支付历史

```
GET /api/v1/sub/history?page=1&size=20
权限: access_token (scope=FULL)
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "orderNo": "ZY20260501000001",
        "planKey": "lite",
        "period": "MONTHLY",
        "amount": 2900,
        "currency": "CNY",
        "channel": "WECHAT",
        "status": "PAID",
        "paidAt": "2026-05-01T10:00:00Z"
      }
    ],
    "total": 5, "page": 1, "size": 20, "pages": 1
  }
}
```

---

### 3.11 申请退款

```
POST /api/v1/sub/refund/apply
权限: access_token (scope=FULL)
```

**请求体：**
```json
{
  "orderNo": "ZY20260501000001",
  "reason": "DUPLICATE_PURCHASE",
  "description": "重复购买了两次 Lite 月卡"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| orderNo | string | ✅ | |
| reason | string | ✅ | 枚举: `DUPLICATE_PURCHASE`, `ACCIDENTAL`, `NOT_SATISFIED`, `OTHER` |
| description | string | ❌ | 最长 500 字符 |

**成功响应** `200`：
```json
{ "code": 0, "data": { "refundId": "RF20260518000001", "status": "PENDING_REVIEW" } }
```

**可能错误：** 40001 | 40401 订单不存在 | 40043 已有在处理的退款 | 40942 退款后防刷

---

### 3.12 试用状态

```
GET /api/v1/sub/trial-status
权限: access_token (scope=FULL)
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "available": true,
    "trialDays": 7,
    "used": false
  }
}
```

---

### 3.13 开始试用

```
POST /api/v1/sub/trial/start
权限: access_token (scope=FULL)
```

**请求体：** 无

**成功响应** `201`：
```json
{
  "code": 0,
  "data": {
    "planKey": "lite",
    "startDate": "2026-05-18",
    "endDate": "2026-05-25",
    "trialDays": 7
  }
}
```

**可能错误：** 40044 试用已使用过 | 40901 已有活跃订阅

---

## 4. 管理后台接口 `/api/v1/admin/**`

### 4.1 后台认证

#### 4.1.1 后台登录

```
POST /api/v1/admin/auth/login
权限: 无
```

**请求体（密码模式）：**
```json
{
  "grantType": "PASSWORD",
  "username": "admin",
  "password": "AdminP@ss1"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| grantType | string | ✅ | 枚举: `PASSWORD` / `SMS` / `WECHAT` / `WECOM_QR` |
| username | string | 按 grantType | |
| password | string | PASSWORD 时必填 | |

**成功响应** `200`（无需 TOTP）：
```json
{
  "code": 0,
  "data": {
    "adminId": 1,
    "username": "admin",
    "role": "SUPER_ADMIN",
    "permissions": ["dashboard", "users", "users.export", "..."],
    "accessToken": "eyJ...",
    "refreshToken": "d4e5f6...",
    "expiresIn": 900,
    "totpRequired": false
  }
}
```

TOTP 场景：
```json
{
  "code": 0,
  "data": {
    "totpRequired": true,
    "tempToken": "temp-xxx"
  }
}
```

> 当 `totpRequired=true` 时，客户端需继续调 TOTP 验证接口。

**可能错误：** 40001 | 40105 | 40106 账号锁定 | 40107 账号禁用

#### 4.1.2 TOTP 验证（登录第二步）

```
POST /api/v1/admin/auth/login
```

**请求体（TOTP 模式）：**
```json
{
  "grantType": "TOTP",
  "tempToken": "temp-xxx",
  "totpCode": "123456"
}
```

**成功响应** `200`：（同 4.1.1 成功响应，含完整 token）

**可能错误：** 40001 | 40112 TOTP 错误

#### 4.1.3 管理员 TOTP 设置

```
POST /api/v1/admin/auth/totp/setup    → 返回 secret + QR（同用户 TOTP 1.21）
POST /api/v1/admin/auth/totp/enable   → 验证 + 启用
POST /api/v1/admin/auth/totp/disable  → 禁用
```

#### 4.1.4 企业微信扫码回调

```
POST /api/v1/admin/auth/wecom/callback
权限: 无（企业微信服务端回调，签名验证）
```

**请求体：** 企业微信回调 XML/JSON 原始格式

#### 4.1.5 刷新 & 登出

```
POST /api/v1/admin/auth/refresh   → 同用户 /auth/refresh
POST /api/v1/admin/auth/logout    → 同用户 /auth/logout，额外记录审计
GET  /api/v1/admin/auth/session   → 查询当前会话的剩余有效期和最后活跃时间
```

---

### 4.2 用户管理

#### 4.2.1 用户列表

```
GET /api/v1/admin/users?page=1&size=20&status=ACTIVE&keyword=test
权限: users (查看)
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| page | int | ❌ | 默认 1 |
| size | int | ❌ | 默认 20 |
| status | string | ❌ | 枚举: `ACTIVE` / `DISABLED` / `DELETED` |
| keyword | string | ❌ | 模糊搜索 username / email / phone |

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "userId": 1001,
        "username": "testuser",
        "email": "u***@example.com",
        "phone": "138****5678",
        "status": "ACTIVE",
        "subscriptionPlan": "lite",
        "createdAt": "2026-01-15T08:30:00Z"
      }
    ],
    "total": 1523, "page": 1, "size": 20, "pages": 77
  }
}
```

**可能错误：** 40305 权限不足（CS 查看但正常）

---

#### 4.2.2 用户详情

```
GET /api/v1/admin/users/{userId}
权限: users (查看)
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "userId": 1001,
    "username": "testuser",
    "email": "user@example.com",
    "emailVerified": true,
    "phone": "13812345678",
    "phoneVerified": false,
    "nickname": "Test",
    "avatarUrl": "...",
    "status": "ACTIVE",
    "createdAt": "2026-01-15T08:30:00Z",
    "authIdentities": [
      { "identityId": 1, "type": "PASSWORD", "createdAt": "..." },
      { "identityId": 2, "type": "WECHAT", "identifier": "wx_openid_***", "createdAt": "..." }
    ],
    "subscription": {
      "plan": "lite",
      "status": "ACTIVE",
      "startDate": "2026-05-01",
      "endDate": "2026-06-01"
    },
    "devices": [
      { "deviceId": "...", "deviceName": "iPhone", "platform": "IOS", "lastActiveAt": "..." }
    ]
  }
}
```

**可能错误：** 40401 用户不存在

---

#### 4.2.3 启用/禁用用户

```
PUT /api/v1/admin/users/{userId}/status
权限: users (批量禁用)
```

**请求体：**
```json
{
  "status": "DISABLED",
  "reason": "违规内容发布"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| status | string | ✅ | 枚举: `ACTIVE` / `DISABLED` |
| reason | string | ❌ | 最长 200 字符 |

**成功响应** `200`：
```json
{ "code": 0, "data": { "userId": 1001, "status": "DISABLED" } }
```

**可能错误：** 40001 | 40305 权限不足

---

#### 4.2.4 认证身份列表

```
GET /api/v1/admin/users/{userId}/auth-identities
权限: users.view-identity
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "identities": [
      { "identityId": 1, "type": "PASSWORD", "identifier": null, "createdAt": "...", "lastUsedAt": "..." },
      { "identityId": 2, "type": "WECHAT", "identifier": "oxxxxxx", "createdAt": "...", "lastUsedAt": "..." }
    ]
  }
}
```
> PASSWORD 类型的 `identifier` 始终为 null（不暴露密码 hash）。

---

#### 4.2.5 批量启用/禁用

```
PUT /api/v1/admin/users/batch/status
权限: users.batch-disable
```

**请求体：**
```json
{
  "userIds": [1001, 1002, 1003],
  "action": "DISABLE"
}
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "successCount": 3,
    "failedCount": 0,
    "failures": []
  }
}
```

---

#### 4.2.6 导出用户数据

```
GET /api/v1/admin/users/export?status=ACTIVE&start=2026-01-01&end=2026-05-18
权限: users.export
```

**成功响应** `200`：CSV/Excel 文件流
```json
{
  "code": 0,
  "data": { "downloadUrl": "https://oss.zhiyu.app/exports/users_20260518.csv", "expiresIn": 3600 }
}
```

---

### 4.3 审计日志

#### 4.3.1 登录日志

```
GET /api/v1/admin/audit/login-log?userId=1001&start=2026-05-01&end=2026-05-18&type=PASSWORD&page=1&size=20
权限: audit
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "id": 1,
        "userId": 1001,
        "username": "testuser",
        "loginType": "PASSWORD",
        "status": "SUCCESS",
        "sourceIp": "192.168.1.1",
        "ipGeo": "北京",
        "deviceName": "iPhone 16 Pro",
        "platform": "IOS",
        "userAgent": "Mozilla/5.0...",
        "createdAt": "2026-05-18T10:30:00Z"
      }
    ],
    "total": 42, "page": 1, "size": 20, "pages": 3
  }
}
```

---

#### 4.3.2 身份变更记录

```
GET /api/v1/admin/audit/identity-changes?userId=1001&start=2026-05-01&end=2026-05-18&page=1&size=20
权限: audit
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "id": 1,
        "userId": 1001,
        "action": "BIND",
        "identityType": "WECHAT",
        "sourceIp": "192.168.1.1",
        "createdAt": "2026-05-18T10:30:00Z"
      },
      {
        "id": 2,
        "userId": 1001,
        "action": "UNBIND",
        "identityType": "QQ",
        "sourceIp": "192.168.1.1",
        "createdAt": "2026-05-17T09:00:00Z"
      }
    ],
    "total": 5, "page": 1, "size": 20, "pages": 1
  }
}
```

---

#### 4.3.3 管理员操作日志

```
GET /api/v1/admin/audit/operations?operatorId=1&action=UPDATE_USER_STATUS&start=2026-05-01&end=2026-05-18&page=1&size=20
权限: audit
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "id": 1,
        "operatorId": 1,
        "operatorUsername": "admin",
        "action": "UPDATE_USER_STATUS",
        "targetType": "USER",
        "targetId": "1001",
        "detail": {
          "field": "status",
          "oldValue": "ACTIVE",
          "newValue": "DISABLED",
          "reason": "违规内容"
        },
        "sourceIp": "10.0.0.1",
        "createdAt": "2026-05-18T11:00:00Z"
      }
    ],
    "total": 1, "page": 1, "size": 20, "pages": 1
  }
}
```

---

#### 4.3.4 导出操作日志

```
GET /api/v1/admin/audit/operations/export?start=2026-05-01&end=2026-05-18
权限: audit.export
```

**成功响应** `200`：同导出格式，返回下载 URL。

---

### 4.4 订阅管理

#### 4.4.1 订阅列表

```
GET /api/v1/admin/subscriptions?page=1&size=20&status=ACTIVE&plan=lite
权限: subscriptions (查看)
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "subscriptionId": 1,
        "userId": 1001,
        "username": "testuser",
        "planKey": "lite",
        "planName": "Lite",
        "status": "ACTIVE",
        "startDate": "2026-05-01",
        "endDate": "2026-06-01",
        "autoRenew": true,
        "pendingDowngrade": null
      }
    ],
    "total": 500, "page": 1, "size": 20, "pages": 25
  }
}
```

---

#### 4.4.2 订阅详情（含支付流水）

```
GET /api/v1/admin/subscriptions/{subscriptionId}
权限: subscriptions (查看)
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "subscriptionId": 1,
    "userId": 1001,
    "plan": "lite",
    "status": "ACTIVE",
    "startDate": "2026-05-01",
    "endDate": "2026-06-01",
    "autoRenew": true,
    "payments": [
      {
        "orderNo": "ZY20260501000001",
        "channel": "WECHAT",
        "amount": 2900,
        "status": "PAID",
        "paidAt": "2026-05-01T10:00:00Z"
      }
    ]
  }
}
```

---

#### 4.4.3 手动修改订阅

```
PUT /api/v1/admin/subscriptions/{subscriptionId}
权限: subscriptions.modify
```

**请求体：**
```json
{
  "action": "EXTEND",
  "extraDays": 30,
  "reason": "系统故障补偿"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| action | string | ✅ | 枚举: `EXTEND`, `UPGRADE`, `CANCEL`, `REFUND` |
| extraDays | int | EXTEND 时必填 | 1-365 |
| reason | string | ✅ | 最长 200 字符 |

---

### 4.5 支付管理

#### 4.5.1 支付流水列表

```
GET /api/v1/admin/payments?page=1&size=20&start=2026-05-01&end=2026-05-18&channel=WECHAT&status=PAID
权限: payments (查看)
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "paymentId": 1,
        "orderNo": "ZY20260501000001",
        "userId": 1001,
        "username": "testuser",
        "channel": "WECHAT",
        "transactionId": "420000123420230101...",
        "amount": 2900,
        "currency": "CNY",
        "status": "PAID",
        "reconciliationStatus": "MATCH",
        "paidAt": "2026-05-01T10:00:00Z"
      }
    ],
    "total": 500, "page": 1, "size": 20, "pages": 25
  }
}
```

---

#### 4.5.2 支付详情

```
GET /api/v1/admin/payments/{paymentId}
权限: payments (查看)
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "paymentId": 1,
    "orderNo": "ZY20260501000001",
    "userId": 1001,
    "channel": "WECHAT",
    "transactionId": "...",
    "amount": 2900,
    "currency": "CNY",
    "status": "PAID",
    "rawNotification": { "return_code": "SUCCESS", "...": "..." },
    "reconciliationStatus": "MATCH",
    "reconciliationNote": null,
    "paidAt": "2026-05-01T10:00:00Z"
  }
}
```

**可能错误：** 40401

---

#### 4.5.3 导出支付流水

```
GET /api/v1/admin/payments/export?start=2026-05-01&end=2026-05-18&channel=WECHAT
权限: payments.export
```

**成功响应** `200`：CSV 文件流或下载 URL

---

#### 4.5.4 对账结果 & 手动触发

```
GET  /api/v1/admin/payments/reconciliation?date=2026-05-17
权限: payments (查看)

POST /api/v1/admin/payments/reconciliation/run
请求体: { "date": "2026-05-17" }
权限: payments.reconcile
```

**对账结果** `200`：
```json
{
  "code": 0,
  "data": {
    "date": "2026-05-17",
    "summary": { "MATCH": 480, "MISMATCH": 2, "ONLY_LOCAL": 1, "ONLY_REMOTE": 0 },
    "details": [{ "orderNo": "...", "localAmount": 2900, "remoteAmount": 2901, "status": "MISMATCH" }]
  }
}
```

---

### 4.6 退款审核

#### 4.6.1 退款申请列表

```
GET /api/v1/admin/refunds?page=1&size=20&status=PENDING_REVIEW&start=...&end=...
权限: refunds (查看)
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "refundId": "RF20260518000001",
        "orderNo": "ZY20260501000001",
        "userId": 1001,
        "username": "testuser",
        "amount": 2900,
        "channel": "WECHAT",
        "reason": "DUPLICATE_PURCHASE",
        "description": "重复购买",
        "status": "PENDING_REVIEW",
        "appliedAt": "2026-05-18T10:00:00Z"
      }
    ],
    "total": 3, "page": 1, "size": 20, "pages": 1
  }
}
```

---

#### 4.6.2 退款详情

```
GET /api/v1/admin/refunds/{refundId}
权限: refunds (查看)
```

**成功响应** `200`：（含原始订单信息 + 用户信息）

---

#### 4.6.3 审核通过

```
POST /api/v1/admin/refunds/{refundId}/approve
权限: refunds.approve
```

**请求体：**
```json
{ "note": "确认重复购买，同意退款" }
```

---

#### 4.6.4 审核拒绝

```
POST /api/v1/admin/refunds/{refundId}/reject
权限: refunds.approve
```

**请求体：**
```json
{ "reason": "已超过退款期限" }
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| reason | string | ✅ | 最长 200 字符 |

---

### 4.7 系统配置

#### 4.7.1 配置分组列表

```
GET /api/v1/admin/config
权限: config (查看)
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "groups": [
      { "groupId": "subscription", "dataId": "plans", "description": "套餐定义" },
      { "groupId": "notification", "dataId": "templates", "description": "通知模板" },
      { "groupId": "security", "dataId": "policy", "description": "安全策略" },
      { "groupId": "rate-limit", "dataId": "thresholds", "description": "限流阈值" },
      { "groupId": "payment", "dataId": "channels", "description": "支付渠道开关" }
    ]
  }
}
```

---

#### 4.7.2 查看配置

```
GET /api/v1/admin/config/{groupId}/{dataId}
权限: config (查看)
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "groupId": "subscription",
    "dataId": "plans",
    "content": "subscription:\n  plans:\n    free:\n      features: ...",
    "format": "YAML",
    "version": 3,
    "updatedAt": "2026-05-15T10:00:00Z"
  }
}
```

---

#### 4.7.3 更新配置

```
PUT /api/v1/admin/config/{groupId}/{dataId}
权限: config.edit
```

**请求体：**
```json
{
  "content": "subscription:\n  plans:\n    ...",
  "format": "YAML"
}
```

**可能错误：** 40305 权限不足

---

#### 4.7.4 配置变更历史

```
GET /api/v1/admin/config/history/{groupId}/{dataId}
权限: config (查看)
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "versions": [
      { "version": 3, "operator": "admin", "changedAt": "2026-05-15T10:00:00Z", "summary": "更新 Lite 价格" },
      { "version": 2, "operator": "admin", "changedAt": "2026-05-10T10:00:00Z", "summary": "新增 Pro 套餐" }
    ]
  }
}
```

---

### 4.8 通知模板管理

#### 4.8.1 模板列表

```
GET /api/v1/admin/notifications?type=EMAIL&page=1&size=20
权限: notifications (查看)
```

**成功响应** `200`：按 type 筛选的模板列表，含每个模板的变量说明。

---

#### 4.8.2 模板详情

```
GET /api/v1/admin/notifications/{templateId}
权限: notifications (查看)
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "templateId": "register_welcome",
    "type": "EMAIL",
    "subject": "欢迎注册 ZhiYu",
    "body": "<html>欢迎 {{username}}...</html>",
    "variables": ["username", "app_name"],
    "updatedAt": "..."
  }
}
```

---

#### 4.8.3 编辑模板

```
PUT /api/v1/admin/notifications/{templateId}
权限: notifications (编辑)
```

**请求体：**
```json
{
  "subject": "...",
  "body": "..."
}
```

---

#### 4.8.4 发送测试消息

```
POST /api/v1/admin/notifications/{templateId}/test
权限: notifications (查看)
```

**请求体：**
```json
{
  "target": "user@example.com",
  "variables": { "username": "testuser", "app_name": "ZhiYu" }
}
```

---

### 4.9 日志管理

#### 4.9.1 访问日志

```
GET /api/v1/admin/logs/access?start=2026-05-18T00:00:00Z&end=2026-05-18T23:59:59Z&path=/auth/login&status=200&ip=192.168.1.1&page=1&size=20
权限: logs (查看)
```

**成功响应** `200`：分页日志条目列表。

---

#### 4.9.2 应用日志

```
GET /api/v1/admin/logs/application?start=...&end=...&level=ERROR&keyword=OutOfMemory&page=1&size=20
权限: logs (查看)
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "timestamp": "2026-05-18T10:30:12.345Z",
        "level": "ERROR",
        "logger": "c.z.payment.service.PaymentServiceImpl",
        "message": "Payment verification failed",
        "traceId": "a1b2c3d4-e5f6...",
        "exception": "java.net.SocketTimeoutException: ..."
      }
    ],
    "total": 3, "page": 1, "size": 20, "pages": 1
  }
}
```

---

#### 4.9.3 慢查询日志 / 安全日志

```
GET /api/v1/admin/logs/slow-sql?start=...&end=...&minMs=200&page=...
GET /api/v1/admin/logs/security?start=...&end=...&type=LOGIN_FAIL&ip=...&page=...
权限: logs (查看)
```

---

#### 4.9.4 日志统计

```
GET /api/v1/admin/logs/stats
权限: logs (查看)
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "last7Days": {
      "total": 1500000,
      "byLevel": { "ERROR": 42, "WARN": 1200, "INFO": 800000, "DEBUG": 700000 },
      "byType": { "access": 500000, "application": 1000000, "slow_sql": 120, "security": 88 },
      "trend": [{ "date": "2026-05-12", "count": 200000 }, "..."]
    }
  }
}
```

---

### 4.10 后台用户管理（仅 SUPER_ADMIN）

#### 4.10.1 后台用户列表

```
GET /api/v1/admin/admins?page=1&size=20&status=ACTIVE
权限: admins
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "adminId": 1,
        "username": "admin",
        "role": "SUPER_ADMIN",
        "status": "ACTIVE",
        "lastLoginAt": "2026-05-18T10:30:00Z",
        "lastLoginIp": "10.0.0.1",
        "createdAt": "2026-01-01T00:00:00Z"
      },
      {
        "adminId": 2,
        "username": "cs_zhang",
        "role": "CS",
        "status": "ACTIVE",
        "lastLoginAt": "2026-05-17T15:00:00Z",
        "lastLoginIp": "10.0.0.2",
        "createdAt": "2026-03-15T09:00:00Z"
      }
    ],
    "total": 5, "page": 1, "size": 20, "pages": 1
  }
}
```

**可能错误：** 40305 权限不足（非 SUPER_ADMIN）

#### 4.10.2 创建后台用户

```
POST /api/v1/admin/admins
权限: admins
```

**请求体：**
```json
{
  "username": "cs_li",
  "password": "SecureP@ss1",
  "role": "CS",
  "phone": "13812345678",
  "email": "cs_li@zhiyu.app"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| username | string | ✅ | 3-32 位字母数字下划线 |
| password | string | ✅ | 8-128 位，至少 3 类字符 |
| role | string | ✅ | 枚举: `SUPER_ADMIN` / `ADMIN` / `CS` |
| phone | string | ❌ | E.164 格式 |
| email | string | ❌ | RFC 5322 |

**成功响应** `201`：
```json
{
  "code": 0,
  "data": {
    "adminId": 3,
    "username": "cs_li",
    "role": "CS",
    "status": "ACTIVE",
    "createdAt": "2026-05-18T12:00:00Z"
  }
}
```

**可能错误：** 40001 | 40305 | 40901 用户名已存在

#### 4.10.3 编辑后台用户

```
PUT /api/v1/admin/admins/{adminId}
权限: admins
```

**请求体：**（全部可选，部分更新）
```json
{
  "role": "ADMIN",
  "status": "DISABLED"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| role | string | ❌ | 枚举: `SUPER_ADMIN` / `ADMIN` / `CS` |
| status | string | ❌ | 枚举: `ACTIVE` / `DISABLED` |
| phone | string | ❌ | |
| email | string | ❌ | |

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "adminId": 2,
    "username": "cs_zhang",
    "role": "ADMIN",
    "status": "DISABLED",
    "updatedAt": "2026-05-18T12:00:00Z"
  }
}
```

**可能错误：** 40001 | 40305 | 40401 管理员不存在

#### 4.10.4 删除后台用户

```
DELETE /api/v1/admin/admins/{adminId}
权限: admins
```

**成功响应** `200`：
```json
{ "code": 0, "data": { "message": "管理员已删除" } }
```

**可能错误：** 40305 | 40401

> 不可删除自己。

#### 4.10.5 管理员登录历史

```
GET /api/v1/admin/admins/{adminId}/login-history?page=1&size=20
权限: admins
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "id": 1,
        "adminId": 1,
        "username": "admin",
        "loginType": "PASSWORD",
        "grantType": "PASSWORD",
        "status": "SUCCESS",
        "sourceIp": "10.0.0.1",
        "ipGeo": "北京",
        "userAgent": "Mozilla/5.0...",
        "totpVerified": true,
        "createdAt": "2026-05-18T10:30:00Z"
      }
    ],
    "total": 42, "page": 1, "size": 20, "pages": 3
  }
}
```

---

### 4.11 我的账户

#### 4.11.1 当前管理员信息

```
GET /api/v1/admin/my-account/profile
权限: 登录即可
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "adminId": 1,
    "username": "admin",
    "role": "SUPER_ADMIN",
    "email": "admin@zhiyu.app",
    "phone": "138****5678",
    "permissions": ["dashboard", "users", "users.export", "users.batch-disable", "..."],
    "totpEnabled": true,
    "lastLoginAt": "2026-05-18T10:30:00Z",
    "lastLoginIp": "10.0.0.1",
    "createdAt": "2026-01-01T00:00:00Z"
  }
}
```

#### 4.11.2 修改密码

```
PUT /api/v1/admin/my-account/password
权限: 登录即可
```

**请求体：**
```json
{
  "currentPassword": "OldP@ss1",
  "newPassword": "NewSecureP@ss2"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| currentPassword | string | ✅ | |
| newPassword | string | ✅ | 公共规则，不可与用户名相同 |

**成功响应** `200`：
```json
{ "code": 0, "data": { "message": "密码修改成功，请重新登录" } }
```

**可能错误：** 40001 | 40105 当前密码错误 | 40118 新密码与用户名相同

#### 4.11.3 自己的登录历史

```
GET /api/v1/admin/my-account/login-history?page=1&size=20
权限: 登录即可
```

**成功响应** `200`：（格式同 4.10.5，但仅返回当前管理员记录）

#### 4.11.4 自己的操作日志

```
GET /api/v1/admin/my-account/operation-log?start=2026-05-01&end=2026-05-18&page=1&size=20
权限: 登录即可
```

**成功响应** `200`：（格式同 4.3.3，但仅返回当前管理员记录）

#### 4.11.5 当前活跃会话

```
GET /api/v1/admin/my-account/sessions
权限: 登录即可
```

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "sessions": [
      {
        "sessionId": "a1b2c3d4...",
        "deviceName": "Chrome / macOS",
        "sourceIp": "10.0.0.1",
        "ipGeo": "北京",
        "loginAt": "2026-05-18T10:30:00Z",
        "lastActiveAt": "2026-05-18T12:00:00Z",
        "current": true
      }
    ]
  }
}
```

#### 4.11.6 踢出其他会话

```
DELETE /api/v1/admin/my-account/sessions/{sessionId}
权限: 登录即可
```

**成功响应** `200`：
```json
{ "code": 0, "data": { "message": "会话已终止" } }
```

**可能错误：** 40401 会话不存在 | 40001 不可踢出当前会话

---

## 5. 文件上传接口 `/file/**`

### 5.1 获取 OSS 上传凭证

```
POST /api/v1/file/upload-credential
权限: access_token (scope=FULL)
```

**请求体：**
```json
{
  "fileType": "AVATAR",
  "fileName": "profile.jpg",
  "contentType": "image/jpeg",
  "fileSize": 204800
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| fileType | string | ✅ | 枚举: `AVATAR` / `EXPORT` / `ATTACHMENT` |
| fileName | string | ✅ | 原始文件名，最长 256 字符 |
| contentType | string | ✅ | MIME 类型 |
| fileSize | number | ✅ | 字节数 |

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "uploadUrl": "https://oss.zhiyu.app/bucket/avatars/1001/2026/05/a1b2c3d4.jpg?Expires=...&Signature=...",
    "objectKey": "avatars/1001/2026/05/a1b2c3d4.jpg",
    "accessUrl": "https://oss.zhiyu.app/avatars/1001/2026/05/a1b2c3d4.jpg",
    "method": "PUT",
    "headers": { "Content-Type": "image/jpeg", "x-oss-security-token": "..." },
    "expiresIn": 300
  }
}
```

**可能错误：** 40001 | 40021 不支持的文件类型 | 40022 文件过大

### 5.2 文件大小限制

| fileType | 最大尺寸 | 允许类型 |
|----------|:------:|---------|
| `AVATAR` | 2 MB | `image/jpeg`, `image/png`, `image/webp` |
| `EXPORT` | 50 MB | `text/csv`, `application/vnd.ms-excel` |
| `ATTACHMENT` | 10 MB | `image/*`, `application/pdf`, `text/plain` |

### 5.3 上传确认

客户端直传 OSS 完成后，调此接口通知后端：

```
POST /api/v1/file/upload-complete
权限: access_token (scope=FULL)
```

**请求体：**
```json
{
  "objectKey": "avatars/1001/2026/05/a1b2c3d4.jpg",
  "fileType": "AVATAR"
}
```

| 字段 | 类型 | 必填 | 校验 |
|------|------|:--:|------|
| objectKey | string | ✅ | OSS object key |
| fileType | string | ✅ | 枚举同上 |

**成功响应** `200`：
```json
{
  "code": 0,
  "data": {
    "objectKey": "avatars/1001/2026/05/a1b2c3d4.jpg",
    "accessUrl": "https://oss.zhiyu.app/avatars/1001/2026/05/a1b2c3d4.jpg",
    "size": 204800,
    "verified": true
  }
}
```

**可能错误：** 40001 | 40401 文件不存在 | 42221 OSS 校验失败（文件未上传成功或已过期）

---

## 6. 健康检查 `/actuator/**`

| 端点 | 权限 | 说明 |
|------|------|------|
| `GET /actuator/health` | 无 | 综合健康状态（K8s liveness probe） |
| `GET /actuator/health/readiness` | 无 | 就绪检查（DB + Redis + Nacos） |
| `GET /actuator/health/liveness` | 无 | 存活检查（进程是否运行） |
| `GET /actuator/info` | 无 | 构建信息（version, commit hash, timestamp） |

**`/actuator/health` 响应** `200`：
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "MySQL", "validationQuery": "isValid()" } },
    "redis": { "status": "UP", "details": { "cluster": "sentinel", "nodes": 3 } },
    "nacos": { "status": "UP" },
    "diskSpace": { "status": "UP", "details": { "total": 107374182400, "free": 53687091200 } }
  }
}
```

> 响应头包含限流信息：`X-RateLimit-Remaining: 99`，`X-RateLimit-Reset: 1716019200`

---

## 7. 错误码速查（按接口）

| 接口 | 可能错误码 |
|------|-----------|
| `/auth/send-register-code` | 40001, 40111, 42903 |
| `/auth/register` | 40001, 40109, 40111, 40901, 40902, 42903(IP) |
| `/auth/check-availability` | 40001 |
| `/auth/third-party` | 40001, 40115, 40116 |
| `/auth/third-party/bind-email` | 40001, 40109, 40902 |
| `/auth/sms/send` | 40001, 40111, 42901 |
| `/auth/sms/login` | 40001, 40109, 40114, 40106 |
| `/auth/webauthn/register/finish` | 40001, 40116 |
| `/auth/webauthn/auth/finish` | 40001, 40116 |
| `/auth/forgot-password` | 40001, 40111, 42901 |
| `/auth/reset-password` | 40001, 40117, 40118 |
| `/auth/refresh` | 40001, 40103, 40104 |
| `/auth/action-verify` | 40001, 40105, 40112, 40114 |
| `/user/bind-*` | 40001, 40109, 40304, 40904 |
| `/user/unbind/{id}` | 40401, 40031, 40304 |
| `/user/deactivate` | 40304, 40033 |
| `/user/reactivate` | 40001, 40109, 40034, 40401 |
| `/sub/orders` | 40001, 40041, 40942, 50341 |
| `/sub/verify-receipt` | 40001, 42242, 40941 |
| `/sub/callback/{channel}` | 42241 (签名验证失败/静默忽略) |
| `/sub/upgrade` | 40001, 40041, 40042 |
| `/sub/downgrade` | 40001, 40041, 40042 |
| `/sub/refund/apply` | 40001, 40401, 40043, 40942 |
| `/sub/trial/start` | 40044, 40901 |
| `/file/upload-credential` | 40001, 40021, 40022 |
| `/file/upload-complete` | 40001, 40401, 42221 |
| `/actuator/health` | —（始终 200，仅 status=DOWN 时影响 K8s） |
| Admin 所有接口 | + 40305 (权限不足) |
