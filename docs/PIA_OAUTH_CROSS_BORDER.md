# OAuth 第三方登录数据出境隐私影响评估（PIA）

> 依据《个人信息保护法》第55-56条，个人信息处理者在处理敏感个人信息、委托处理、向境外提供个人信息等情形下，应当事前进行个人信息保护影响评估。

## 1. 评估概述

| 项目 | 内容 |
|------|------|
| **评估对象** | ZhiYu 平台 OAuth 第三方登录（微信/Google/Apple） |
| **评估日期** | 2026-05-23 |
| **评估人** | 数据保护官 (DPO) |
| **数据涉及范围** | 用户 OpenID/UnionID、昵称、头像、邮箱（scope 授权） |

## 2. 数据流描述

### 2.1 微信 OAuth (WeChat)

```
用户 → 微信授权页 → [授权确认] → 微信回调 ZhiYu 后端
     → ZhiYu 后端调用微信 /sns/oauth2/access_token
     → 获取 openId + unionId + accessToken
     → 后端调用 /sns/userinfo → 获取昵称、头像（如用户授权）
     → 数据仅存储在 ZhiYu 自建数据库（阿里云中国区）
```

**数据流出境情况**：微信 OAuth 接口服务器位于中国境内，**不涉及数据出境**。

### 2.2 Apple OAuth (Sign in with Apple)

```
用户 → Apple 授权页 → [Face ID / Touch ID 验证] → Apple 回调 ZhiYu 后端
     → ZhiYu 后端验证 Apple identityToken (JWT)
     → 获取 user identifier + email（如用户授权）+ name（首次）
     → 数据仅存储在 ZhiYu 自建数据库（阿里云中国区）
```

**数据流出境情况**：Apple 授权服务器位于美国/全球 CDN。用户授权时：
- **流向境外**：用户设备 IP、授权请求上下文
- **回传境内**：identityToken（含 user identifier）

### 2.3 Google OAuth

```
用户 → Google 授权页 → [账号选择/确认] → Google 回调 ZhiYu 后端
     → ZhiYu 后端用 authorizationCode 换 accessToken
     → 调用 Google /oauth2/v3/userinfo → 获取 sub, email, name, picture
     → 数据仅存储在 ZhiYu 自建数据库（阿里云中国区）
```

**数据流出境情况**：Google OAuth 服务器位于美国/全球。用户授权时：
- **流向境外**：用户设备 IP、授权请求上下文、authorizationCode
- **回传境内**：userinfo 响应（sub, email, name, picture）

## 3. 风险分析

| 风险 | 等级 | 描述 | 缓解措施 |
|------|:----:|------|----------|
| 数据出境未经安全评估 | 中 | Google/Apple 回调涉及数据经境外服务器中转 | OAuth 回调仅包含授权码，不包含用户实际业务数据 |
| third-party 数据滥用 | 低 | OAuth Provider 可能记录用户授权行为 | 仅请求最小必要 scope（openid/profile）；不请求好友列表、通讯录等高敏感 scope |
| 传输链路窃听 | 低 | 公网传输存在被监听风险 | 全链路 HTTPS + 证书固定（certificate pinning） |
| 授权码劫持 | 中 | authorizationCode 在回调 URL 中明文传输 | 使用 PKCE (Proof Key for Code Exchange) 扩展；authorizationCode 有效期 ≤ 5min |
| 数据超范围使用 | 低 | OAuth 获取的数据可能超出告知范围使用 | 仅存储必要字段（user identifier + display name）；头像不存储本地，仅引用原 URL |

## 4. 合规评估

### 4.1 合法性基础 (PIPL Art.13)

| 处理行为 | 合法性基础 |
|----------|-----------|
| 通过微信登录获取 openId、昵称 | 用户同意（点击微信授权按钮） |
| 通过 Apple 登录获取 user identifier | 用户同意（Face ID / Touch ID 授权） |
| 通过 Google 登录获取 email | 用户同意（Google 账号授权页） |
| 将获取的信息存储在 ZhiYu 数据库 | 履行合同所必需（提供登录服务） |

### 4.2 单独同意 (PIPL Art.23, 39)

- 向境外提供个人信息需取得用户**单独同意**
- **当前状态**：Google/Apple OAuth 涉及数据经境外中转，应在授权页面单独告知用户
- **实现状态**：前端登录页已添加隐私政策勾选框（`privacyConsent` 字段），后端已强制校验

### 4.3 数据出境安全评估 (PIPL Art.38, 40)

| 条件 | ZhiYu 当前状态 |
|------|---------------|
| 处理 100 万人以上个人信息 | 待业务规模确定后评估 |
| 累计向境外提供 10 万人以上个人信息 | 待业务规模确定后评估 |
| 累计向境外提供 1 万人以上敏感个人信息 | 不适用（OAuth 不获取敏感个人信息） |

> **当前结论**：ZhiYu 作为起步期平台，OAuth 登录涉及的数据出境属于最小必要范围，且仅涉及基础账号标识符（不涉及敏感个人信息），**当前不触发强制安全评估申报义务**。用户规模达到申报门槛后，需重新评估。

## 5. 改进建议

1. **隐私政策更新** — 在隐私政策中明确告知用户哪些 OAuth Provider 服务器位于境外，以及数据出境的具体情况
2. **单独同意 UI** — 当用户选择 Google/Apple 登录时，在授权页下方添加数据出境告知文案："登录过程涉及数据经境外服务器中转，点击授权即表示您已知悉并同意"
3. **数据最小化** — 定期审查 OAuth 获取的字段，删除不再需要的 profile 字段
4. **审计日志** — 记录每次 OAuth 授权的 provider、scope、IP、时间戳，保存期限 ≥ 3 年
5. **退出机制** — 提供"解绑第三方账号"功能，解绑后删除从该 Provider 获取的 profile 数据

## 6. 审批

| 角色 | 姓名 | 签名 | 日期 |
|------|------|------|------|
| 评估人 (DPO) | — | — | 2026-05-23 |
| 技术负责人 | — | — | — |
| 法务负责人 | — | — | — |
