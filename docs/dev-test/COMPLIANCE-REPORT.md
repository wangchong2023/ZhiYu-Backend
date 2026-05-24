# ZhiYu-Backend 合规审查报告

> 审查日期：2026-05-23 | 审查范围：全部 docs/ 设计文档 vs 当前代码实现

## 一、总体评估

| 维度 | 完成度 | 评级 | 关键风险 |
|------|:---:|:---:|------|
| PRD P0 功能 | 7/12 (58%) | 🟡 | 用户资料/套餐订阅模块空白 |
| PRD P1 功能 | 3/17 (18%) | 🔴 | 支付/通知/设备管理大面积缺失 |
| PRD P2 功能 | 1.5/7 (21%) | 🟢 | 监控仪表盘已提前实现 |
| 前端页面 | 10/23 (43%) | 🟡 | P0 页面已覆盖，P1 页面全部缺失 |
| 后端模块 | 3/7 (43%) | 🟡 | 3 个模块为空壳 |
| 数据库 | 12/12 业务表 | 🟢 | 所有 DDL 已创建完毕 |
| 开发规范 | 13/13 (100%) | 🟢 | 完美合规 |
| 安全合规 | 6.5/15 (43%) | 🔴 | PIPL 合规存在法律风险 |
| 测试覆盖 | 1.5/7 (21%) | 🔴 | 后端仅 3 个测试 |
| CI/CD | 7/9 (78%) | 🟡 | Woodpecker CI 就绪 |
| 运维监控 | 2/6 (33%) | 🔴 | 告警/日志聚合缺失 |

---

## 二、PRD 功能实现明细

### P0 — MVP（目标：12 项，已实现：7 项）

| # | 功能 | 模块 | 状态 | 备注 |
|---|------|------|:---:|------|
| 1 | 邮箱注册 + 密码登录 | zhiyu-auth | ✅ | `AuthController` + `AuthService` |
| 2 | 邮箱验证码 | zhiyu-auth | ✅ | `CaptchaController` / `CaptchaService` |
| 3 | JWT Token 签发与刷新 | ufp-auth | ✅ | `JwtService` (RS256)，含 Refresh Token 轮换 |
| 4 | 忘记/重置密码 | zhiyu-auth | ✅ | 邮箱验证码流程 |
| 5 | CAPTCHA 防机器人 | zhiyu-auth | ✅ | 图形验证码 + 滑块验证 |
| 6 | 个人信息查看/编辑 | zhiyu-user | ❌ | 模块为空壳，无任何 Java 代码 |
| 7 | 账户注销（软删除） | zhiyu-user | ❌ | 同上 |
| 8 | 套餐列表查询 | zhiyu-subscription | ❌ | 模块为空壳 |
| 9 | 免费游客默认开通 | zhiyu-subscription | ❌ | 同上 |
| 10 | 管理员密码登录 + TOTP | zhiyu-admin | ✅ | `AdminAuthController` |
| 11 | 用户管理（列表/详情/禁用） | zhiyu-admin | ✅ | `AdminUserController` + 前端 `UserListPage` |
| 12 | 审计日志查看 | zhiyu-admin | ✅ | `AdminLogController` + 前端 `AuditLogPage` |

### P1 — 第一轮迭代（目标：17 项，已实现：3 项）

| # | 功能 | 模块 | 状态 | 备注 |
|---|------|------|:---:|------|
| 13 | 微信/Google/Apple 第三方登录 | zhiyu-auth | ✅ | `OAuthController` / `OAuthService`（不含 QQ） |
| 14 | 短信验证码登录 | zhiyu-auth | ❌ | API-SPEC 已定义，代码未实现 |
| 15 | 多设备管理 + 踢出 | zhiyu-auth | ❌ | `auth_user_device` 表存在，无 API |
| 16 | TOTP 双因素（用户端） | zhiyu-auth | ❌ | ufp-auth 已有 TOTP service，未暴露 API |
| 17 | WebAuthn 通行密钥 | zhiyu-auth | ✅ | `WebAuthnController` / `WebAuthnService` |
| 18 | 账户恢复工单 | zhiyu-auth | ❌ | `account_recovery_ticket` 表存在，代码未实现 |
| 19 | 绑定/解绑多种认证方式 | zhiyu-user | ❌ | 模块为空壳 |
| 20 | 微信支付/支付宝支付 | zhiyu-subscription | ❌ | 模块为空壳 |
| 21 | 套餐升级（折价）/降级（预约） | zhiyu-subscription | ❌ | 同上 |
| 22 | 自动续费管理 | zhiyu-subscription | ❌ | 同上 |
| 23 | 退款申请流程 | zhiyu-subscription | ❌ | 同上 |
| 24 | RBAC（多角色支持） | zhiyu-admin | ⚠️ | `auth_role`/`auth_grant` 表存在，无前端管理界面 |
| 25 | 订阅/支付管理 | zhiyu-admin | ❌ | 依赖 zhiyu-subscription 先实现 |
| 26 | 退款审核 | zhiyu-admin | ❌ | 同上 |
| 27 | 通知模块（邮件/SMS/Push） | zhiyu-notification | ❌ | 模块为空壳 |
| 28 | 第三方绑定邮箱升级 scope | zhiyu-auth | ❌ | API-SPEC §1.6 已定义，未实现 |

### P2 — 迭代二（目标：7 项，已实现：1.5 项）

| # | 功能 | 模块 | 状态 | 备注 |
|---|------|------|:---:|------|
| 29 | Apple/Google IAP 订阅 | — | ❌ | 未开始 |
| 30 | 通知模板管理 | zhiyu-admin | ❌ | 未开始 |
| 31 | 系统配置可视化管理 | zhiyu-admin | ❌ | 未开始 |
| 32 | Dashboard 监控仪表盘 | zhiyu-admin | ✅ | **超出计划提前完成**，含 5 个子页面 |
| 33 | 日志管理（五类日志检索） | zhiyu-admin | ⚠️ | 已实现三类（应用/安全/访问），缺运行/审计日志 |
| 34 | 文件上传（阿里云 OSS） | — | ❌ | 未开始 |

---

## 三、前端页面合规

| # | 页面 | 路由 | FRONTEND-DESIGN 要求 | 状态 |
|---|------|------|------|:---:|
| 1 | 登录页 | `/admin/login` | 密码登录 + TOTP + 第三方登录按钮 | ✅ 已实现 |
| 2 | 仪表盘 | `/admin/dashboard` | 统计卡片 + 趋势图 + 告警列表 | ✅ 已实现 |
| 3 | 用户列表 | `/admin/users` | 列表/搜索/详情抽屉/批量禁用/导出 | ⚠️ 缺批量/导出/SearchForm 封装 |
| 4 | 审计日志 | `/admin/audit` | Tabs(登录日志/身份变更/管理员操作) | ⚠️ 缺管理员操作 tab |
| 5 | 我的账户 | `/admin/account` | Tabs(个人信息/认证身份/通行密钥/登录历史) | ✅ 已实现 |
| 6 | 监控总览 | `/admin/monitor/overview` | 健康状态 + 资源使用 | ✅ 已实现 |
| 7 | API 指标 | `/admin/monitor/metrics` | QPS + P50/P99 + 错误率 + 端点排名 | ✅ 已实现 |
| 8 | 日志检索 | `/admin/monitor/logs` | 五类日志 tab 筛选 | ⚠️ 三类日志 |
| 9 | 告警面板 | `/admin/monitor/alerts` | 告警列表 + 统计摘要 | ✅ 已实现 |
| 10 | 日志级别 | `/admin/monitor/settings` | 动态调整 + 历史记录 + 自动回滚 | ✅ 已实现 |
| 11 | 订阅管理 | `/admin/subscriptions` | DataTable + 详情抽屉 | ❌ 未实现 |
| 12 | 支付流水 | `/admin/payments` | 渠道筛选 + 对账面板 | ❌ 未实现 |
| 13 | 退款审核 | `/admin/refunds` | 审批抽屉 + ApproveModal | ❌ 未实现 |
| 14 | 后台用户管理 | `/admin/admins` | 仅 SUPER_ADMIN | ❌ 未实现 |
| 15 | 通知模板 | `/admin/notifications` | 编辑器 + 测试发送 | ❌ 未实现 |
| 16 | 系统配置 | `/admin/config` | Feature Flag + 配置历史 | ❌ 未实现 |

**国际化**：react-i18next + zh-CN/en-US 双语完整，所有页面已接入 `useTranslation()`。42 个前端测试全部通过。

**缺失的统一组件**：`<PermissionGate>`、`<ConfirmAction>`、`<ExportButton>`、`<SearchForm>` 均未封装。

---

## 四、后端模块状态

| 模块 | 设计职责 | 代码状态 | Controller | Service | Mapper | Entity | 测试 |
|------|---------|:---:|:---:|:---:|:---:|:---:|:---:|
| ufp-common | 工具/异常/Filter/DTO/i18n | ✅ 活跃 | — | — | — | — | 0 |
| ufp-auth | JWT/BCrypt/TOTP/WebAuthn/OAuth | ✅ 活跃 | — | 4 | 23+ | 23+ | 0 |
| zhiyu-common | MyBatis-Plus/Redis 配置 | ✅ 活跃 | — | — | — | — | 0 |
| zhiyu-auth | 注册/登录/OAuth Provider | ✅ 活跃 | 4 | 5 | 有 | 有 | 0 |
| zhiyu-user | 用户资料/偏好/注销 | 🔴 空壳 | 0 | 0 | 0 | 0 | 0 |
| zhiyu-subscription | 套餐/订单/支付/退款 | 🔴 空壳 | 0 | 0 | 0 | 0 | 0 |
| zhiyu-notification | 邮件/SMS/Push | 🔴 空壳 | 0 | 0 | 0 | 0 | 0 |
| zhiyu-admin | 管理员认证/RBAC/审计/监控 | ✅ 活跃 | 5 | 5 | 有 | 有 | 0 |
| zhiyu-server | Spring Boot 入口 + Flyway | ✅ 活跃 | — | — | — | — | 1 |

**严重问题**：3 个 P0/P1 模块（user、subscription、notification）为 Maven 空壳项目，仅含 `pom.xml`，无任何 Java 源文件。

---

## 五、数据库合规

Flyway 迁移脚本完整（V1.0.0 ~ V1.9.0），12 张业务表 + 23 张 UFP auth 表 + 监控表，与 DATABASE.md 设计 100% 一致。

| 检查项 | 状态 |
|--------|:---:|
| 命名规范（单数 snake_case，auth_ 前缀） | ✅ |
| 主键统一 `BIGINT AUTO_INCREMENT` | ✅ |
| 索引命名 `idx_<table>_<col>` / `uk_<table>_<col>` | ✅ |
| JSON 字段 `xxx_json` | ✅ |
| 时间字段 `xxx_at` DATETIME(3) | ✅ |
| Flyway 版本化管理 | ✅ |
| OAuth 账户合并策略实现 | ❌ 表结构就绪，业务逻辑未实现 |

---

## 六、安全合规

### 已实现

- BCrypt 密码哈希 + 独立盐值 (strength=12)
- JWT RS256 非对称签名
- Token 黑名单 + Refresh Token 轮换 + 盗用检测
- 连续登录失败锁定（5 次锁定 15 分钟）+ CAPTCHA 触发（3 次后）
- 管理员 TOTP 双因素认证

### 待处理

| 风险等级 | 事项 | 影响 |
|:---:|------|------|
| 🔴 高 | 日志数据脱敏未配置（手机号/邮箱/IP/Token 明文记录） | PIPL 违规风险 |
| 🔴 高 | 数据保护官（DPO）未指定 | PIPL 法律要求 |
| 🔴 高 | Google/Apple OAuth 数据出境 PIA 未评估 | PIPL 合规 |
| 🟡 中 | 敏感个人信息单独同意机制未实现 | PIPL 合规 |
| 🟡 中 | ZAP 基线扫描未部署 | OWASP 最佳实践 |
| 🟡 中 | OWASP Dependency Check 未集成 | 依赖漏洞风险 |
| 🟢 低 | 密钥轮换自动化未实现 | 运维效率 |

---

## 七、测试覆盖

### 当前状态

| 层级 | 测试数 | 覆盖率 | 目标 | 差距 |
|------|:---:|:---:|:---:|:---:|
| 后端单元测试 | 3 | ~1% | ≥80% | -79% |
| 后端集成测试 | 0 | 0% | 5 场景 | -100% |
| 前端单元测试 | 42 | ~60% (渲染) | ≥80% | -20% |
| E2E 测试 | 0 | 0% | 5 场景 | -100% |
| 性能测试 | 0 | 0% | 5 场景 | -100% |

### 模块级覆盖率目标 vs 实际

| 模块 | 目标 | 实际 |
|------|:---:|:---:|
| ufp-common | 90% | 0% |
| ufp-auth | 85% | 0% |
| zhiyu-common | 90% | 0% |
| zhiyu-auth | 85% | 0% |
| zhiyu-admin | 80% | 0% |
| zhiyu-server | N/A | 1 test (Flyway) |

---

## 八、建议优先行动

### 立即处理（P0 阻塞）

1. **实现 zhiyu-user 模块**：用户资料查看/编辑、账户注销（软删除）— P0 核心需求
2. **实现 zhiyu-subscription 基础功能**：套餐列表查询、免费游客默认开通 — P0 核心需求
3. **日志脱敏**：配置 logback 脱敏转换器，防止 PIPL 合规风险

### 短期（1-2 周）

4. **后端单元测试补全**：优先 zhiyu-auth 和 zhiyu-admin 模块，目标覆盖率 80%
5. **集成测试框架**：基于 Testcontainers 编写核心流程集成测试
6. **统一前端组件**：封装 `<PermissionGate>`、`<SearchForm>`、`<ConfirmAction>`

### 中期（1 个月）

7. **zhiyu-notification 模块**：邮件发送基础功能
8. **P1 支付功能**：微信支付/支付宝接入
9. **安全加固**：OWASP DC 集成、ZAP 扫描、PIA 评估完成

---

> 本报告基于 `docs/` 目录下全部设计文档与 `backend/`、`frontend/` 实际代码对比生成。审查工具：手动代码审查 + `find`/`grep` 静态分析 + 自动化测试执行。
