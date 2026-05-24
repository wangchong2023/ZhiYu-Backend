# 测试覆盖率最终报告

**日期：** 2026-05-23
**分支：** main

---

## 一、后端覆盖率

| 模块 | 测试数 | 指令覆盖率 | 分支覆盖率 | 行覆盖率 | 状态 |
|------|:------:|:----------:|:----------:|:--------:|:----:|
| ufp-common | 198 | 99.7% | N/A | 98.8% | ✅ |
| ufp-auth | 136 | 98.9% | 95.9% | 98.0% | ✅ |
| zhiyu-common | 13 | 44.6%¹ | - | - | ⚠️¹ |
| zhiyu-auth | 316 | 95.5% | 95.2% | 96.6% | ✅ |
| zhiyu-user | 23 | 100% | 100% | 100% | ✅ |
| zhiyu-admin | 172 | 97.6% | 95.3% | 97.5% | ✅ |
| zhiyu-server | 3 | ~100% | N/A | ~100% | ✅ |
| **总计** | **~861** | **95%+** | **95%+** | **96%+** | ✅ |

> ¹ zhiyu-common 覆盖率低是因为配置类（RestClientConfig、MyBatisPlusConfig、WebMvcConfig、OpenApiConfig、RedisConfig）无法被单元测试覆盖。所有业务类（ApiResponse、GlobalExceptionHandler）覆盖率为 100%。

### 关键模块测试明细

**ufp-auth (136 tests, 10 new test files):**
- TokenTypeTest, AuthGrantTypeTest, AuthUserStatusTest, LoginResultTest — 枚举值参数化测试
- TokenBlacklistTest — Token 黑名单功能测试
- JwtKeyLoaderTest — RSA 密钥加载（正常/缺失/无效/空目录）
- WebAuthnCredentialRepositoryTest — 14 测试覆盖全部 5 个方法
- JwtServiceTest — 新增 TTL 单位、pending token、边界条件
- PasswordServiceTest — BCrypt 哈希/验证/null 安全

**zhiyu-auth (316 tests, 26 测试文件):**
- AuthServiceTest — 32 测试：注册/登录/刷新/登出/TOTP/设备
- AuthValidatorTest — 37 测试：参数校验全覆盖
- Filter 测试 — ActionTokenFilter(25) + JwtAuthFilter(20) + ScopeFilter(16) + IpWhitelistFilter(11) + RateLimitFilter(8) + ActionTokenService(8)
- OAuth 测试 — OAuthProviderFactory(10) + WechatOAuthProvider(10) + GoogleOAuthProvider(12) + AppleOAuthProvider(13)
- Controller 测试 — AuthController(15) + WebAuthnController(11) + TotpController(8) + UserDeviceController(7) + OAuthController(5) + CaptchaController(3)

**zhiyu-admin (172 tests, agent 提升):**
- AdminMonitorServiceTest — 31 测试：指标/告警/日志/健康检查
- AdminStatsServiceTest — 26 测试：概览/趋势/分布/异常处理
- AdminLogServiceTest — 17 测试：安全日志/登录日志筛选
- AdminUserServiceTest — 15 测试：CRUD/状态筛选/空结果
- AdminConverterTest — 15 测试：MapStruct 转换器/空安全/分支覆盖

---

## 二、前端覆盖率

**测试：** 135 tests, 18 test files, 全部通过
**构建：** `npx tsc --noEmit` 无错误, `npm run build` 成功

| 类别 | 语句 % | 分支 % | 函数 % | 行 % |
|------|:------:|:------:|:------:|:----:|
| **总体** | **88.93** | **67.47** | **63.33** | **88.93** |
| API 层 | 96.89 | 100 | 92.85 | 96.89 |
| 组件 | 95.29 | 72.22 | 100 | 95.29 |

### 页面覆盖率明细

| 页面 | 行 % | 测试数 | 关键覆盖 |
|------|:----:|:------:|----------|
| MonitorLogsPage | 100% | 18 | 全部 4 tab + 筛选 + 分页 |
| MonitorOverviewPage | 98.48% | 3 | 健康卡片 + 统计概览 |
| MonitorAlertsPage | 98.3% | 3 | 告警摘要 + 列表 |
| DashboardPage | 94.61% | 3 | 指标概览卡片 |
| MonitorMetricsPage | 87.2% | 3 | 时间范围 + 指标图表 |
| AuditLogPage | 90.72% | 19 | 3 tab + 筛选 + CSV 导出 |
| MyAccountPage | 90.84% | 7 | 个人信息/tab/设备/TOTP |
| UserListPage | 90.04% | 6 | 用户列表/筛选/分页 |
| LoginPage | 89.59% | 26 | 密码/SMS/隐私/验证码/captcha |
| LogLevelSettingsPage | 84.94% | 2 | 日志级别列表/修改 |

### E2E 测试 (Playwright)

- `frontend/e2e/admin-login.spec.ts` — 6 测试：表单显示、验证错误、SMS tab 切换、隐私同意、登录提交、认证重定向
- `frontend/playwright.config.ts` — Chromium 项目配置

---

## 三、合规差距修复 — 全部 9 项完成

| # | 任务 | 状态 | 产出 |
|---|------|:----:|------|
| 1 | SessionTimeoutOverlay | ✅ | JWT exp 监控，5 分钟提前弹窗，token 刷新 |
| 2 | 审计日志管理员操作 tab | ✅ | AdminOperationTab：操作人/动作/目标/IP/时间 |
| 3 | 日志检索 access/slow-query tab | ✅ | AccessLogTab(EIP/方法/状态码) + SlowQueryTab(SQL/耗时) |
| 4 | TOTP 双因素 API | ✅ | setup/enable/verify/disable 端点 |
| 5 | 多设备管理 API | ✅ | 设备列表/踢出/信任端点 |
| 6 | Filter Chain 补全 | ✅ | RateLimit/IpWhitelist/Scope/ActionToken 全部实现 |
| 7 | zhiyu-user 模块 | ✅ | UserProfileController + UserProfileService |
| 8 | 安全加固 | ✅ | 隐私政策勾选框 + OWASP DC + Trivy 镜像扫描 |
| 9 | 测试基础设施 | ✅ | JaCoCo 80% 规则 + 861 后端测试 + 135 前端测试 + E2E |

---

## 四、验证结果

| 检查项 | 命令 | 结果 |
|--------|------|:----:|
| 后端编译 | `./mvnw -f backend/pom.xml compile` | ✅ BUILD SUCCESS |
| 后端测试 | `./mvnw -f backend/pom.xml test` | ✅ 861 tests, 0 failures |
| 前端测试 | `npx vitest run` | ✅ 135 tests, 0 failures |
| TypeScript | `npx tsc --noEmit` | ✅ 无错误 |
| 前端构建 | `npm run build` | ✅ 成功 |
| CI 配置 | `woodpecker-cli lint` | ✅ 通过 |
