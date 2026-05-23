# ZhiYu 管理后台前端设计规格

> **⚠️ 当前实现状态：`frontend/` 目录完全为空（仅 `.gitkeep`），无 `package.json`、无 Vite 配置、无任何组件。** 本文档描述 12+ 页面，但 PRD P0 范围仅需 3 个页面（管理员登录+TOTP、用户列表/详情/禁用、审计日志查看）。以下 P0 页面标注 `[P0]`，其余为后续迭代规划。

> 本文档定义 zhiyu-admin-web 的页面级设计规格，包括每个页面的组件树、交互状态（加载/空/错误/正常）和数据流向。技术栈与开发规范见 [DEVELOPMENT-STANDARDS.md](../dev-test/DEVELOPMENT-STANDARDS.md)。

## 1. 全局设计约定

### 1.1 页面状态模型

每个数据驱动的页面统一处理四种状态：

```
┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
│ Loading  │   │  Error   │   │  Empty   │   │  Normal  │
│ 骨架屏/   │   │ 错误提示 + │   │ 空状态图 + │   │  正常数据  │
│ Spin     │   │ 重试按钮  │   │ 引导文案  │   │  渲染     │
└──────────┘   └──────────┘   └──────────┘   └──────────┘
```

### 1.2 统一组件清单

| 组件 | 用途 | 位置 |
|------|------|------|
| `<SearchForm>` | 搜索筛选表单（关键词 + 下拉筛选） | `components/` |
| `<DataTable>` | 分页数据表格（Ant Table 封装） | `components/` |
| `<DetailDrawer>` | 详情抽屉（右侧滑出） | `components/` |
| `<StatusTag>` | 状态标签（颜色映射） | `components/` |
| `<PermissionGate>` | 权限守卫（按钮级） | `components/` |
| `<ConfirmAction>` | 二次确认弹窗（含原因输入） | `components/` |
| `<ExportButton>` | 导出按钮（含 loading 状态） | `components/` |

### 1.3 路由结构

```
/admin
├── /login                    # 登录页
├── /dashboard                # 仪表盘
├── /users                    # 用户列表
│   └── /users/:id            # 用户详情 (抽屉)
├── /subscriptions            # 订阅列表
│   └── /subscriptions/:id    # 订阅详情 (抽屉)
├── /payments                 # 支付流水
│   └── /payments/:id         # 支付详情 (抽屉)
├── /refunds                  # 退款审核
│   └── /refunds/:id          # 退款详情 (抽屉)
├── /audit                    # 审计日志
├── /admins                   # 后台用户管理 (仅 SUPER_ADMIN)
├── /notifications            # 通知模板
├── /config                   # 系统配置
├── /my-account               # 我的账户
├── /monitor
│   ├── /monitor/overview     # 服务健康
│   ├── /monitor/metrics      # API 指标
│   ├── /monitor/logs         # 日志检索
│   ├── /monitor/alerts       # 告警面板
│   └── /monitor/settings     # 日志级别调整
└── /403                      # 无权限
```

---

## 2. 页面具体设计

### 2.1 登录页 `/admin/login` `[P0]`

#### 组件树
```
LoginPage
├── LoginLayout (居中卡片布局)
│   ├── Logo + AppName
│   └── LoginCard
│       ├── LoginMethodTabs (PASSWORD | SMS) [P1]
│       │   └── LoginForm (按 grantType 动态切换)
│       │       ├── [PASSWORD] UserNameInput + PasswordInput + CaptchaImage + CaptchaInput + LoginButton [P0 已实现]
│       │       └── [SMS]       PhoneInput + SendCodeButton + CodeInput + CaptchaImage + CaptchaInput + LoginButton [P1]
│       └── TotpModal (PASSWORD 登录成功后按需弹出)
│           └── TotpInput + 6 Input squares + VerifyButton
└── SessionTimeoutOverlay (全局监听、非登录页时覆盖)
```

#### 状态处理

| 状态 | 表现 |
|------|------|
| 常规 | 显示登录表单，默认 PASSWORD 模式 |
| PASSWORD 登录中 | 按钮 loading + 禁用输入 |
| 返回 totpRequired=true | 弹出 TotpModal，不可关闭 |
| PASSWORD 错误 | form 下方 `message.error("密码错误")`，不清空输入 |
| 账号锁定 | form 下方 `message.error("账号已被临时锁定，15 分钟后重试")` |
| 账号禁用 | form 下方 `message.error("账号已被管理员禁用")` |
| 验证码错误 | form 下方 `message.error("验证码错误")`，自动刷新验证码 |
| 网络错误 | `message.error("网络异常，请重试")` |

#### 数据流
```
LoginForm
  ├── onSubmit(credentials) → apiClient.post('/admin/login', values)
  │     ├── 成功 → localStorage 存 token → router.push('/admin/dashboard')
  │     └── 失败 → message.error(error.message)
  │
  ├── CaptchaImage
  │     └── onMount / onClick → apiClient.get('/auth/captcha/image?sceneId=zhiyu_login')
  │           └── 成功 → 展示 Base64 验证码图片 + 保存 captchaToken
  │
  ├── WebAuthnLogin
  │     ├── onClick → setWebAuthnOpen(true)
  │     └── WebAuthnModal
  │           ├── 输入用户名
  │           └── onSubmit → authApi.webauthnAuthBegin(username)
  │                 ├── → navigator.credentials.get({ publicKey })
  │                 └── → authApi.webauthnAuthFinish(challengeId, credentialJson)
  │                       ├── 成功 → localStorage 存 token → router.push('/admin/dashboard')
  │                       └── 失败 → message.error("通行密钥认证失败")
  │
  └── TotpModal [P1]
        └── onSubmit(tempToken, totpCode) → authApi.login({ grantType: 'TOTP', tempToken, totpCode })
              ├── 成功 → useAuthStore.login() → router.push('/admin/dashboard')
              └── 失败 → 提示 TOTP 错误，保留弹窗
```

---

### 2.2 仪表盘 `/admin/dashboard` `[P1]`

#### 组件树
```
DashboardPage
├── PageHeader ("仪表盘")
├── StatCards (4 卡片行)
│   ├── StatCard (今日新增用户) [Trend: ↑12%]  → onClick → /admin/users
│   ├── StatCard (活跃订阅数)  [number]
│   ├── StatCard (今日营收)    [amount]
│   └── StatCard (在线用户)    [实时轮询 10s]
├── TrendChart (7日趋势 — ECharts 折柱混合)
│   ├── 日新增用户 (bar)
│   └── 日活跃用户 (line)
└── RecentAlerts (最近告警列表，最多 5 条)
    └── AlertItem → onClick → /admin/monitor/alerts
```

#### 状态处理

| 状态 | 表现 |
|------|------|
| Loading | 4 个 StatCard 骨架屏 + 图表 Spin |
| Error | `result.status='error'` + 图表空白 + 重试按钮 |
| Empty | 初始上线，数据均为 0 正常展示 |

#### 数据流
```
useEffect (mount)
  ├── GET /api/v1/admin/dashboard/stats → useQuery({ refetchInterval: 10_000 })
  │     └── { newUsers, activeSubs, revenue, onlineUsers }
  └── GET /api/v1/admin/dashboard/trend → useQuery({ refetchInterval: 60_000 })
        └── [{ date, newUsers, activeUsers }]
```

---

### 2.3 用户管理 `/admin/users` `[P0]`

#### 组件树
```
UserListPage
├── PageHeader ("用户管理")
│   └── Actions
│       ├── <PermissionGate permission="users.batch-disable">
│       │     └── BatchDisableButton (批量禁用)
│       └── <PermissionGate permission="users.export">
│             └── ExportButton (导出)
├── SearchForm
│   ├── KeywordInput (username/email/phone 模糊搜索)
│   ├── StatusSelect (ACTIVE | DISABLED | DELETED)
│   └── SearchButton + ResetButton
├── DataTable
│   ├── Columns: 用户名 | 邮箱(脱敏+验证标记) | 手机(脱敏+验证标记) | Scope | 状态 | 订阅 | 注册时间 | 操作
│   └── Row Actions (每行)
│       ├── [查看] → openDrawer(userId)
│       ├── <PermissionGate permission="users.view-identity">
│       │     └── [身份] → openIdentityDrawer(userId)
│       └── [禁用/启用] → ConfirmAction → PUT /admin/users/{id}/status
├── Pagination
└── UserDetailDrawer
    ├── 基本信息 (username, email [+ 验证标记 ✓/✗], mobile [+ 验证标记 ✓/✗], nickname, avatar)
    ├── Scope 标记 (LIMITED 橙色 / FULL 绿色)
    ├── 登录方式开关 (用户名登录 / 邮箱登录 / 手机号登录 — login_enable toggle)
    ├── 密码状态 (密码过期时间, 密码历史变更时间)
    ├── 安全信息
    │   ├── TOTP 双因素 (状态: 未启用/待激活/已激活 + 激活时间)
    │   ├── WebAuthn 通行密钥列表 (device_name, credential_id 脱敏, sign_count, last_used_time)
    │   └── 设备列表 (deviceName, platform, trusted_for_totp, lastActiveAt)
    ├── 认证身份列表 (provider 图标, openid 脱敏, nickname, avatar_url, enabled, 绑定时间)
    ├── 订阅信息 (plan, status, date range)
    └── 状态 + 生命周期 (enable/disabled/deleted, enable_expire, 注册时间, 最近登录时间)
```

#### 状态处理

| 状态 | 表现 |
|------|------|
| Loading | Table skeleton (3 行灰色条) |
| Error | `result.status='error'` + 重试 |
| Empty (无匹配) | `<Empty description="暂无匹配用户" />` |
| 批量禁用 | Table 多选 → 底部批量操作栏 → ConfirmAction(填写原因) → 调用 API |
| 导出 | ExportButton loading → 轮询下载链接 → 成功提示 |

#### 数据流
```
useQuery(['admin-users', filters], () => userApi.list(filters), {
  keepPreviousData: true  // 翻页保留旧数据
})
```

---

### 2.4 订阅管理 `/admin/subscriptions` `[P1]`

#### 组件树
```
SubscriptionListPage
├── PageHeader
├── SearchForm
│   ├── StatusSelect (ACTIVE | CANCELING | EXPIRED | REFUNDED)
│   ├── PlanSelect (lite | pro)
│   └── SearchButton
├── DataTable
│   Columns: 用户 | 套餐 | 状态 | 开始日期 | 结束日期 | 自动续费 | 操作
│   Row Actions: [详情] → openDrawer
└── SubscriptionDetailDrawer
    ├── 订阅基本信息
    ├── 支付流水列表 (该订阅的历史支付)
    └── <PermissionGate permission="subscriptions.modify">
          └── ModifyAction (EXTEND | UPGRADE | CANCEL | REFUND)
```

#### 状态处理

| 状态 | 表现 |
|------|------|
| 手动修改 | ConfirmAction(原因必填) → 成功刷新列表 + 记录审计 |
| 详情支付流水为空 | "暂无支付记录" |

---

### 2.5 支付流水 `/admin/payments` `[P1]`

#### 组件树
```
PaymentListPage
├── PageHeader
│   └── <PermissionGate permission="payments.export">
│         └── ExportButton (导出)
├── SearchForm
│   ├── ChannelSelect (WECHAT | ALIPAY | APPLE | GOOGLE)
│   ├── StatusSelect (SUCCESS | REFUND | FAIL)
│   ├── DateRangePicker (start ~ end)
│   └── SearchButton
├── DataTable
│   Columns: 订单号 | 用户 | 渠道 | 金额 | 状态 | 对账状态 | 支付时间 | 操作
│   Row Actions: [详情] → openDrawer
├── <PermissionGate permission="payments.reconcile">
│     └── ReconcilePanel (日期选择 + 手动触发 + 结果摘要)
└── PaymentDetailDrawer
    ├── 支付基本信息
    ├── 原始回调 JSON (RawNotification JSON Viewer)
    └── 对账信息
```

#### 状态处理

| 状态 | 表现 |
|------|------|
| 对账中 | ReconcilePanel 显示进度 |
| 对账差异 | MISMATCH 行高亮红色，ONLY_LOCAL/ONLY_REMOTE 行高亮黄色 |
| 导出 | 同用户导出逻辑 |

---

### 2.6 退款审核 `/admin/refunds` `[P1]`

#### 组件树
```
RefundListPage
├── PageHeader
├── SearchForm
│   ├── StatusSelect (PENDING_REVIEW | APPROVED | REJECTED | REFUNDED)
│   ├── DateRangePicker
│   └── SearchButton
├── DataTable
│   Columns: 退款单号 | 订单号 | 用户 | 金额 | 原因 | 状态 | 申请时间 | 操作
│   StatusBadge: PENDING_REVIEW=orange, APPROVED=green, REJECTED=red
│   Row Actions (仅 PENDING_REVIEW):
│     └── [详情] → openDrawer
├── RefundDetailDrawer
│   ├── 退款信息 (单号, 金额, 原因, 描述)
│   ├── 原订单信息 (订单号, 支付渠道, 交易号)
│   ├── 用户信息 (username, email)
│   └── <PermissionGate permission="refunds.approve">
│         ├── ApproveButton (填写审核意见 → 确认)
│         └── RejectButton (填写拒绝原因 → 确认)
└── ApproveModal
    └── NoteInput (备注) + ConfirmButton (危险操作红色)
```

#### 状态处理

| 状态 | 表现 |
|------|------|
| 审批通过 | 按钮 loading → 成功 → 列表自动刷新 |
| 审批拒绝 | 弹出 RejectModal(原因必填) → 确认 |
| 已处理退款 | 行操作区灰显，显示审核人和审核时间 |
| 退款回调后 | 状态自动从 APPROVED → REFUNDED |

---

### 2.7 审计日志 `/admin/audit` `[P0]`

#### 组件树
```
AuditPage
├── PageHeader ("审计日志")
│   └── <PermissionGate permission="audit.export">
│         └── ExportButton
├── Tabs
│   ├── Tab: 登录日志
│   │   └── LoginLogTable
│   │       Columns: 用户 | 方式 | 结果 | IP | 地理位置 | 设备 | 时间
│   │       Filters: userId, type, dateRange
│   ├── Tab: 身份变更
│   │   └── IdentityChangeTable
│   │       Columns: 用户 | 操作(BIND/UNBIND) | 认证类型 | IP | 时间
│   └── Tab: 管理员操作
│       └── AdminOperationTable
│           Columns: 操作人 | 动作 | 目标 | 详情(JSON展开) | IP | 时间
│           Filters: operatorId, action, dateRange
└── DetailJsonViewer (可展开的 JSON 阅读器，高亮变更字段)
```

#### 状态处理

| 状态 | 表现 |
|------|------|
| 读操作，无加载动画 | 查询后直接渲染 |
| 导出 | 同前 |

---

### 2.8 后台用户管理 `/admin/admins` (仅 SUPER_ADMIN) `[P1]`

#### 组件树
```
AdminManagementPage
├── PageHeader
│   └── CreateAdminButton → CreateAdminModal
├── DataTable
│   Columns: 用户名 | 角色 | 状态 | 最后登录 | 操作
│   Row Actions: [编辑] → EditModal | [删除] → ConfirmAction
├── CreateAdminModal
│   ├── UsernameInput
│   ├── PasswordInput
│   ├── RoleSelect
│   └── SubmitButton
├── EditAdminModal
│   ├── RoleSelect
│   ├── StatusToggle (ACTIVE | DISABLED)
│   └── SubmitButton
└── LoginHistoryDrawer (查看其登录历史)
    └── Table: 时间 | IP | 设备 | 结果
```

---

### 2.9 通知模板 `/admin/notifications` `[P2]`

#### 组件树
```
NotificationPage
├── PageHeader
├── TypeSelect (EMAIL | SMS | PUSH)
├── DataTable
│   Columns: 模板Key | 类型 | 描述 | 更新时间 | 操作
│   Row Actions: [编辑] → EditDrawer | [测试发送] → TestModal
├── EditDrawer
│   ├── SubjectInput (仅 EMAIL)
│   ├── BodyEditor (代码编辑器，YAML/HTML 语法高亮)
│   ├── VariablesList (可用的 {{变量}} 预览)
│   └── <PermissionGate permission="notifications">  # 仅 SUPER_ADMIN
│         └── SaveButton
└── TestModal
    ├── TargetInput (邮箱/手机号)
    ├── VariablesForm (key-value 输入)
    └── SendTestButton
```

---

### 2.10 系统配置 `/admin/config` `[P2]`

#### 组件树
```
ConfigPage
├── PageHeader
├── ConfigGroupList (左侧卡片列表)
│   └── ConfigGroupCard × 5 (subscription | notification | security | rate-limit | payment)
│       └── [name + 描述 + 风险等级 Badge]
├── ConfigEditor (右侧，选中 group 后显示)
│   ├── ConfigHeader (groupId/dataId、当前版本、更新时间)
│   ├── CodeEditor (YAML/JSON 语法高亮、格式化、校验)
│   ├── <PermissionGate permission="config.edit">
│   │     └── SaveButton (高风险配置 → TotpModal 二次验证)
│   └── ConfigHistoryPanel (下方)
│       └── VersionList → 点击查看历史内容 → <PermissionGate permission="config.rollback">
│             └── RollbackButton
└── TotpModal (高风险配置保存时弹出)
```

#### 状态处理

| 状态 | 表现 |
|------|------|
| 高风险配置保存 | 弹出 TOTP 验证，验证通过才写入 |
| 回滚确认 | ConfirmAction("将回滚到版本 N，当前修改将丢失") |
| 编辑冲突 | 若两个管理员同时编辑，后者保存时提示"配置已被他人修改，请刷新" |

---

### 2.11 我的账户 `/admin/my-account` `[P2]`

#### 组件树
```
MyAccountPage
├── PageHeader ("我的账户")
├── Tabs
│   ├── Tab: 个人信息
│   │   └── ProfileForm (姓名、头像、邮箱、手机)
│   │       └── SaveButton
│   ├── Tab: 修改密码
│   │   └── PasswordForm
│   │       ├── CurrentPasswordInput
│   │       ├── NewPasswordInput + ConfirmPasswordInput
│   │       └── SaveButton
│   ├── Tab: TOTP 设置
│   │   ├── TOTPStatus (已启用 / 未启用)
│   │   ├── SetupButton → QRCodeModal (secret + QR码)
│   │   ├── EnableButton + CodeInput
│   │   └── DisableButton (ConfirmAction)
│   ├── Tab: 登录历史
│   │   └── LoginHistoryTable (时间 | IP | 地理位置 | 设备 | 结果)
│   ├── Tab: 操作日志
│   │   └── OperationLogTable (时间 | 动作 | 目标 | 详情)
│   └── Tab: 活跃会话
│       └── SessionList
│           └── SessionItem (设备名 | 平台 | IP | 最后活跃时间)
│               └── KickOutButton (踢出其他设备)
```

---

### 2.12 运行监控 `/admin/monitor` `[P2]`

#### 2.12.1 概览 `/admin/monitor/overview`

```
MonitorOverviewPage
├── PageHeader ("运行监控")
├── HealthCards (4 卡片)
│   ├── HealthCard (应用实例) [UP/DOWN + 实例数]
│   ├── HealthCard (数据库)     [响应时间]
│   ├── HealthCard (Redis)      [连接状态]
│   └── HealthCard (Nacos)      [连接状态]
├── TodayStats (3 卡片)
│   ├── 今日 API 调用量
│   ├── 今日错误数
│   └── 当前在线用户
└── HealthStatusBadge: green(UP) / yellow(DEGRADED) / red(DOWN)
```

#### 2.12.2 API 指标 `/admin/monitor/metrics`

```
MonitorMetricsPage
├── TimeRangeSelector (1h | 6h | 24h | 7d)
├── QPSChart (ECharts area chart)
├── LatencyChart (P50/P95/P99 ECharts line chart)
└── ErrorRateChart (5xx rate ECharts line chart)
```

#### 2.12.3 日志检索 `/admin/monitor/logs`

```
MonitorLogsPage
├── Tabs
│   ├── Tab: 访问日志
│   ├── Tab: 应用日志
│   ├── Tab: 慢查询
│   └── Tab: 安全日志
├── SearchForm (按 Tab 动态切换 filter)
│   ├── [通用] DateRangePicker
│   ├── [访问] PathInput, StatusSelect, IpInput
│   ├── [应用] LevelSelect, KeywordInput
│   ├── [慢查询] MinMsInput
│   └── [安全] TypeSelect, IpInput
├── LogTable (可展开详情)
│   Columns: 时间 | Level | Logger | Message(截断) | traceId
│   ExpandRow: 完整 message + 异常堆栈 (代码块)
│   traceId 可点击 → 过滤该 traceId 的所有日志
└── LogStatsPanel (底部折叠)
    └── 7 天趋势图 + 按级别分布饼图
```

#### 2.12.4 告警面板 `/admin/monitor/alerts`

```
MonitorAlertsPage
├── AlertSummary (今日告警总数 | P0/P1/P2 分别)
├── AlertList
│   └── AlertItem
│       ├── SeverityBadge (P0=red, P1=orange, P2=yellow)
│       ├── AlertName, Condition, CurrentValue, FiredAt
│       └── Status (FIRING | RESOLVED)
└── 所有数据只读，来自 Prometheus AlertManager
```

#### 2.12.5 日志级别 `/admin/monitor/settings` (仅 SUPER_ADMIN)

```
LogLevelSettingsPage
├── PageHeader
├── LoggerList (搜索 + 表格)
│   Columns: Logger名称 | 当前级别 | 操作
│   Row Actions: [调整] → LevelSelect + ConfirmButton
├── LevelSelect (TRACE | DEBUG | INFO | WARN | ERROR | OFF)
└── AdjustmentHistory (调整记录)
    Column: 时间 | 操作人 | Logger | 旧级别→新级别 | 自动回滚时间
```

---

## 3. 全局交互规范

### 3.1 会话超时

```
全局 Hook: useSessionTimeout(idleThresholdMs = 30 * 60 * 1000)

行为:
1. 监听 mousemove / keydown / click / scroll / touchstart
2. 连续 idleThresholdMs 无操作 → 触发倒计时
3. 显示 SessionTimeoutModal (不可关闭):
   "会话即将超时，点击'继续使用'保持登录状态" + 60 秒倒计时
4. 用户点击"继续" → 重置 idle 计时 + 刷新 refresh_token
5. 倒计时归零 → 强制退出:
   - 调用 POST /api/v1/admin/auth/logout
   - 清空 useAuthStore
   - sessionStorage.setItem('postLogoutPath', window.location.pathname)
   - 跳转 /admin/login?reason=timeout
```

### 3.2 Token 自动刷新

```
Axios 响应拦截器:
1. 收到 40101 (Token 过期)
2. 检查是否正在刷新中:
   - 是 → 请求加入队列，等待刷新完成
   - 否 → 调用 POST /api/v1/admin/auth/refresh
3. 刷新成功 → 更新 useAuthStore → 重放队列中的请求
4. 刷新失败 (40103/40104) → 强制退出
```

### 3.3 权限控制

```
路由层:
  <ProtectedRoute permission="users">
    ├── 有权限 → 渲染页面
    └── 无权限 → 重定向 /admin/403

菜单层:
  SidebarMenu: 渲染前 filter(route.permission ∈ userPermissions)
  无权限的菜单项不出现在侧边栏

按钮层:
  <PermissionGate permission="users.batch-disable">
    ├── 有权限 → 渲染 children
    └── 无权限 → null
```

### 3.4 通知与消息

| 场景 | 方式 |
|------|------|
| 操作成功 | `message.success(...)` 绿色顶部提示，3 秒消失 |
| 操作失败 | `message.error(...)` 红色顶部提示，5 秒消失 |
| 危险操作 | `Modal.confirm(...)` 居中确认弹窗，红色确认按钮 |
| 表单提交 | 按钮 loading，防止重复提交 |

---

## 4. 数据流总览

```
┌─────────────────────────────────────────────────────────┐
│  Zustand Stores (全局状态)                                │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ useAuthStore │  │ useUIStore    │  │ useRouteStore │  │
│  │ user         │  │ sidebarOpen   │  │ currentPath   │  │
│  │ permissions  │  │ theme         │  │ breadcrumbs   │  │
│  │ tokens       │  │ locale        │  │               │  │
│  │ login/out    │  │               │  │               │  │
│  └─────────────┘  └──────────────┘  └───────────────┘  │
├─────────────────────────────────────────────────────────┤
│  React Query (服务端状态缓存)                              │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ useUserList  │  │ useSubList   │  │ useRefundList │  │
│  │ staleTime:30s│  │ staleTime:60s│  │ staleTime:10s │  │
│  └─────────────┘  └──────────────┘  └───────────────┘  │
├─────────────────────────────────────────────────────────┤
│  API Layer (Axios)                                       │
│  ┌──────────────────────────────────────────────────┐   │
│  │ Request interceptor: 注入 Authorization header     │   │
│  │ Response interceptor: 401 → refresh → 40104 → logout│   │
│  │ Base URL: /api/v1/admin                            │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## 5. 依赖版本锁定

| 包 | 版本 |
|------|------|
| react | ^18.3 |
| react-router-dom | ^6.23 |
| antd | ^5.17 |
| @ant-design/icons | ^5.3 |
| zustand | ^4.5 |
| @tanstack/react-query | ^5.40 |
| axios | ^1.7 |
| echarts | ^5.5 |
| echarts-for-react | ^3.0 |
| dayjs | ^1.11 |
| vite | ^5.2 |
| typescript | ^5.4 |
| vitest | ^1.6 |
| @testing-library/react | ^15.0 |
| msw | ^2.3 |
| @playwright/test | ^1.44 |

---

## 6. 环境变量体系

### 6.1 文件结构

```
frontend/
├── .env                        # 所有环境共享的默认值
├── .env.development            # 本地开发（Vite dev server）
├── .env.test                   # 测试环境（CI E2E）
├── .env.staging                # 预发布环境
├── .env.production             # 生产环境
└── .env.example                # 模板文件（提交 Git，不含真实值）
```

### 6.2 变量定义

| 变量名 | .env.development | .env.production | 说明 |
|--------|:---:|:---:|------|
| `VITE_API_BASE_URL` | `http://localhost:8080` | `/api/v1` | API 基地址（生产由 nginx 代理） |
| `VITE_APP_TITLE` | `ZhiYu Admin (DEV)` | `ZhiYu Admin` | 浏览器标签页标题 |
| `VITE_APP_VERSION` | `0.0.0-dev` | `$npm_package_version` | 应用版本号 |
| `VITE_ENABLE_MOCK` | `true` | `false` | 是否启用 MSW mock |
| `VITE_SENTRY_DSN` | _(空)_ | `https://...` | 错误监控 DSN |
| `VITE_GA_ID` | _(空)_ | `G-XXXXXXXXXX` | Google Analytics ID |
| `VITE_LOG_LEVEL` | `debug` | `error` | 前端日志级别 |
| `VITE_BUILD_DROP_CONSOLE` | `false` | `true` | 生产构建是否移除 console |
| `VITE_DEV_SERVER_PORT` | `5173` | — | Vite dev server 端口 |

### 6.3 使用方式

```typescript
// vite.config.ts — 通过 import.meta.env 访问
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  return {
    server: {
      port: parseInt(env.VITE_DEV_SERVER_PORT) || 5173,
      proxy: {
        '/api': {
          target: env.VITE_API_BASE_URL,
          changeOrigin: true,
        },
      },
    },
    define: {
      __APP_VERSION__: JSON.stringify(env.VITE_APP_VERSION),
      __ENABLE_MOCK__: env.VITE_ENABLE_MOCK === 'true',
    },
  };
});

// 运行时使用
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;
```

### 6.4 安全约束

- `.env.production` 中的 `VITE_SENTRY_DSN` 等第三方 Key **不提交 Git**，在 CI 中通过环境变量注入
- 所有 `VITE_*` 变量在构建时被静态替换为字符串字面量（Vite 行为），运行时不可变更
- **禁止**在 `.env` 文件中存储私钥、API Secret 等真正敏感的密钥（这些由后端管理）
- `.env.example` 仅包含变量名和说明，值为空或占位符 `changeme`

---

## 7. 前后端联调方案

### 7.1 本地开发代理

Vite dev server 通过 `proxy` 配置将 `/api` 请求转发到后端：

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',  // 后端 Spring Boot 端口
        changeOrigin: true,
        // WebSocket 支持（如 HMR 或实时推送）
        ws: true,
      },
    },
  },
});
```

**联调流程：**

```
┌──────────────────────┐     ┌──────────────────────┐
│  Vite Dev Server      │     │  Spring Boot          │
│  http://localhost:5173│     │  http://localhost:8080 │
│                       │     │                        │
│  前端页面请求          │     │                        │
│  fetch('/api/v1/...') │────▶│  代理转发到后端         │
│                       │     │  返回 JSON 响应         │
│                       │◀────│                        │
└──────────────────────┘     └──────────────────────┘
```

**启动顺序：**

```bash
# 终端 1: 启动后端
./mvnw spring-boot:run -pl zhiyu-server -Dspring.profiles.active=dev

# 终端 2: 启动前端（后端就绪后）
cd frontend && npm run dev
# → http://localhost:5173/admin/login
```

> **注意**：前端 dev server 启动不依赖后端是否就绪。Vite proxy 仅在请求 API 时才转发，404 时显示 "后端未启动" 提示。

### 7.2 无后端时的前端独立开发（MSW Mock）

当后端 API 尚未实现时，前端通过 MSW (Mock Service Worker) 拦截请求并返回模拟数据：

```typescript
// mocks/handlers/auth.ts
import { http, HttpResponse } from 'msw';

export const authHandlers = [
  // POST /api/v1/admin/auth/login
  http.post('/api/v1/admin/auth/login', async ({ request }) => {
    const body = await request.json();
    // 返回固定的测试响应
    return HttpResponse.json({
      code: 0,
      message: 'success',
      data: {
        accessToken: 'mock-access-token-xxx',
        refreshToken: 'mock-refresh-token-xxx',
        expiresIn: 900,
        user: { id: 1, username: 'admin', role: 'SUPER_ADMIN' },
        permissions: ['dashboard', 'users', 'audit'],
      },
    });
  }),

  // GET /api/v1/admin/users
  http.get('/api/v1/admin/users', () => {
    return HttpResponse.json({
      code: 0,
      message: 'success',
      data: {
        records: [
          { id: 1, username: 'testuser', email: 'u***@example.com', status: 'ACTIVE', createdAt: '2026-01-15T10:30:00Z' },
          { id: 2, username: 'vipuser', email: 'v***@example.com', status: 'ACTIVE', createdAt: '2026-02-20T14:00:00Z' },
        ],
        total: 2,
        page: 1,
        size: 20,
        pages: 1,
      },
    });
  }),
];
```

```typescript
// mocks/browser.ts
import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';

export const worker = setupWorker(...handlers);
```

```typescript
// main.tsx — 条件启用 MSW
async function enableMocking() {
  if (import.meta.env.VITE_ENABLE_MOCK !== 'true') return;
  const { worker } = await import('./mocks/browser');
  return worker.start({ onUnhandledRequest: 'warn' });
}

enableMocking().then(() => {
  ReactDOM.createRoot(document.getElementById('root')!).render(<App />);
});
```

**Mock 数据原则：**
- Mock 返回的数据结构必须与 API-SPEC.md 中定义的一致
- 覆盖四种状态：正常响应、空数据、错误（含错误码）、边界值（长文本、特殊字符）
- 模拟网络延迟：`import { delay } from 'msw';` → `await delay(300);`（300-800ms 随机）
- Mock handler 按模块拆分：`mocks/handlers/auth.ts`, `users.ts`, `audit.ts` 等

### 7.3 联调检查清单

| 检查项 | 说明 |
|--------|------|
| ✅ 后端 `/actuator/health` 返回 UP | 基础连通性 |
| ✅ Vite proxy 日志显示转发成功 | Dev server 日志中看到 `[proxy] /api/v1/... -> http://localhost:8080` |
| ✅ 登录接口返回正确 token | Cookie/Authorization header 正常设置 |
| ✅ CORS 未报错 | 同源代理不触发 CORS，无需额外配置 |
| ✅ WebSocket（如有）连接正常 | Vite proxy 配置了 `ws: true` |
| ✅ 生产构建的 SPA fallback | nginx `try_files $uri /index.html` |

---

## 8. API 类型生成与共享

### 8.1 策略

从后端 OpenAPI 规范自动生成 TypeScript 类型定义，确保前后端接口契约一致：

```
API-SPEC.md (权威规范)
        │
        ▼ (手动导出或工具解析)
  openapi.yaml (OpenAPI 3.0)
        │
        ▼ (openapi-typescript 或 openapi-generator)
  src/api/types.ts (TypeScript 类型)
        │
        ▼
  前端代码使用类型安全的请求/响应
```

### 8.2 工具选型

| 工具 | 方式 | 优缺点 |
|------|------|--------|
| **openapi-typescript** (推荐) | 生成纯类型文件（无运行时） | 轻量、类型安全、按需引用 |
| openapi-generator | 生成完整 HTTP client | 代码量大、更新需重新生成 |
| 手写类型 | 手动同步 | 灵活但容易漂移 |

### 8.3 生成流程

```bash
# 1. 从 API-SPEC.md 手动创建 openapi.yaml（一次性，后续维护）
# 2. 安装 openapi-typescript
npm install -D openapi-typescript

# 3. 从 openapi.yaml 生成 TypeScript 类型
npx openapi-typescript docs/openapi.yaml -o frontend/src/api/schema.d.ts

# 4. 在 CI 中校验类型与 OpenAPI 一致（防止漂移）
npm run typecheck
```

### 8.4 使用示例

```typescript
// src/api/schema.d.ts (自动生成)
export interface paths {
  '/api/v1/admin/users': {
    get: {
      parameters: { query: { page?: number; size?: number; keyword?: string; status?: 'ACTIVE' | 'DISABLED' } };
      responses: {
        200: { content: { 'application/json': ApiResponse<PageData<UserListResp>> } };
      };
    };
  };
}

// src/api/userApi.ts (业务代码中使用)
import type { paths } from './schema';

type UserListParams = paths['/api/v1/admin/users']['get']['parameters']['query'];
type UserListResp = paths['/api/v1/admin/users']['get']['responses']['200']['content']['application/json'];

export async function fetchUsers(params: UserListParams): Promise<UserListResp> {
  return apiClient.get('/admin/users', { params });
}
```

### 8.5 错误码类型

```typescript
// 从 ErrorCode 枚举生成的 TypeScript 类型
export const ErrorCodes = {
  TOKEN_EXPIRED: 40101,
  ACCOUNT_LOCKED: 40106,
  ACCOUNT_DISABLED: 40107,
  // ... 与后端 ErrorCode.java 保持同步
} as const;

export type ErrorCode = (typeof ErrorCodes)[keyof typeof ErrorCodes];

// 前端错误处理可以精确匹配
if (error.code === ErrorCodes.TOKEN_EXPIRED) {
  refreshToken();
} else if (error.code === ErrorCodes.ACCOUNT_DISABLED) {
  message.error('账号已被管理员禁用');
}
```

---

## 9. 移动端/平板响应式适配

### 9.0 Ant Design 已有的响应式能力

Ant Design 5.x 内置以下响应式能力，**无需额外实现**：

| 内置能力 | 说明 |
|----------|------|
| **Grid 栅格系统** | `Row`/`Col` 的 `xs/sm/md/lg/xl/xxl` 断点自适应，与 §9.2 断点一致 |
| **Layout.Sider** | `breakpoint` 属性在 ≤768px 时自动隐藏为 Drawer 模式 |
| **Menu** | `mode="inline"` / `mode="horizontal"` 通过 CSS 自动调整 |
| **Form** | `labelCol` / `wrapperCol` 的响应式 span（通过 `Form.Item` 的 `{ xs: 24, sm: 12 }` 等） |
| **Space** | 自动换行 |
| **Descriptions** | `column` 支持 `{ xxl: 4, xl: 3, lg: 2, md: 1 }` 断点对象 |
| **Affix / Anchor** | 固定定位自适应 |

**本项目策略**：充分利用 Ant Design 内置响应式能力 + 仅对以下 Ant Design 未覆盖的场景做补充适配：
- DataTable（Ant Table 无自动→CardList 切换）
- 触屏交互差异（hover / 右键 / 拖拽 在触屏上无对应行为）
- 页面级布局（底部 TabBar 替代侧边栏）

不引入 `antd-mobile`（它是独立组件库，API 不兼容，面向纯移动端 H5，不适合管理后台）。

### 9.1 目标

管理后台（zhiyu-admin-web）在以下设备上均可正常使用：

| 设备 | 宽度 | 目标 |
|------|------|------|
| 桌面 | ≥ 1200px | 完整布局（默认） |
| 平板横屏 | 768–1199px | 折叠侧边栏 + 表格横向滚动 |
| 平板竖屏 / 大屏手机 | 480–767px | 隐藏侧边栏 + 表格 → 卡片列表 |
| 小屏手机 | < 480px | 全屏操作 + 按钮折叠 |

### 9.2 断点定义

```typescript
// src/styles/breakpoints.ts
export const breakpoints = {
  xs: 480,   // 手机竖屏
  sm: 576,   // 手机横屏
  md: 768,   // 平板竖屏
  lg: 992,   // 平板横屏
  xl: 1200,  // 桌面
  xxl: 1600, // 大屏桌面
} as const;
```

```css
/* CSS Modules 中使用 */
@media (max-width: 768px) {
  .table-responsive {
    overflow-x: auto;
  }
}

@media (max-width: 480px) {
  .card-list {
    grid-template-columns: 1fr; /* 单列 */
  }
}
```

Ant Design 栅格 `Row/Col` 直接使用上述断点：

```tsx
<Row gutter={[16, 16]}>
  <Col xs={24} sm={24} md={12} lg={8} xl={6}>
    <StatCard title="今日新增用户" value={123} />
  </Col>
  <Col xs={24} sm={24} md={12} lg={8} xl={6}>
    <StatCard title="活跃订阅数" value={456} />
  </Col>
</Row>
```

### 9.3 布局适配策略

```
桌面 (≥1200px)
┌──────┬────────────────────────────────┐
│      │                                │
│ 固定  │         主内容区                │
│ 侧边栏 │    Table / Form / Chart       │
│ 240px│                                │
│      │                                │
└──────┴────────────────────────────────┘

平板 (768–1199px)
┌──┬───────────────────────────────────┐
│折│                                    │
│叠│          主内容区                    │
│侧│     Table (横向滚动)                 │
│边│     Form (label 在上)               │
│栏│     Chart (简化)                    │
└──┴───────────────────────────────────┘

手机 (<768px)
┌──────────────────────┐
│  ☰ 标题         [⋮]  │  ← 顶部导航栏（固定）
├──────────────────────┤
│                      │
│      主内容区         │
│   DataTable → Card   │
│   Drawer → 全屏 Modal │
│   StatCards → 单列    │
│   SearchForm → 折叠   │
│                      │
├──────────────────────┤
│ 底栏: 首页|用户|审计|我│  ← 底部 TabBar
└──────────────────────┘
```

#### 9.3.1 侧边栏

```tsx
// 桌面: 固定侧边栏; 平板: 折叠(仅图标); 手机: 完全隐藏 → 底部TabBar
const { md, lg } = useBreakpoint();

// ≥1200px: 展开侧边栏
// 768–1199px: 折叠为图标模式（hover 展开浮层）
// <768px: 隐藏侧边栏，改用 BottomTabBar + 汉堡菜单 Drawer
```

| 断点 | 侧边栏行为 | 菜单触发 |
|------|-----------|---------|
| ≥ 1200px | 固定展开，宽 240px | 常驻可见 |
| 768–1199px | 自动折叠为图标条，宽 64px | hover 展开浮层子菜单 |
| < 768px | 完全隐藏 | 左上角汉堡图标 ☰ → Drawer 侧滑菜单 |

#### 9.3.2 底部 TabBar（仅手机 < 768px）

```tsx
// 手机端底部固定 4 Tab
<BottomTabBar>
  <Tab icon={<HomeOutlined />} label="首页" to="/admin/dashboard" />
  <Tab icon={<UserOutlined />} label="用户" to="/admin/users" />
  <Tab icon={<AuditOutlined />} label="审计" to="/admin/audit" />
  <Tab icon={<SettingOutlined />} label="我的" to="/admin/my-account" />
</BottomTabBar>
```

Tab 对应 P0 四个核心页面。二级页面（详情、编辑）通过顶部返回按钮导航。

#### 9.3.3 顶部操作栏（手机 < 768px）

桌面端的操作按钮（批量禁用、导出、新建等）在手机上折叠为右下角 FAB 或右上角 `[更多]` 下拉菜单：

```tsx
// 桌面: 展开的操作按钮组
// 手机: 折叠到右上角 <Dropdown>
{isMobile ? (
  <Dropdown menu={{ items: actionMenuItems }}>
    <Button icon={<MoreOutlined />} />
  </Dropdown>
) : (
  <Space>
    <Button>批量禁用</Button>
    <Button>导出</Button>
  </Space>
)}
```

### 9.4 组件级适配

#### 9.4.1 DataTable → CardList

表格在手机上横向滚动体验极差。小于 768px 时，`<DataTable>` 自动切换为 `<CardList>`：

```tsx
// 自动切换逻辑
const isMobile = useBreakpoint().xs; // <480px

{isMobile ? (
  <CardList
    data={users}
    renderItem={(user) => (
      <Card
        title={user.username}
        extra={<StatusTag status={user.status} />}
        onClick={() => openDetail(user.id)}
      >
        <Descriptions column={1} size="small">
          <Item label="邮箱">{maskEmail(user.email)}</Item>
          <Item label="注册时间">{formatDate(user.createdAt)}</Item>
        </Descriptions>
      </Card>
    )}
  />
) : (
  <DataTable columns={columns} dataSource={users} />
)}
```

**转换规则：**

| 表格模式（桌面） | 卡片模式（手机） |
|:---|:---|
| 列: 用户名 \| 邮箱 \| 状态 \| 操作 | 卡片标题=用户名, 标签=状态, 内容=2-3 个关键字段 |
| 表头排序 ▲▼ | 卡片排序通过顶部 SegmentedControl |
| 多选 checkbox 列 | 卡片长按 → 多选模式 |
| 行内操作按钮 | 卡片右侧 > 箭头 → 操作底单 ActionSheet |
| 分页器 | 无限滚动（`IntersectionObserver`）或 "加载更多" 按钮 |

#### 9.4.2 DetailDrawer → 全屏 Modal

```tsx
// 桌面: 右侧滑出 Drawer (width=640)
// 平板: Drawer (width='100%', 最大 520px)
// 手机: 全屏 Modal
const drawerWidth = useBreakpoint().md ? 640 : '100%';
const Component = useBreakpoint().xs ? Modal : Drawer;
const componentProps = useBreakpoint().xs
  ? { open, onCancel: onClose, width: '100%', style: { top: 0, padding: 0 }, footer: null }
  : { open, onClose, width: drawerWidth, placement: 'right' };
```

#### 9.4.3 SearchForm 折叠

```tsx
// 桌面: 展开所有筛选项（横向排列）
// 平板: 展开主要筛选项，次要项折叠
// 手机: 默认折叠，点击「筛选」按钮展开全屏搜索面板
<SearchForm collapsible={isMobile ? 'fullscreen' : isTablet ? 'compact' : false}>
```

#### 9.4.4 StatCard 统计卡片

```tsx
// 桌面: 4 列; 平板: 2 列; 手机: 1 列
<Row gutter={[16, 16]}>
  <Col xs={24} sm={12} lg={6}><StatCard ... /></Col>
  <Col xs={24} sm={12} lg={6}><StatCard ... /></Col>
  <Col xs={24} sm={12} lg={6}><StatCard ... /></Col>
  <Col xs={24} sm={12} lg={6}><StatCard ... /></Col>
</Row>
```

#### 9.4.5 图表 (ECharts)

```tsx
// 图表在移动端自动降低复杂度
const chartOption = useMemo(() => ({
  ...baseOption,
  // ≤768px: 隐藏图例、减少数据点、缩小字体
  legend: { show: !isMobile },
  grid: isMobile
    ? { left: 0, right: 0, top: 10, bottom: 0 }
    : { left: 50, right: 20, top: 20, bottom: 30 },
  xAxis: {
    ...baseOption.xAxis,
    axisLabel: { rotate: isMobile ? 45 : 0, fontSize: isMobile ? 10 : 12 },
  },
}), [baseOption, isMobile]);

<ReactECharts
  option={chartOption}
  style={{ height: isMobile ? 200 : 400 }}
  opts={{ renderer: 'svg' }} // SVG 比 Canvas 在小屏上更清晰
/>
```

### 9.5 触屏交互适配

| 场景 | 桌面 | 手机/平板 |
|------|------|---------|
| 表格行操作 | hover 显示操作按钮 | 左滑显示操作按钮（SwipeAction） |
| Tooltip | hover 触发 | 长按触发 |
| Dropdown 菜单 | hover 展开 | 点击展开 |
| 右键菜单 | `onContextMenu` | 长按触发 ActionSheet |
| 表头拖拽列宽 | 支持 | **禁用**（改为固定列宽） |
| 文本选择 | 正常选择 | 长按选择 |
| 拖拽排序 | 支持 | **禁用**（改为上下箭头按钮） |
| 滚动区域 | 鼠标滚轮 + 滚动条 | 原生 touch scroll + 隐藏滚动条（`-webkit-overflow-scrolling: touch`） |

```css
/* 全局触屏优化 */
@media (pointer: coarse) {
  /* 触屏设备增大点击区域（最小 44x44px） */
  .ant-btn, .ant-table-row {
    min-height: 44px;
  }

  /* 隐藏滚动条但保留滚动能力 */
  .ant-table-body {
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
  }
  .ant-table-body::-webkit-scrollbar {
    display: none;
  }

  /* 移除 hover 依赖的样式（触屏无 hover） */
  .ant-table-row:hover {
    background: inherit;
  }
}
```

### 9.6 各页面移动端降级策略

| 页面 | 桌面 | 平板 | 手机 | 手机降级策略 |
|------|------|------|------|------------|
| 登录 `/admin/login` | 居中卡片 | 居中卡片 | 全屏垂直布局 | 卡片无边距，logo 缩小 |
| 仪表盘 `/admin/dashboard` | 4 列卡片 + 图表 | 2 列卡片 + 图表 | 1 列卡片 + 迷你图 | 隐藏图例，降低图表高度 |
| 用户管理 `/admin/users` | Table + Drawer | Table(横滚) + Drawer | CardList + 全屏 Modal | 仅显示 3 个关键字段 |
| 订阅管理 `/admin/subscriptions` | Table + Drawer | Table(横滚) + Drawer | CardList + 全屏 Modal | 仅显示 3 个关键字段 |
| 支付流水 `/admin/payments` | Table + Drawer | Table(横滚) + Drawer | CardList + 全屏 Modal | 金额、渠道、状态为主 |
| 退款审核 `/admin/refunds` | Table + Drawer | Table(横滚) + Drawer | CardList + 全屏 Modal | PENDING_REVIEW 卡片高亮 |
| 审计日志 `/admin/audit` | Table + 多 Tab | Table(横滚) + Tab | CardList + Tab 下拉 | JSON 详情折叠显示 |
| 后台用户管理 `/admin/admins` | Table + Modal | Table(横滚) + Modal | CardList + 全屏 Modal | 简化字段 |
| 通知模板 `/admin/notifications` [P2] | Table + Drawer | — | — | 不推荐手机编辑（代码编辑器体验差） |
| 系统配置 `/admin/config` [P2] | 左侧列表 + 右侧编辑器 | 列表 → 编辑器(切换) | 列表页 → 编辑器页(跳转) | 代码编辑器仍需键盘，提示"请在桌面端操作" |
| 我的账户 `/admin/my-account` | 多 Tab 表单 | 多 Tab 表单 | 单列垂直表单 | 所有字段纵向排列 |
| 运行监控 `/admin/monitor` [P2] | 多图表 Dashboard | 简化图表 | 仅显示实例状态 + 关键数字 | 不推荐手机深度分析 |

### 9.7 强制桌面端操作

以下操作**不支持**移动端，检测到移动端时显示引导提示：

| 操作 | 理由 | 提示文案 |
|------|------|---------|
| 配置编辑器（代码编辑） | 代码编辑器需要键盘 | "配置编辑功能建议在桌面端使用" |
| 批量 Excel 导出 | 下载大文件 + 手机存储限制 | "数据导出将在桌面端生效" |
| 日志检索（复杂筛选） | 多项筛选 + 日志阅读 | "日志检索建议在桌面端使用" |

```tsx
// 检测移动端并引导
<PermissionGate permission="config.edit">
  {isMobile ? (
    <Alert
      type="info"
      message="建议在桌面端编辑配置"
      description="手机端仅支持查看配置，编辑功能请在桌面浏览器中操作"
      showIcon
    />
  ) : (
    <CodeEditor ... />
  )}
</PermissionGate>
```

### 9.8 响应式测试清单

| 测试场景 | 工具 | 检查点 |
|----------|------|--------|
| 多设备预览 | Chrome DevTools Device Mode | iPad / iPhone SE / Pixel 7 / Galaxy Fold |
| 触摸事件 | DevTools → Sensors → Touch | 按钮可点击、列表可滑动、下拉刷新 |
| 横竖屏切换 | Device Mode rotate | 布局不破裂、表单输入不丢失 |
| 网络慢速 | Network → Slow 3G | 骨架屏正确显示、不白屏 |
| 系统字体缩放 | 系统设置 → 字体 120% | 布局不错乱、文字不截断 |

---

## 10. 无障碍访问 (Accessibility / a11y)

### 10.1 目标等级

**WCAG 2.1 AA** 合规，覆盖以下标准：

| 类别 | 关键要求 | 优先级 |
|------|---------|:----:|
| 可感知 (Perceivable) | 所有非文本内容有文本替代、颜色不是唯一传达信息的方式、对比度 ≥ 4.5:1 | P0 |
| 可操作 (Operable) | 所有功能可通过键盘操作、无键盘陷阱、有足够的操作时间 | P0 |
| 可理解 (Understandable) | 语言可编程检测、导航一致、输入辅助（错误提示） | P1 |
| 健壮 (Robust) | 最大化兼容辅助技术 (屏幕阅读器) | P1 |

### 10.2 具体实施

#### 色彩与对比度

| 元素 | 最小对比度 | Ant Design Token |
|------|:--------:|-----------------|
| 正文文本 | 4.5:1 | `colorText` / `colorTextSecondary` 满足 |
| 大文本 (≥18px 或 ≥14px bold) | 3:1 | `colorTextHeading` |
| 表单错误状态 | 不单独依赖颜色 | 错误文本 + 图标 + 边框三态 |
| 成功/警告/错误状态 | 配合图标使用 | `antd` Alert / Badge 组件自带 |

#### 键盘导航

```
焦点顺序 (Tab 键):
1. Skip Link "跳转到主内容" (仅键盘时可见)
2. 顶部导航 (Logo → 用户菜单)
3. 侧边栏菜单 (可展开/折叠子菜单)
4. 主内容区 (表单、表格、按钮)
5. 页脚

实现:
- 所有可交互元素 (button/a/input/select) 使用原生语义 HTML
- 自定义组件使用 tabIndex="0" 和 onKeyDown handler
- Modal/Drawer 打开时焦点移入、关闭时焦点返回触发器
- 表格行可通过 Enter 键选中、Space 键展开详情
```

#### 屏幕阅读器

```tsx
// ARIA 标注示例
<Table
  aria-label="用户列表"
  aria-describedby="user-table-desc"
/>

// 动态内容变更通知
<div role="status" aria-live="polite" aria-atomic="true">
  {loading ? '正在加载...' : `共 ${total} 条记录`}
</div>

// 表单错误关联
<Form.Item
  label="用户名"
  validateStatus={error ? 'error' : ''}
  help={error ? '用户名已被占用' : ''}
  // antd 自动关联 aria-describedby → error message id
>
  <Input aria-required="true" aria-invalid={!!error} />
</Form.Item>

// 图标按钮 (无文本标签)
<Button icon={<EditOutlined />} aria-label="编辑用户" />
<Tooltip title="删除">
  <Button icon={<DeleteOutlined />} aria-label="删除用户" />
</Tooltip>
```

#### 表单错误处理

```typescript
// 错误焦点管理: 表单提交失败后自动聚焦第一个错误字段
const formRef = useRef<FormInstance>(null);

const handleSubmitFailed = () => {
  const firstErrorField = document.querySelector('.ant-form-item-has-error input');
  (firstErrorField as HTMLElement)?.focus();
};
```

### 10.3 测试工具

| 工具 | 用途 | 集成 |
|------|------|------|
| axe-core / @axe-core/react | 自动化 a11y 审计 | CI + 开发时控制台 |
| eslint-plugin-jsx-a11y | 静态 JSX 规则检查 | ESLint |
| Lighthouse | 综合审计 (含 a11y 评分) | CI E2E 后 |
| VoiceOver (macOS) | 屏幕阅读器手动测试 | QA 每迭代 |
| NVDA (Windows) | 屏幕阅读器手动测试 | QA 每迭代 |

### 10.4 CI 检查

```yaml
# GitHub Actions 中
- name: Run axe accessibility check
  run: npx @axe-core/cli --exit --stdout src/**/*.tsx

- name: Run jsx-a11y lint
  run: npx eslint --rule 'jsx-a11y/alt-text: error' src/
```
