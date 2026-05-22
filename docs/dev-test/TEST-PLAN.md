# ZhiYu-Backend 测试计划与用例

> **⚠️ 当前实现状态：本文档为测试计划规格，对应代码尚未开始编写。** 全项目目前仅 2 个脚手架级测试（`ZhiYuApplicationTest`、`FlywayMigrationRunnerTest`），130+ 测试用例全部针对不存在的 Service/Controller。JaCoCo 覆盖率阈值（80%）在当前空代码状态下会导致 CI 失败 — 建议在业务代码开始实现前将 JaCoCo check 阈值暂设为 `informational` 或通过 profile 条件启用。以下内容作为开发时的测试需求参考。
>
> **P0 范围提示：** 本文档包含 P1 特性（订阅支付 UT-SUB-*、第三方登录 UT-AUTH-12/13、退款 E2E-05、管理 TOTP UT-ADMIN-02/03）的测试用例。第一版开发时应优先实现 P0 模块的测试（UT-AUTH-01~11, UT-TOKEN-01~07, UT-USER-01~08, UT-ADMIN-01/04/05）。

> 本文档基于 [开发规范](DEVELOPMENT-STANDARDS.md) 定义的测试策略，为每个模块提供可执行的测试用例清单。测试分层：单元测试 (JUnit 5 + Mockito) → 集成测试 (Testcontainers) → E2E (核心业务流)。

## 1. 测试策略

> 🔮 = 远期预留：`frontend/` 目录当前为空，frontend 测试与管理后台 E2E 依赖前端项目启动后再实施。

| 层级 | 框架 | 启动 Spring | 外部依赖 | 目标覆盖率 | 执行位置 |
|------|------|:----------:|----------|:----------:|---------|
| Unit | JUnit 5 + Mockito | ❌ | 无 | Service 85%, Util 90% | `mvn test` |
| Integration | @SpringBootTest + Testcontainers | ✅ | MySQL 8.0, Redis 7 | Mapper 80%, Controller 70% | `mvn verify` |
| E2E | SpringBootTest + Testcontainers | ✅ | MySQL, Redis | 核心路径 100% 覆盖 | `mvn verify` |
| Frontend Unit | Vitest + RTL | N/A | MSW | 80% | `npm test` 🔮 |
| Frontend E2E | Playwright | N/A | 真实后端 | 5 条路径 | `npm run e2e` 🔮 |

## 2. 后端单元测试用例

### 2.1 认证模块 — AuthService

| ID | 用例 | 前置条件 | 预期结果 |
|----|------|---------|---------|
| UT-AUTH-01 | 正常注册 | 用户名/邮箱未占用，验证码正确 | 创建 user + identity，返回 Token |
| UT-AUTH-02 | 用户名已占用 | username 存在 | BizException(40901) |
| UT-AUTH-03 | 邮箱已占用 | email 存在 | BizException(40902) |
| UT-AUTH-04 | 验证码错误 | verifyCode 不匹配 | BizException(40109) |
| UT-AUTH-05 | 密码与用户名相同 | password == username | BizException(40118) |
| UT-AUTH-06 | 弱密码 | "12345678" | ConstraintViolationException |
| UT-AUTH-07 | 正常密码登录 | 用户存在，密码正确 | 返回 Token |
| UT-AUTH-08 | 密码错误 | 密码不匹配 | BizException(40105) |
| UT-AUTH-09 | 账号被禁用 | user.status = DISABLED | BizException(40107) |
| UT-AUTH-10 | 账号已注销 | user.status = DELETED | BizException(40108) |
| UT-AUTH-11 | 触发临时锁定 | login:fail 计数 ≥ 5 | BizException(40106) |
| UT-AUTH-12 | 第三方新用户登录 | identity 不存在 | 创建 user + identity，scope=LIMITED |
| UT-AUTH-13 | 第三方已有用户登录 | identity 存在 | 返回 Token，scope=FULL |
| UT-AUTH-14 | 刷新 token 正常 | refresh_token 有效 | 返回新 token 对，旧 token 失效 |
| UT-AUTH-15 | refresh_token 重用检测 | 旧 token 被再次使用 | BizException(40104)，全设备失效 |

### 2.2 认证模块 — TokenService

| ID | 用例 | 预期结果 |
|----|------|---------|
| UT-TOKEN-01 | 签发 access_token | 有效期 900 秒，payload 含 jti/user_id/device_id/scope |
| UT-TOKEN-02 | 签发 refresh_token | 128-bit 随机字符串，Redis 存储 |
| UT-TOKEN-03 | 验签有效 token | 返回 Claims，不查 Redis |
| UT-TOKEN-04 | 验签过期 token | BizException(40101) |
| UT-TOKEN-05 | 验签被篡改 token | BizException(40102) |
| UT-TOKEN-06 | 登出后 token 入黑名单 | jti 写入 Redis jti blacklist，TTL=剩余有效期 |
| UT-TOKEN-07 | 黑名单中的 token 被拒绝 | 验签通过但 jti 在黑名单 → BizException(40102) |

### 2.3 用户模块 — UserService

| ID | 用例 | 预期结果 |
|----|------|---------|
| UT-USER-01 | 绑定新认证方式 | action_token 有效，创建 identity |
| UT-USER-02 | 绑定已存在的认证方式 | BizException(40904) |
| UT-USER-03 | 解绑认证方式（多个） | identity 删除成功 |
| UT-USER-04 | 解绑最后一个认证方式 | BizException(40031) |
| UT-USER-05 | 注销账户 | 创建软删除标记，清空所有 token |
| UT-USER-06 | 重复注销（30 天内） | BizException(40033) |
| UT-USER-07 | 重新激活（30 天内） | 验证码正确 → 恢复 ACTIVE |
| UT-USER-08 | 重新激活（已超 30 天） | BizException(40034) |

### 2.4 订阅模块 — SubscriptionService

| ID | 用例 | 预期结果 |
|----|------|---------|
| UT-SUB-01 | 创建微信支付订单 | 生成 orderNo，返回 prepayInfo |
| UT-SUB-02 | 不存在的套餐 | BizException(40041) |
| UT-SUB-03 | 升级折价计算 | Lite(余20天) → Pro：newAmount - (2900/30)*20 |
| UT-SUB-04 | 升级到相同套餐 | BizException(40042) |
| UT-SUB-05 | 支付回调验签通过 | 更新订单→PAID，激活订阅，记录 payment |
| UT-SUB-06 | 支付回调验签失败 | BizException(42241) |
| UT-SUB-07 | 支付回调幂等（重复） | 返回 success 不重复发货 |
| UT-SUB-08 | 票据验证（Apple IAP） | 向 Apple 验证 receipt → 激活订阅 |
| UT-SUB-09 | 票据无效 | BizException(42242) |
| UT-SUB-10 | 试用开始 | trial_used=false → 标记试用，设置 7 天有效期 |
| UT-SUB-11 | 重复试用 | BizException(40044) |

### 2.5 管理后台 — AdminService

| ID | 用例 | 预期结果 |
|----|------|---------|
| UT-ADMIN-01 | 密码登录（无需 TOTP） | 返回 admin JWT + permissions |
| UT-ADMIN-02 | 密码登录（需 TOTP） | 返回 totpRequired=true + tempToken |
| UT-ADMIN-03 | TOTP 验证（第二步） | tempToken 有效 → 返回完整 JWT |
| UT-ADMIN-04 | 权限不足操作 | CS 执行 PUT /admin/users/{id}/status → BizException(40305) |
| UT-ADMIN-05 | 禁用用户 | 填写原因 → user.status=DISABLED + 审计日志 |
| UT-ADMIN-06 | 审核退款通过 | 调用渠道退款 API → refund.status=REFUNDED |
| UT-ADMIN-07 | 更新系统配置 | 高风险配置需 TOTP → 写入 Nacos + 记录变更 |

### 2.6 UFP 认证库 — ufp-auth `[P0]`

| ID | 用例 | 预期结果 |
|----|------|---------|
| UT-UFP-01 | BCrypt 密码哈希 (strength=12) | 生成 60-char hash，`$2b$12$` 前缀 |
| UT-UFP-02 | BCrypt 密码验证 (匹配) | 正确密码返回 true |
| UT-UFP-03 | BCrypt 密码验证 (不匹配) | 错误密码返回 false |
| UT-UFP-04 | BCrypt 空密码处理 | 空白/null 密码抛出 ConstraintViolationException |
| UT-UFP-05 | JWT RS256 签发 | 含 jti/sub/iat/exp/device_id/scope，有效期 900s |
| UT-UFP-06 | JWT 验签 (有效) | 返回 Claims，不查 Redis |
| UT-UFP-07 | JWT 验签 (过期) | JwtException → BizException(40101) |
| UT-UFP-08 | JWT 验签 (篡改) | JwtException → BizException(40102) |
| UT-UFP-09 | JWT 验签 (错误算法) | 用 HS256 签名的 token → BizException(40102) |
| UT-UFP-10 | Token 黑名单写入 | jti 写入 Redis，TTL=剩余有效期 |
| UT-UFP-11 | Token 黑名单命中 | jti 在 Redis 黑名单 → BizException(40102) |
| UT-UFP-12 | TOTP 密钥生成 | 生成 Base32 密钥，长度 ≥ 16 字节 |
| UT-UFP-13 | TOTP 验证码生成 (当前 30s 窗口) | 6 位数字，与 RFC 6238 兼容 |
| UT-UFP-14 | TOTP 验证码校验 (正确) | 返回 true |
| UT-UFP-15 | TOTP 验证码校验 (错误) | 返回 false |
| UT-UFP-16 | TOTP 验证码校验 (漂移 ±1 窗口) | 前后 30s 窗口的码也通过 |
| UT-UFP-17 | WebAuthn 注册挑战生成 | 返回 challenge + rpId + user.id (32-byte random) |
| UT-UFP-18 | WebAuthn 验证挑战 | challenge 签名有效 → 返回 true |
| UT-UFP-19 | OAuth state 生成与校验 | 生成 CSRF state → Redis 存储 → 回调校验通过/拒绝 |

### 2.7 通知模块 — NotificationService `[P1]`

| ID | 用例 | 预期结果 |
|----|------|---------|
| UT-NOTIF-01 | 发送邮件 (成功) | MailSender.send() 被调用，outbox status=SENT |
| UT-NOTIF-02 | 发送邮件 (SMTP 超时) | 重试 3 次 → outbox status=FAILED + 记录 error |
| UT-NOTIF-03 | 发送邮件 (频率限制) | 同一 recipient 60s 内第 2 封 → BizException(42903) |
| UT-NOTIF-04 | 发送短信 (成功) | SMS provider.send() 被调用，记录 send log |
| UT-NOTIF-05 | 发送短信 (余额不足) | provider 返回 quota exceeded → BizException(50020) |
| UT-NOTIF-06 | 模板渲染 (Thymeleaf) | 变量替换正确，HTML 输出合法 |
| UT-NOTIF-07 | 模板不存在 | BizException(50021) |
| UT-NOTIF-08 | Outbox 重发 (FAILED 记录) | 定时任务扫描 FAILED → 重发 → status=SENT |
| UT-NOTIF-09 | Outbox 重发 (超过最大次数) | retry_count ≥ 3 → status=DEAD，不再重试 |
| UT-NOTIF-10 | Push 通知 (FCM 成功) | FirebaseMessaging.send() 返回 messageId |

---

## 3. 后端集成测试用例

### 3.1 Auth 接口

| ID | 用例 | 方法 | 路径 | 预期 |
|----|------|------|------|------|
| IT-AUTH-01 | 完整注册流程 | POST | `/auth/send-register-code` → `/auth/register` | 201, 返回 token |
| IT-AUTH-02 | 注册字段校验失败 | POST | `/auth/register` (username=空) | 40001 |
| IT-AUTH-03 | 密码登录 + refresh + logout | POST | `/auth/login` → `/auth/refresh` → `/auth/logout` | 全部 200 |
| IT-AUTH-04 | logout 后 token 被拒绝 | GET | `/user/profile` (用已 logout 的 token) | 40102 |
| IT-AUTH-05 | 重新激活（全链路） | POST | `/user/deactivate` → `/user/reactivate` | 200 |
| IT-AUTH-06 | CAPTCHA 绕过检测 | POST | `/auth/send-register-code` (无 captchaToken) | 40111 |
| IT-AUTH-07 | 邮件发送频率限制 | POST | `/auth/send-register-code` × 2 (间隔 < 60s) | 42903(第二次) |
| IT-AUTH-08 | 获取 CAPTCHA 配置 | GET | `/auth/captcha/config` | 200, sceneId + appKey |
| IT-AUTH-09 | CAPTCHA token 过期 | POST | `/auth/send-register-code` (过期 captchaToken) | 40112 |
| IT-AUTH-10 | 检查用户名/邮箱可用 | POST | `/auth/check-availability` (username=free) | 200, available=true |
| IT-AUTH-11 | 检查用户名已占用 | POST | `/auth/check-availability` (username=taken) | 200, available=false |
| IT-AUTH-12 | 找回用户名 | POST | `/auth/forgot-username` (email=已注册) | 200, 发送邮件 |

### 3.2 User 接口

| ID | 用例 | 方法 | 路径 | 预期 |
|----|------|------|------|------|
| IT-USER-01 | 获取个人资料 | GET | `/user/profile` | 200, 脱敏字段正确 |
| IT-USER-02 | 更新昵称 | PUT | `/user/profile` | 200, 字段已更新 |
| IT-USER-03 | 未登录访问 | GET | `/user/profile` (无 token) | 401xx |
| IT-USER-04 | scope=LIMITED 访问 | GET | `/user/profile` (LIMITED token) | 40302 |
| IT-USER-05 | 获取用户设置 | GET | `/user/settings` | 200, language/timezone |
| IT-USER-06 | 更新用户设置 | PUT | `/user/settings` (language=en) | 200, 字段已更新 |

### 3.3 Subscription 接口

| ID | 用例 | 方法 | 路径 | 预期 |
|----|------|------|------|------|
| IT-SUB-01 | 查询套餐列表 | GET | `/sub/plans` | 200, 三个套餐 |
| IT-SUB-02 | 查询订阅状态 | GET | `/sub/status` | 200, quotas 包含当前用量 |
| IT-SUB-03 | 创建订单（含 mock 支付 API） | POST | `/sub/orders` | 201, orderNo |
| IT-SUB-04 | 支付回调幂等 | POST | `/sub/callback/WECHAT` 重复发 | 200, 不重复写 payment |

### 3.4 Admin 接口

| ID | 用例 | 方法 | 路径 | 预期 |
|----|------|------|------|------|
| IT-ADMIN-01 | Admin 登录 + 获取用户列表 | POST → GET | `/admin/auth/login` → `/admin/users` | 200, 分页数据 |
| IT-ADMIN-02 | 禁用用户 | PUT | `/admin/users/{id}/status` | 200, status=DISABLED |
| IT-ADMIN-03 | CS 角色禁用用户 | PUT | `/admin/users/{id}/status` | 40305 |
| IT-ADMIN-04 | 查看审计日志 | GET | `/admin/audit/login-log` | 200 |

### 3.5 Notification 接口 `[P1]`

| ID | 用例 | 方法 | 路径 | 预期 |
|----|------|------|------|------|
| IT-NOTIF-01 | 发送邮件 (含模板渲染) | POST | `/notification/email` | 200, outbox 写入, 异步发送 |
| IT-NOTIF-02 | 发送短信 | POST | `/notification/sms` | 200, send log 写入 |
| IT-NOTIF-03 | 查询发送状态 | GET | `/notification/status/{outboxId}` | 200, status/messageId |
| IT-NOTIF-04 | 邮件频率限制 | POST | `/notification/email` × 2 (间隔 < 60s) | 42903 (第二次) |
| IT-NOTIF-05 | Outbox 重发 (定时任务触发) | N/A | 扫描 FAILED 记录 | retry_count 递增, 最终 SENT |

---

## 4. 后端 E2E 测试场景

### E2E-01: 新用户注册并使用免费功能

```
步骤:
1. 完成 CAPTCHA → 发送邮箱验证码
2. 完成注册 → 获得 JWT
3. 查看个人资料
4. 查看套餐列表
5. 查看订阅状态 (free)
6. 更新昵称
7. 登出
预期: 所有步骤返回 200/201
```

### E2E-02: 用户购买订阅全流程

```
步骤:
1. 注册并登录
2. 查看套餐列表
3. 选择 Lite 月付 → 创建微信支付订单
4. (模拟)微信支付回调
5. 验证订阅状态 → ACTIVE, plan=lite
6. 查看配额用量
7. 取消自动续费
8. 验证 autoRenew=false
预期: 每一步正确，订阅状态机正确转换
```

### E2E-03: 第三方登录 + 绑定邮箱

```
步骤:
1. (模拟)微信授权 → /auth/third-party
2. 验证 scope=LIMITED
3. 尝试访问 /user/profile → 40302
4. 发送邮箱验证码
5. 绑定邮箱
6. 验证 scope=FULL
7. 成功访问 /user/profile
预期: scope 过渡正确
```

### E2E-04: 套餐升级 + 降级

```
步骤:
1. 购买 Lite 月付
2. 升级到 Pro (验证折价金额)
3. 验证订阅立即变为 Pro
4. 降级到 Lite
5. 验证 pending_downgrade=lite，当前仍为 Pro
6. (模拟)Pro 到期
7. 验证切换为 Lite
预期: 升级/降级逻辑正确
```

### E2E-05: 退款全流程

```
步骤:
1. 购买 Pro → 已支付
2. 申请退款 (DUPLICATE_PURCHASE)
3. Admin 查看退款列表
4. Admin 审核通过
5. (模拟)微信退款回调
6. 验证 subscription.status=REFUNDED
7. 尝试再次购买同套餐 → 40942
预期: 退款状态机正确，防刷有效
```

---

## 5. Admin E2E 场景（Playwright）[🔮 远期：依赖管理后台前端]

| ID | 测试场景 | 步骤 |
|----|---------|------|
| FE-E2E-01 | 管理员登录 → 仪表盘 | 填入用户名密码 → 点击登录 → 看到 Dashboard → 退出 |
| FE-E2E-02 | 用户管理 | 点击用户管理 → 搜索用户 → 点击详情抽屉 → 禁用某用户 |
| FE-E2E-03 | 审计日志 | 打开审计日志 → 筛选登录日志 → 验证可查看 |
| FE-E2E-04 | 退款审核 | 退款列表 → 查看详情 → 填写审核意见 → 审批通过 |
| FE-E2E-05 | 会话超时 | 登录 → 等待 30 分钟(模拟) → 倒计时弹窗 → 点击继续 → 不退出 |

---

## 6. 性能测试场景

| ID | 场景 | 工具 | 并发 | 目标 |
|----|------|------|:----:|------|
| PERF-01 | 登录接口压测 | JMeter | 1000 | P99 < 1000ms |
| PERF-02 | 套餐列表查询 | JMeter | 5000 | P99 < 100ms |
| PERF-03 | 创建订单（含第三方调用 mock） | JMeter | 100 | P99 < 2000ms |
| PERF-04 | 支付回调处理 | JMeter | 500 | P99 < 500ms |
| PERF-05 | 用户列表分页查询 | JMeter | 100 | P99 < 300ms |

---

## 7. 测试数据准备

```java
// 测试 fixture 基类
@SpringBootTest
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("zhiyu_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
    }
}
```

---

## 8. 覆盖率报告

| 模块 | 最低行覆盖率 | 生成方式 | 说明 |
|------|:----------:|---------|------|
| ufp-common | 90% | JaCoCo | 工具类、异常、校验器、缓存抽象 |
| ufp-auth | 85% | JaCoCo | JWT、BCrypt、TOTP、WebAuthn、Token 黑名单 |
| zhiyu-common | 90% | JaCoCo | MyBatis-Plus/Redis 配置、业务异常 |
| zhiyu-auth | 85% | JaCoCo | 注册、登录、验证码、OAuth Provider |
| zhiyu-user | 80% | JaCoCo | 用户资料、偏好、注销 |
| zhiyu-subscription | 80% | JaCoCo | 套餐、订单、支付、配额 |
| zhiyu-notification | 80% | JaCoCo | 邮件、SMS、Push、模板渲染 |
| zhiyu-admin | 80% | JaCoCo | 管理员认证、RBAC、审计日志 |
| zhiyu-server | N/A | JaCoCo | 仅 Spring Boot 入口 + Flyway 迁移，无业务代码 |
| 全局 | **80%** | JaCoCo aggregate | |

CI 中 `mvn verify` 生成 JaCoCo 报告，不达标则构建失败。
