# ZhiYu 管理后台前端设计规格

> 本文档定义 zhiyu-admin-web 的页面级设计规格，包括每个页面的组件树、交互状态（加载/空/错误/正常）和数据流向。技术栈与开发规范见 [DEVELOPMENT-STANDARDS.md](../DEVELOPMENT-STANDARDS.md)。

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

### 2.1 登录页 `/admin/login`

#### 组件树
```
LoginPage
├── LoginLayout (居中卡片布局)
│   ├── Logo + AppName
│   └── LoginCard
│       ├── LoginMethodTabs (PASSWORD | SMS | WECHAT | WECOM_QR)
│       │   └── LoginForm (按 grantType 动态切换)
│       │       ├── [PASSWORD] UserNameInput + PasswordInput + LoginButton
│       │       ├── [SMS]       PhoneInput + SendCodeButton + CodeInput + LoginButton
│       │       ├── [WECHAT]    WechatQRCode (iframe/二维码)
│       │       └── [WECOM_QR]  WecomQRCode (扫码)
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
| 微信/企微二维码过期 | 自动刷新二维码 |
| 网络错误 | `message.error("网络异常，请重试")` |

#### 数据流
```
LoginForm
  ├── onSubmit(grantType, credentials) → authApi.login()
  │     ├── 成功 (totpRequired=false) → useAuthStore.login() → router.push('/admin/dashboard')
  │     ├── 成功 (totpRequired=true)  → 打开 TotpModal
  │     └── 失败 → message.error(error.message)
  │
  └── TotpModal
        └── onSubmit(tempToken, totpCode) → authApi.login({ grantType: 'TOTP', tempToken, totpCode })
              ├── 成功 → useAuthStore.login() → router.push('/admin/dashboard')
              └── 失败 → 提示 TOTP 错误，保留弹窗
```

---

### 2.2 仪表盘 `/admin/dashboard`

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

### 2.3 用户管理 `/admin/users`

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
│   ├── Columns: 用户名 | 邮箱(脱敏) | 手机(脱敏) | 状态 | 订阅 | 注册时间 | 操作
│   └── Row Actions (每行)
│       ├── [查看] → openDrawer(userId)
│       ├── <PermissionGate permission="users.view-identity">
│       │     └── [身份] → openIdentityDrawer(userId)
│       └── [禁用/启用] → ConfirmAction → PUT /admin/users/{id}/status
├── Pagination
└── UserDetailDrawer
    ├── 基本信息 (username, email, phone, nickname, avatar)
    ├── 状态 + 生命周期时间
    ├── 认证身份列表 (type, identifier, createdAt)
    ├── 订阅信息 (plan, status, date range)
    └── 设备列表 (deviceName, platform, lastActiveAt)
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

### 2.4 订阅管理 `/admin/subscriptions`

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

### 2.5 支付流水 `/admin/payments`

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

### 2.6 退款审核 `/admin/refunds`

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

### 2.7 审计日志 `/admin/audit`

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

### 2.8 后台用户管理 `/admin/admins` (仅 SUPER_ADMIN)

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

### 2.9 通知模板 `/admin/notifications`

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

### 2.10 系统配置 `/admin/config`

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

### 2.11 我的账户 `/admin/my-account`

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

### 2.12 运行监控 `/admin/monitor`

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
