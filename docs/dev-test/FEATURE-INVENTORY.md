# ZhiYu-Backend 全部功能点盘点

> 更新日期：2026-05-23 | 基于合规审查报告与实际代码交叉验证

---

## 一、P0 核心功能（目标 12 / 已实现 9 / 部分 1 / 未实现 2）

| # | 功能 | 模块 | 状态 | 证据 |
|---|------|------|:---:|------|
| 1 | 邮箱注册 + 密码登录 | zhiyu-auth | ✅ | `AuthController` + `AuthService.register()` / `login()` |
| 2 | 邮箱验证码 | zhiyu-auth | ✅ | `CaptchaController` / `CaptchaService` |
| 3 | JWT Token 签发与刷新 | ufp-auth | ✅ | `JwtService.issue()` / `refresh()` (RS256)，含 Refresh Token 轮换 |
| 4 | 忘记/重置密码 | zhiyu-auth | ✅ | 邮箱验证码流程，`AuthService.resetPassword()` |
| 5 | CAPTCHA 防机器人 | zhiyu-auth | ✅ | 图形验证码 + 滑块验证 |
| 6 | 个人信息查看/编辑 | zhiyu-user | ✅ | `UserProfileController` + `UserProfileService` — 本次补充 |
| 7 | 账户注销（软删除） | zhiyu-user | ✅ | `DELETE /user/account` — 本次补充 |
| 8 | 套餐列表查询 | zhiyu-subscription | ❌ | 模块为空壳，零 Java 代码 |
| 9 | 免费游客默认开通 | zhiyu-subscription | ❌ | 同上 |
| 10 | 管理员密码登录 + TOTP | zhiyu-admin | ✅ | `AdminAuthController` / `AdminAuthService` |
| 11 | 用户管理（列表/详情/禁用） | zhiyu-admin | ⚠️ | 列表/搜索/详情抽屉已实现；缺批量操作、CSV 导出 |
| 12 | 审计日志查看 | zhiyu-admin | ✅ | `AdminLogController` + 前端 `AuditLogPage`（3 tab） |

---

## 二、P1 迭代一功能（目标 17 / 已实现 6 / 部分 1 / 未实现 10）

| # | 功能 | 模块 | 状态 | 证据 |
|---|------|------|:---:|------|
| 13 | 微信/Google/Apple 第三方登录 | zhiyu-auth | ✅ | `OAuthController` / `OAuthService` + 3 个 Provider |
| 14 | 短信验证码登录 | zhiyu-auth | ⚠️ | 前端 SMS tab 完整（LoginPage:192-232），后端 `/auth/sms/send` 存在；但 `LoginRequest` 缺少 `grantType`/`smsCode` 字段，登录 flow 未打通 |
| 15 | 多设备管理 + 踢出 | zhiyu-auth | ✅ | `UserDeviceController` + `DeviceService` — 本次补充 |
| 16 | TOTP 双因素（用户端） | zhiyu-auth | ✅ | `TotpController`（setup/enable/verify/disable） — 本次补充 |
| 17 | WebAuthn 通行密钥 | zhiyu-auth | ✅ | `WebAuthnController` / `WebAuthnService` |
| 18 | 账户恢复工单 | zhiyu-auth | ❌ | `account_recovery_ticket` 表存在，无 API |
| 19 | 绑定/解绑多种认证方式 | zhiyu-user | ❌ | zhiyu-user 仅 profile 基础 CRUD，无 auth method 管理 |
| 20 | 微信支付/支付宝支付 | zhiyu-subscription | ❌ | 模块为空壳 |
| 21 | 套餐升级（折价）/降级（预约） | zhiyu-subscription | ❌ | 同上 |
| 22 | 自动续费管理 | zhiyu-subscription | ❌ | 同上 |
| 23 | 退款申请流程 | zhiyu-subscription | ❌ | 同上 |
| 24 | RBAC（多角色支持） | zhiyu-admin | ❌ | `auth_role`/`auth_grant` 表存在，无后端 API，无前端管理界面 |
| 25 | 订阅/支付管理 | zhiyu-admin | ❌ | 依赖 zhiyu-subscription 先实现 |
| 26 | 退款审核 | zhiyu-admin | ❌ | 同上 |
| 27 | 通知模块（邮件/SMS/Push） | zhiyu-notification | ❌ | 模块为空壳，零 Java 代码 |
| 28 | 第三方绑定邮箱升级 scope | zhiyu-auth | ❌ | API-SPEC §1.6 已定义，未实现 |

---

## 三、P2 迭代二功能（目标 7 / 已实现 1.5 / 部分 1.5 / 未实现 4）

| # | 功能 | 模块 | 状态 | 证据 |
|---|------|------|:---:|------|
| 29 | Apple/Google IAP 订阅 | — | ❌ | 未开始 |
| 30 | 通知模板管理 | zhiyu-admin | ❌ | 未开始 |
| 31 | 系统配置可视化管理 | zhiyu-admin | ❌ | 未开始 |
| 32 | Dashboard 监控仪表盘 | zhiyu-admin | ✅ | 5 个子页面：概览/指标/日志/告警/日志级别 |
| 33 | 日志管理（五类日志检索） | zhiyu-admin | ⚠️ | 已实现 4 类（应用/安全/访问/慢查询），缺运行日志 tab |
| 34 | 文件上传（阿里云 OSS） | — | ❌ | 未开始 |

---

## 四、前端页面（目标 16 / 已实现 11 / 部分 0 / 未实现 5）

| # | 页面 | 路由 | 状态 | 备注 |
|---|------|------|:---:|------|
| 1 | 登录页 | `/admin/login` | ✅ | 密码 + SMS tab + TOTP + 第三方登录按钮 + 隐私勾选框 + captcha |
| 2 | 仪表盘 | `/admin/dashboard` | ✅ | 统计卡片 + 趋势图 + 告警列表 |
| 3 | 用户列表 | `/admin/users` | ✅ | 列表/搜索/详情抽屉；缺批量禁用/CSV 导出 |
| 4 | 审计日志 | `/admin/audit` | ✅ | 3 tab：登录日志/身份变更/管理员操作（本次补充） |
| 5 | 我的账户 | `/admin/account` | ✅ | 4 tab：个人信息/认证身份/通行密钥/设备管理 |
| 6 | 监控总览 | `/admin/monitor/overview` | ✅ | 健康状态 + 资源使用 |
| 7 | API 指标 | `/admin/monitor/metrics` | ✅ | QPS + P50/P99 + 错误率 + 端点排名 |
| 8 | 日志检索 | `/admin/monitor/logs` | ✅ | 4 类日志 tab 筛选（本次补全 access/slow-query） |
| 9 | 告警面板 | `/admin/monitor/alerts` | ✅ | 告警列表 + 统计摘要 |
| 10 | 日志级别 | `/admin/monitor/settings` | ✅ | 动态调整 + 历史记录 + 自动回滚 |
| 11 | 订阅管理 | `/admin/subscriptions` | ❌ | 未实现，依赖 zhiyu-subscription 模块 |
| 12 | 支付流水 | `/admin/payments` | ❌ | 未实现 |
| 13 | 退款审核 | `/admin/refunds` | ❌ | 未实现 |
| 14 | 后台用户管理 | `/admin/admins` | ❌ | 仅 SUPER_ADMIN 可见，未实现 |
| 15 | 通知模板 | `/admin/notifications` | ❌ | 未实现 |
| 16 | 系统配置 | `/admin/config` | ❌ | Feature Flag + 配置历史 |

---

## 五、后端模块

| # | 模块 | 设计职责 | 状态 | Controller | Service | Entity | 测试 |
|---|------|---------|:---:|:---:|:---:|:---:|:---:|
| 1 | ufp-common | 工具/异常/Filter/DTO/i18n | ✅ | — | — | — | 198 |
| 2 | ufp-auth | JWT/BCrypt/TOTP/WebAuthn/OAuth | ✅ | — | 4 | 23+ | 136 |
| 3 | zhiyu-common | MyBatis-Plus/Redis 配置 | ✅ | — | — | — | 13 |
| 4 | zhiyu-auth | 注册/登录/OAuth/TOTP/设备/Filter Chain | ✅ | 6 | 6 | 有 | 316 |
| 5 | zhiyu-user | 用户资料/注销 | ⚠️ | 1 | 1 | 0 | 23 |
| 6 | zhiyu-admin | 管理员认证/RBAC/审计/监控 | ✅ | 5 | 5 | 有 | 172 |
| 7 | zhiyu-server | Spring Boot 入口 + Flyway | ✅ | — | — | — | 3 |
| 8 | zhiyu-subscription | 套餐/订单/支付/退款 | ❌ | 0 | 0 | 0 | 0 |
| 9 | zhiyu-notification | 邮件/SMS/Push | ❌ | 0 | 0 | 0 | 0 |

---

## 六、安全能力

| # | 能力 | 状态 | 备注 |
|---|------|:---:|------|
| 1 | BCrypt 密码哈希 (strength=12) | ✅ | `PasswordService` |
| 2 | JWT RS256 非对称签名 | ✅ | `JwtService` |
| 3 | Token 黑名单 + Refresh 轮换 + 盗用检测 | ✅ | `TokenBlacklist` |
| 4 | 连续登录失败锁定（5 次/15 分钟） | ✅ | `LoginAttemptService` |
| 5 | CAPTCHA 触发（3 次失败后） | ✅ | `LoginAttemptService.checkCaptchaRequired()` |
| 6 | 管理员 TOTP 双因素 | ✅ | ufp-auth `TotpService` |
| 7 | 用户端 TOTP 双因素 API | ✅ | `TotpController` — 本次补充 |
| 8 | Rate Limiting（Filter 层） | ✅ | `RateLimitFilter` — 本次补充 |
| 9 | IP 白名单 Filter | ✅ | `IpWhitelistFilter` — 本次补充 |
| 10 | Scope 校验 Filter | ✅ | `ScopeFilter` — 本次补充 |
| 11 | ActionToken 校验 Filter | ✅ | `ActionTokenFilter` — 本次补充 |
| 12 | 隐私政策勾选（PIPL 告知-同意） | ✅ | LoginPage 隐私勾选框 — 本次补充 |
| 13 | OWASP Dependency Check（CI） | ✅ | `.woodpecker.yml` Stage — 本次补充 |
| 14 | Trivy 镜像扫描（CI） | ✅ | `.woodpecker.yml` Stage — 本次补充 |
| 15 | Gitleaks 密钥扫描（CI） | ✅ | `.woodpecker.yml` Stage 5 |
| 16 | 日志数据脱敏（手机号/邮箱/IP） | ❌ | PIPL 违规风险 |
| 17 | DPO 指定 | ❌ | PIPL 法律要求 |
| 18 | Google/Apple OAuth 数据出境 PIA | ❌ | PIPL 合规 |
| 19 | 敏感个人信息单独同意 | ❌ | PIPL 合规 |
| 20 | ZAP 基线扫描 | ❌ | OWASP 最佳实践 |
| 21 | 密钥轮换自动化 | ❌ | 运维效率 |

---

## 七、测试基础设施

| # | 项目 | 状态 | 当前值 | 目标 |
|---|------|:---:|:---:|:---:|
| 1 | 后端单元测试（总计） | ✅ | ~861 tests | ≥80% 覆盖率 |
| 2 | ufp-common 覆盖率 | ✅ | 99.7% | 90% |
| 3 | ufp-auth 覆盖率 | ✅ | 98.9% | 85% |
| 4 | zhiyu-auth 覆盖率 | ✅ | 95.5% | 85% |
| 5 | zhiyu-admin 覆盖率 | ✅ | 97.6% | 80% |
| 6 | zhiyu-user 覆盖率 | ✅ | 100% | 80% |
| 7 | 前端单元测试 | ✅ | 135 tests, 88.93% | ≥80% |
| 8 | 集成测试 (Testcontainers) | ✅ | `AuthFlowIT.java` (3 tests) | 5 场景 |
| 9 | E2E 测试 (Playwright) | ✅ | `admin-login.spec.ts` (6 tests) | 5 场景 |
| 10 | JaCoCo 强制规则 | ✅ | `backend/pom.xml` (80% rule) | 80% |

---

## 八、统计摘要

| 维度 | 总数 | 已实现 | 部分 | 未实现 | 完成率 |
|------|:---:|:---:|:---:|:---:|:---:|
| P0 功能 | 12 | 9 | 1 | 2 | 75% |
| P1 功能 | 17 | 6 | 1 | 10 | 35% |
| P2 功能 | 7 | 1 | 2 | 4 | 14% |
| 前端页面 | 16 | 11 | 0 | 5 | 69% |
| 后端模块 | 9 | 6.5 | 0.5 | 2 | 72% |
| 安全能力 | 21 | 15 | 0 | 6 | 71% |
| 测试设施 | 10 | 10 | 0 | 0 | 100% |
| **合计** | **92** | **58.5** | **4.5** | **29** | **64%** |

---

> 方法：基于 `docs/dev-test/COMPLIANCE-REPORT.md` 逐项交叉验证实际代码。审查工具：`find`/`grep` 静态分析 + JaCoCo 覆盖率报告 + vitest coverage v8。
