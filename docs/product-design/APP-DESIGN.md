# ZhiYu 客户端 App 设计规格

> 本文档定义 ZhiYu 客户端 App (iOS/Android) 的页面级设计规格。客户端独立于本仓库，原生开发（Swift + SwiftUI / Kotlin + Jetpack Compose），通过 HTTPS 调用后端 REST API。后端接口规格见 [API-SPEC.md](API-SPEC.md)。

## 1. 技术栈

| 平台 | 语言 | UI 框架 | 网络 | 持久化 | 推送 |
|------|------|---------|------|--------|------|
| iOS | Swift 5.10+ | SwiftUI | Alamofire / URLSession | Keychain + UserDefaults | APNs |
| Android | Kotlin 1.9+ | Jetpack Compose | OkHttp + Retrofit | EncryptedSharedPreferences + DataStore | FCM + 个推 |

**通用依赖：**
- WebAuthn: 平台原生 FIDO2 API
- TOTP: 本地时间同步算法（不依赖网络请求）
- JWT 存储: iOS Keychain / Android EncryptedSharedPreferences

## 2. 导航结构

```
TabBar (底部)
├── Tab 1: 首页 (Home)
├── Tab 2: 功能 (Feature) — 具体能力待业务方定义
├── Tab 3: 发现 (Discover)
└── Tab 4: 我的 (Profile)

Modal / Push
├── 登录/注册流程 (全屏 Modal)
│   ├── WelcomePage
│   ├── LoginPage
│   ├── RegisterFlow
│   │   ├── RegisterPage
│   │   ├── EmailVerifyPage
│   │   └── RegisterSuccessPage
│   ├── ThirdPartyLogin (WebAuth Session)
│   ├── SmsLoginPage
│   ├── ForgotPasswordPage
│   └── BindEmailPage (第三方首次登录)
├── 套餐页 (Push from Profile)
│   ├── PlanListPage
│   ├── PlanDetailPage
│   └── CheckoutPage
├── 设置页 (Push from Profile)
│   ├── SettingsPage
│   ├── AccountSecurityPage
│   │   ├── BindIdentityPage
│   │   ├── TotpSetupPage
│   │   └── WebAuthnSetupPage
│   ├── DeviceManagementPage
│   └── NotificationSettingsPage
└── WebView (内嵌 H5，如隐私政策、用户协议)
```

## 3. 页面设计

### 3.1 欢迎页 `/welcome`

```
WelcomePage (首次安装或登出后展示)
├── AppLogo + Slogan
├── FeatureCarousel (3-5 张特性介绍卡片，自动轮播)
├── CTAButton ("注册" / "登录")
└── ThirdPartyLoginButtons
    ├── WechatLoginButton
    ├── QQLoginButton
    ├── GoogleSignInButton
    └── AppleSignInButton
```

**状态：**
| 状态 | 表现 |
|------|------|
| 首次安装 | 展示完整引导 |
| 已登录 | 跳过此页，直接进入 TabBar |

### 3.2 注册流程 `/register`

```
RegisterPage
├── NavBar ("创建账户" + 关闭按钮)
├── UsernameInput (实时检测可用性，debounce 500ms)
│   └── AvailabilityIndicator (✅可用 / ❌已占用 + 建议)
├── EmailInput
├── PasswordInput (带强度指示条)
│   └── PasswordStrengthIndicator (弱/中/强 三色进度条)
├── CaptchaSlider (阿里云滑块验证)
├── SendCodeButton (Captcha 通过后才可点击)
├── CodeInput (6 位数字方格，自动聚焦跳转)
└── RegisterButton (全部校验通过后才可点击)

EmailVerifyPage (中间步骤)
├── Illustration (邮箱图标)
├── Text "验证邮件已发送至 xxx@example.com"
├── CountdownText ("60 秒后可重新发送")
└── OpenEmailButton (跳转系统邮件 App)

RegisterSuccessPage
├── SuccessIcon (勾选动画)
├── Text "欢迎加入 ZhiYu"
└── StartButton ("开始探索")
```

### 3.3 多种登录方式 `/login`

```
LoginPage
├── LoginMethodSegmentedControl (密码 | 短信)
├── [PASSWORD 模式]
│   ├── AccountInput (支持用户名/邮箱/手机号)
│   ├── PasswordInput
│   ├── ForgotPasswordLink
│   └── LoginButton
├── [SMS 模式]
│   ├── PhoneInput
│   ├── SendCodeButton
│   ├── CodeInput (6 位)
│   └── LoginButton
├── Divider ("或使用第三方登录")
└── ThirdPartyLoginButtons (同欢迎页)

TotpPrompt (登录后如已启用 TOTP)
├── Modal "请输入两步验证码"
├── CodeInput (6 位)
├── TrustDeviceToggle ("信任此设备，30 天内免验证")
└── VerifyButton
```

**状态：**
| 状态 | 表现 |
|------|------|
| 登录中 | 按钮 loading，禁用所有输入 |
| 密码错误 | 输入框下方红字"密码错误，还剩 N 次" |
| 账号锁定 | 全屏提示"账号已临时锁定，请 N 分钟后重试" |
| 账号禁用 | "账号已被管理员禁用，如有疑问请联系客服" |
| 第三方授权失败 | Toast "授权失败，请重试" |
| 网络超时 | "网络连接超时，请检查网络" + 重试按钮 |

### 3.4 个人中心 (Profile Tab)

```
ProfilePage
├── UserHeader (头像 + 昵称 + 套餐标签)
├── SubscriptionCard
│   ├── 当前套餐名称 + 状态标签
│   ├── 到期日期
│   ├── 配额使用情况 (进度环):
│   │   ├── 每日对话 (45/200)
│   │   └── 图片生成 (3/20)
│   └── [免费/游客] → "升级套餐" 按钮
│       [付费用户] → "管理订阅" 按钮
├── MenuList
│   ├── MenuItem "我的订单" → OrderHistoryPage
│   ├── MenuItem "设备管理" → DeviceManagementPage
│   ├── MenuItem "账户安全" → AccountSecurityPage
│   ├── MenuItem "通知设置" → NotificationSettingsPage
│   └── MenuItem "关于" → AboutPage
└── LogoutButton (底部)
```

**配额进度环：**
- 比例 < 50%: 绿色
- 50-80%: 橙色
- > 80%: 红色 + "即将用尽" 文案
- 用尽: 灰色 + "今日已用尽，明日 0:00 重置"

### 3.5 账户安全 `/account-security`

```
AccountSecurityPage
├── Section "已绑定的登录方式"
│   ├── IdentityRow (微信) [已绑定] → 解绑按钮
│   ├── IdentityRow (邮箱) [已绑定] → (主登录方式，不可解绑)
│   ├── IdentityRow (手机) [未绑定] → 绑定入口
│   ├── IdentityRow (Google) [未绑定] → 绑定入口
│   └── IdentityRow (Apple) [未绑定] → 绑定入口
├── Section "两步验证"
│   ├── TOTPRow (已启用/未启用) → TOTPSetupPage
│   └── WebAuthnRow (指纹/面容) → WebAuthnSetupPage
├── Section "密码"
│   └── ChangePasswordRow → ChangePasswordPage
└── Section "账户"
    └── DeactivateAccountRow (红色) → DeactivateConfirmModal
```

### 3.6 设备管理 `/devices`

```
DeviceManagementPage
├── CurrentDeviceBanner ("当前设备")
├── DeviceList
│   └── DeviceItem
│       ├── DeviceIcon (phone/laptop/tablet)
│       ├── DeviceName + Platform
│       ├── LastActiveAt ("最后活跃: 2 小时前")
│       ├── CurrentBadge (仅当前设备)
│       └── KickOutButton (非当前设备) → 确认弹窗
└── EmptyState (仅 1 台设备时隐藏列表)
```

**状态：**
| 状态 | 表现 |
|------|------|
| 设备数 = 1 | 仅显示当前设备，无踢出按钮 |
| 设备数 > 5 | 踢出旧设备时弹出 Toast "已达设备上限(5台)，请先下线其他设备" |

### 3.7 TOTP 设置 `/totp-setup`

```
TotpSetupPage
├── [未启用]
│   ├── ExplainText ("两步验证为您的账户提供额外安全保障")
│   ├── SetupButton → 获取 secret + QR 码
│   └── QRCodeView (otpauth:// 二维码 + 手动复制 secret)
│       └── CodeInput (验证一次 TOTP，确认绑定)
│           └── 成功 → RecoveryCodesPage
├── [已启用]
│   ├── EnabledBadge ("已启用 ✓")
│   ├── ViewRecoveryCodesButton (输入密码查看)
│   └── DisableButton (红色) → 确认 + 密码验证
└── RecoveryCodesPage
    ├── WarningText ("请妥善保存以下恢复码，每码仅可使用一次")
    ├── CodeList (5 个 8 位数字，可复制/截图)
    └── IHaveSavedButton → 返回
```

### 3.8 套餐选购 `/plans`

```
PlanListPage
├── PageTitle ("选择套餐")
├── PlanToggleSwitch (月付 / 年付 切换)
│   └── 年付 Badge "省 17%"
├── PlanCards (水平滑动或纵向排列)
│   ├── PlanCard (Free)
│   │   ├── PlanName + Price (¥0/月)
│   │   ├── FeatureList (✅ basic_chat, ✅ text_search)
│   │   └── CurrentBadge (如已在使用)
│   ├── PlanCard (Lite) [推荐标签]
│   │   ├── PlanName + Price (¥29/月)
│   │   ├── TrialBadge ("7 天免费试用")
│   │   ├── FeatureList
│   │   └── CTAButton ("开始试用" / "立即订阅")
│   └── PlanCard (Pro)
│       ├── PlanName + Price (¥99/月)
│       ├── FeatureList
│       └── CTAButton ("立即订阅")
└── RestorePurchasesLink (底部小字 "恢复购买？")

PlanDetailPage
├── 完整功能对比表 (Free vs Lite vs Pro)
├── FAQ (如何取消？如何退款？)
└── SubscribeButton

CheckoutPage
├── OrderSummary (套餐 + 周期 + 金额)
├── PaymentMethodPicker (微信支付 / 支付宝)
├── PayButton
└── CancelButton
```

**状态：**
| 状态 | 表现 |
|------|------|
| 已有活跃订阅 | PlanCard 上显示"当前套餐"，升级/降级操作 |
| Apple IAP 沙箱 | iOS 支付走 StoreKit，不展示支付方式选择 |
| 支付处理中 | PayButton loading，不关闭页面（等待回调） |
| 支付失败 | "支付失败: xxx" + 重新支付按钮 |
| 支付取消 | 返回 PlanListPage，不提示 |
| 恢复购买 | 调 verify-receipt (restoreMode=true) → 验证 → 激活 |

### 3.9 订单历史 `/orders`

```
OrderHistoryPage
├── SegmentedControl (全部 | 已支付 | 已退款)
├── OrderList
│   └── OrderItem
│       ├── OrderNo
│       ├── PlanName + Period
│       ├── Amount
│       ├── StatusBadge
│       ├── CreatedAt
│       └── [退款] → RefundApplyButton (仅已支付且未超期)
└── EmptyState ("暂无订单记录")

RefundApplyPage (Modal)
├── RefundReasonPicker (重复购买 | 误购 | 功能不满足 | 其他)
├── DescriptionInput (选填)
└── SubmitButton
```

---

## 4. 全局交互规范

### 4.1 认证状态管理

```
AppLaunch
  ├── 读取 Keychain/DataStore 中的 refresh_token
  ├── 若存在 → POST /auth/refresh → 获取新 access_token → 进入 Main
  ├── 若不存在 → 展示 WelcomePage

Token 过期处理:
  NetworkInterceptor 捕获 40101
    ├── 自动调用 /auth/refresh
    ├── 刷新成功 → 重放原请求
    └── 刷新失败 (40103/40104) → 清空 token → 跳转 WelcomePage
```

### 4.2 推送通知路由

| 推送类型 | 点击行为 |
|---------|---------|
| 订阅到期提醒 | → ProfilePage (订阅卡片高亮) |
| 续费成功 | → ProfilePage |
| 续费失败 | → PlanListPage |
| 安全通知 (新设备登录) | → DeviceManagementPage |
| 试用到期提醒 | → PlanListPage |

### 4.3 离线处理

| 场景 | 处理 |
|------|------|
| 浏览缓存数据 | 展示上次成功请求的数据 (React Query / Room 缓存) |
| 提交操作 | 显示 "无网络连接" Toast，操作入队列 |
| 网络恢复 | 自动重放队列中的操作 |

### 4.4 数据持久化

| 数据类型 | iOS | Android | 加密 |
|----------|-----|---------|:--:|
| JWT Token | Keychain | EncryptedSharedPreferences | ✅ |
| 用户偏好 | UserDefaults | DataStore | ❌ |
| 缓存数据 | SQLite (GRDB) | Room | ❌ |
| TOTP Secret | Keychain | EncryptedSharedPreferences | ✅ |

---

## 5. 页面流程全图

```
                    ┌──────────────┐
                    │  App Launch   │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │ 有 Token?     │
                    └──┬───────┬───┘
                   YES │       │ NO
               ┌───────▼──┐ ┌──▼──────────┐
               │  Main     │ │ WelcomePage  │
               │  TabBar   │ └──┬───────────┘
               └──┬───┬───┘    │
                  │   │   ┌────┴──────────┐
                  │   │   │ Login/Register │
                  │   │   └────┬───────────┘
                  │   │        │ 成功
                  │   └────────┤
                  │            │
    ┌─────────────┼────────────┼──────────────┐
    │             │            │              │
┌───▼──┐   ┌─────▼────┐  ┌───▼───┐  ┌──────▼──────┐
│ Home  │   │ Feature  │  │Discover│  │   Profile    │
│ Tab   │   │ Tab      │  │ Tab    │  │   Tab        │
└───────┘   └─────┬────┘  └───────┘  └──────┬───────┘
                  │                         │
            (业务方定义)          ┌──────────┼──────────┐
                                 │          │          │
                          ┌──────▼──┐ ┌───▼────┐ ┌───▼──────┐
                          │ 套餐     │ │ 安全   │ │ 订单/设备 │
                          │ 选购     │ │ 设置   │ │ 管理     │
                          └──────┬──┘ └────────┘ └──────────┘
                                 │
                          ┌──────▼──────┐
                          │  支付/结账   │
                          └─────────────┘
```
