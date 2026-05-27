# ZhiYu-Backend 开发规范

> 本文档是 [设计规格](superpowers/specs/2026-05-17-zhiyu-backend-design.md) 的配套规范，定义代码分层、编程风格、API 约定、错误码体系、数据库命名、日志规范和测试策略。所有开发人员必须遵守。

---

## 快速开始

### 环境要求

| 工具 | 版本 | 说明 |
|------|------|------|
| JDK | 21 | [Eclipse Temurin](https://adoptium.net/) 推荐 |
| Maven | 3.9+ | 项目自带 `mvnw` wrapper，无需全局安装 |
| Docker | 24+ | 本地构建镜像，生产部署到 K8s |
| Node.js | 20 LTS | 仅前端 `admin-web` 模块需要 |

### 本地开发基础设施

> **一键部署：** 全新环境从零到一，使用 `./deploy/deploy.sh dev all` 自动完成基础设施部署、配置初始化、镜像构建和应用上线。详见 [§K8s 本地开发环境搭建](#k8s-本地开发环境搭建)。

项目使用 K8s + Docker 部署，本地开发使用 kubeadm 集群。基础设施（MySQL、Redis、Nacos）部署在开发环境 K8s Namespace 中。

开发时可通过 `kubectl port-forward` 将基础设施转发到本地：

```bash
# MySQL
kubectl port-forward svc/mysql -n zhiyu-dev 3306:3306 &
# Redis
kubectl port-forward svc/redis -n zhiyu-dev 6379:6379 &
# Nacos
kubectl port-forward svc/nacos -n zhiyu-dev 8848:8848 &
```

| 服务 | 本地转发地址 | 说明 |
|------|------|------|
| MySQL 8.0 | `localhost:3306` | 开发环境实例 |
| Redis 7 | `localhost:6379` | 开发环境实例 |
| Nacos 2.x | `http://localhost:8848/nacos` | 配置中心 + 注册中心 |

### 启动后端

```bash
# 编译 + 单元测试
./mvnw clean test

# 编译 + 全量测试（含集成测试，需要 Docker）
./mvnw clean verify

# 启动开发服务器（dev profile，从 Nacos 拉配置）
./mvnw spring-boot:run -pl zhiyu-server -Dspring.profiles.active=dev

# 打包
./mvnw clean package -DskipTests
```

应用启动后：
- API 基地址: `http://localhost:8080/api/v1`
- Actuator: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`（dev profile 启用）

### 首次配置 Nacos

```bash
# 1. 登录 Nacos 控制台 http://localhost:8848/nacos
# 2. 切换到 dev namespace
# 3. 创建以下配置：

# Data ID: zhiyu-backend.yml, Group: DEFAULT_GROUP, Format: YAML
# 内容见 ../deploy-ops/INFRASTRUCTURE.md §1.4.1

# Data ID: subscription-plans.yml, Group: SUBSCRIPTION, Format: YAML
# 内容见 ../deploy-ops/INFRASTRUCTURE.md §1.4.2
```

> **提示：** 首次启动前可先在 Nacos 中创建上述两条配置，否则应用会因缺少配置而启动失败。也可用 `spring.cloud.nacos.config.enabled=false` 临时跳过 Nacos，使用 `application-dev.yml` 本地配置。

### K8s 本地开发环境搭建

以下三种方式任选其一，均可在本地运行一个轻量级 K8s 集群用于开发。

#### 方式 A：Docker Desktop K8s（macOS/Windows 推荐）

1. 打开 Docker Desktop → Settings → Kubernetes → **Enable Kubernetes** → Apply & Restart
2. 等待左下角 Kubernetes 图标变绿（首次启动约 3-5 分钟）
3. 验证集群状态：

```bash
kubectl cluster-info
kubectl get nodes
```

> Docker Desktop 自带 `docker` 运行时和 `kubectl`，无需额外安装。

#### 部署基础设施到本地 K8s

```bash
# 1. 创建开发 namespace
kubectl create namespace zhiyu-dev

# 2. 部署 MySQL（单实例，无持久化 — 仅开发）
kubectl run mysql -n zhiyu-dev \
  --image=mysql:8.0 \
  --port=3306 \
  --env=MYSQL_ROOT_PASSWORD=<root-password> \
  --env=MYSQL_DATABASE=zhiyu_dev \
  --env=MYSQL_USER=zhiyu \
  --env=MYSQL_PASSWORD=<user-password>

kubectl expose pod mysql -n zhiyu-dev --port=3306

# 3. 部署 Redis
kubectl run redis -n zhiyu-dev --image=redis:7-alpine --port=6379
kubectl expose pod redis -n zhiyu-dev --port=6379

# 4. 部署 Nacos（单机模式）
kubectl run nacos -n zhiyu-dev \
  --image=nacos/nacos-server:v2.4.0 \
  --port=8848 \
  --port=9848 \
  --env=MODE=standalone \
  --env=SPRING_DATASOURCE_PLATFORM=mysql \
  --env=MYSQL_SERVICE_HOST=mysql \
  --env=MYSQL_SERVICE_DB_NAME=nacos \
  --env=MYSQL_SERVICE_USER=root \
  --env=MYSQL_SERVICE_PASSWORD=<root-password>

kubectl expose pod nacos -n zhiyu-dev --port=8848 --port=9848

# 5. 等待 Pod 就绪
kubectl wait --for=condition=ready pod --all -n zhiyu-dev --timeout=120s

# 6. 转发端口到本地
kubectl port-forward svc/mysql -n zhiyu-dev 3306:3306 &
kubectl port-forward svc/redis -n zhiyu-dev 6379:6379 &
kubectl port-forward svc/nacos -n zhiyu-dev 8848:8848 &

echo "基础设施已就绪: MySQL=localhost:3306, Redis=localhost:6379, Nacos=http://localhost:8848/nacos"
```

> **提示：** Pod 模式下服务通过 `kubectl port-forward` 转发。生产环境用 Deployment + Service 部署（见 `deploy/` 目录下 K8s 清单），本地开发用 Pod 即可。

### 项目结构速览

```
zhiyu-backend/
├── pom.xml                            # 父 POM（依赖管理 + 版本 BOM + 插件管理）
├── docs/                              # 全部设计文档
├── ufp-common/                      # 公共模块（工具/异常/安全 Filter/i18n）
├── zhiyu-auth/                        # 认证模块（P0 核心）
├── zhiyu-user/                        # 用户模块（P0 核心）
├── zhiyu-subscription/                # 订阅/支付模块（P1）
├── zhiyu-admin/                       # 管理后台模块（P0 核心 — 管理员功能）
└── zhiyu-server/                      # 可运行模块（入口 + 装配 + Flyway 迁移）
    └── src/main/resources/
        └── db/migration/              # Flyway SQL 迁移文件
```

> 完整开发流程（规划 → TDD → 代码审查 → 提交）见全局规则（用户级 CLAUDE.md 配置）。

> **当前实现状态（2026-05）：** 业务模块（`zhiyu-auth`、`zhiyu-user`、`zhiyu-subscription`、`zhiyu-admin`）为 Maven 空壳骨架，尚无业务代码。`zhiyu-server` 仅含 Spring Boot 入口 + Flyway 迁移。本文档中的分层规范、包结构约定为**目标架构**，实际代码将在后续开发中逐步填充。

---

## 1. 项目结构与分层规范

### 1.1 Maven 模块布局与职责

```
zhiyu-backend/
├── pom.xml                          # 父 POM（依赖管理 + 版本 BOM）
├── ufp/
│   ├── ufp-common/                 # UFP 基础设施 — 零 ORM/缓存
│   └── ufp-auth/                   # UFP 认证库 — JWT/BCrypt (library jar)
├── zhiyu-common/                   # ZhiYu 业务公共配置 (MyBatis-Plus/Redis)
├── zhiyu-auth/                     # 业务认证模块
├── zhiyu-user/                     # 用户资料模块（纯资料）
├── zhiyu-notification/             # 通知模块（邮件/SMS/Push）
├── zhiyu-subscription/             # 订阅/支付模块
├── zhiyu-admin/                    # 管理后台模块
└── zhiyu-server/                   # 可运行入口（装配 + 启动 + Flyway）
```

**依赖方向（单向，不可逆）：**

```
zhiyu-server
  └→ zhiyu-admin
       └→ zhiyu-subscription
            └→ zhiyu-user
                 └→ zhiyu-auth ──→ ufp-auth
                      │                │
                      └→ zhiyu-common ─┘
                           │
                           └→ ufp-common

所有业务模块（auth/user/subscription/admin）→ zhiyu-notification（通知注入）
```

`ufp-common` 不依赖任何业务模块。上层可依赖下层（通过 Service 接口注入），同层不互相依赖。禁止跨模块直接注入 Mapper。

#### ufp-common（公共模块）

**职责：** 所有业务模块共享的工具、异常、安全基础设施、i18n。

| 子包 | 内容 | 说明 |
|------|------|------|
| `util/` | JwtUtils, SecurityHelper, IpUtils, RandomUtils | 无状态工具类 |
| `exception/` | BizException, GlobalExceptionHandler, ErrorCode | 全局异常 + 错误码枚举 |
| `security/` | PasswordEncoder, JwtProvider | BCrypt + JWT RS256 加密/解密 |
| `filter/` | CorsFilter, JwtAuthFilter, RateLimitFilter, RequestLogFilter | Servlet Filter 链 |
| `dto/` | ApiResponse, PageReq, PageResp | 统一响应信封 + 分页基类 |
| `enums/` | UserStatus, IdentityType, OrderStatus | 跨模块共享的枚举 |
| `config/` | MyBatisPlusConfig, RedisConfig, JacksonConfig | 全局 Spring 配置 |
| `constant/` | RegexPatterns, CacheKeys, DateTimeConstants | 常量集中管理 |

**依赖：** 零业务依赖。仅依赖 Spring Boot Starter Web、Validation、Actuator、Hutool、Commons Lang3。

**i18n 说明：** `src/main/resources/i18n/` 下维护两套资源文件：
- `messages.properties` — 英文 fallback（默认语言）
- `messages_zh_CN.properties` — 中文简体

Spring MessageSource 通过 `Accept-Language` 请求头自动选择。默认 locale 为 `zh_CN`（在 application.yml 中配置 `spring.messages.basename=i18n/messages`）。错误消息、校验消息、业务通知均通过 key 引用，不在代码中硬编码文本。

#### zhiyu-auth（认证模块）— P0

**职责：** 用户身份的生命周期管理——注册、登录、登出、Token 管理、密码重置、验证码发送与校验。

| 子包 | 内容 |
|------|------|
| `controller/` | AuthController（注册/登录/登出/刷新）、CaptchaController（验证码） |
| `service/` | AuthService, TokenService, CaptchaService |
| `mapper/` | UserMapper, UserAuthIdentityMapper, LoginAttemptMapper |
| `entity/` | User, UserAuthIdentity, LoginAttempt |
| `dto/req/` | RegisterReq, LoginReq, SendCodeReq, ResetPasswordReq |
| `dto/resp/` | TokenResp, LoginResp |
| `enums/` | IdentityType（WECHAT\QQ\PHONE...）, GrantType |

**P0 实现范围：**
- 邮箱注册 + 密码登录
- JWT Token 签发/刷新
- CAPTCHA 防机器人
- 忘记/重置密码
- Token 黑名单 + 设备级退出

**依赖：** `ufp-common` + `spring-boot-starter-data-redis`

#### zhiyu-user（用户模块）— P0

**职责：** 用户个人信息的管理，认证方式的绑定与解绑，设备管理。

| 子包 | 内容 |
|------|------|
| `controller/` | UserController（个人信息查看/编辑）、DeviceController（设备管理） |
| `service/` | UserService, DeviceService |
| `mapper/` | UserSettingsMapper, UserDeviceMapper, UserTotpMapper |
| `entity/` | UserSettings, UserDevice, UserTotp |

**P0 实现范围：**
- 个人信息查看/编辑
- 账户注销（软删除，30 天冷却）
- 设备列表查看

**P1 扩充：**
- 绑定/解绑多种认证方式
- 多设备管理 + 踢出
- TOTP 设置与恢复码
- WebAuthn 通行密钥注册

**依赖：** `zhiyu-auth`

#### zhiyu-subscription（订阅/支付模块）— P1

**职责：** 套餐定义管理，订单创建与支付，订阅生命周期，支付回调幂等处理。

| 子包 | 内容 |
|------|------|
| `controller/` | PlanController（套餐列表）、OrderController（订单创建/支付回调） |
| `service/` | SubscriptionService, OrderService, PaymentService, QuotaService |
| `mapper/` | SubscriptionPlanMapper, SubscriptionOrderMapper, PaymentRecordMapper, RefundRecordMapper, QuotaUsageMapper |
| `entity/` | SubscriptionPlan, UserSubscription, SubscriptionOrder, PaymentRecord, RefundRecord, QuotaUsage |

**P0 实现范围：** 套餐列表查询、免费游客默认开通
**P1 实现范围：** 支付下单、回调、升级折价、降级预约、退款
**P2 实现范围：** IAP 票据验证、试用期、自动续费

**依赖：** `zhiyu-user` + 微信支付/支付宝 SDK

#### zhiyu-admin（管理后台模块）— P0

**职责：** 管理员鉴权与权限，后台用户管理，运营操作审计。

| 子包 | 内容 |
|------|------|
| `controller/` | AdminAuthController（管理员登录）、AdminUserController（用户管理）、AuditLogController（审计日志） |
| `service/` | AdminUserService, AuditService, ConfigService |
| `mapper/` | AdminUserMapper, AdminRoleMapper, AdminPermissionMapper, AuditLogMapper |

**P0 实现范围：**
- 管理员密码登录 + TOTP
- 用户管理（列表/详情/禁用）
- 审计日志查看

**P1/P2 扩充：** RBAC 多角色、退款审核、系统配置可视化管理、Dashboard 监控

**依赖：** `zhiyu-subscription`（可查看用户订阅状态 + 处理退款）

#### zhiyu-notification（通知模块）— P1

**职责：** 统一通知发送——邮件、SMS、Push，含模板渲染与发送记录。

| 子包 | 内容 |
|------|------|
| `controller/` | NotificationController（邮件/SMS/Push 发送、发送状态查询） |
| `service/` | NotificationService, EmailService, SmsService, PushService, TemplateService |
| `mapper/` | NotificationOutboxMapper, NotificationTemplateMapper |
| `entity/` | NotificationOutbox, NotificationTemplate |

**P1 实现范围：**
- 邮件发送（Thymeleaf 模板渲染 + 异步发送）
- SMS 发送（阿里云 SMS SDK）
- Outbox 模式保证发送可靠性
- 发送记录查询与重试

**P2 扩充：** Push 通知（FCM/APNs）、通知偏好管理

**依赖：** `zhiyu-common`（MyBatis-Plus/Redis 配置）

#### zhiyu-server（入口模块）

**职责：** Spring Boot 启动类、跨模块装配、Flyway 迁移、K8s 探针端点。不包含业务代码。

| 内容 | 说明 |
|------|------|
| `ZhiYuApplication.java` | `@SpringBootApplication` 主入口 |
| `application.yml` | 最小本地默认配置（真实配置在 Nacos） |
| `bootstrap.yml` | Nacos 配置引导（`spring.cloud.nacos.config` 连接信息） |
| `db/migration/` | Flyway 版本化 SQL 迁移脚本 |

**依赖：** 所有业务模块（通过 `zhiyu-admin` 传递依赖全链路）。

### 1.2 模块内包结构

每个业务模块采用领域驱动的包组织：

```
com.zhiyu.auth/
├── controller/       # REST Controller（薄层，仅参数转换和调用 Service）
├── service/          # 业务接口
│   └── impl/         # 业务实现（包级私有或 protected）
├── mapper/           # MyBatis-Plus Mapper 接口
├── entity/           # 数据库实体（PO，与表一一对应）
├── dto/              # 数据传输对象
│   ├── req/          # 请求 DTO（入参）
│   └── resp/         # 响应 DTO（出参，不含敏感字段）
├── vo/               # 视图对象（仅用于多表联合查询结果）
├── bo/               # 业务对象（Service 内部传递的中间结果）
├── converter/        # 对象转换器（MapStruct 或手写 static 工厂方法）
├── enums/            # 领域枚举
├── config/           # 本模块的 Spring 配置
└── exception/        # 本模块的自定义异常
```

### 1.3 DTO 命名约定

| 类型 | 命名模式 | 示例 | 说明 |
|------|---------|------|------|
| 请求 DTO | `<动作><对象>Req` | `CreateOrderReq`, `LoginReq`, `UserListReq` | 放 `dto/req/` 包，带 `@Valid` 校验注解 |
| 响应 DTO | `<对象>Resp` | `OrderResp`, `LoginResp`, `UserProfileResp` | 放 `dto/resp/` 包，不含敏感字段（password 等） |
| 分页请求 | `<对象>PageReq` | `UserPageReq`, `OrderPageReq` | 继承公共 `PageReq`（含 page, size, sort 字段） |
| 分页响应 | `<对象>PageResp` | `UserPageResp` | 继承公共 `PageResp<T>`（含 total, page, size, items） |
| 视图对象 | `<对象>VO` | `UserSubscriptionVO` | 仅用于多表联查结果，不直接暴露给前端 |
| 业务对象 | `<对象>BO` | `OrderAmountBO` | Service 内部传递，不暴露到 Controller 层 |
| 枚举 | `<含义>Enum` | `OrderStatusEnum`, `IdentityTypeEnum` | 放 `enums/` 包，MyBatis-Plus 自动映射 |

**转换器命名：**
```java
// MapStruct 接口
@Mapper(componentModel = "spring")
public interface OrderConverter {
    OrderResp toResp(SubscriptionOrder order);      // Entity → Resp
    SubscriptionOrder toEntity(CreateOrderReq req);  // Req → Entity
    void updateFromReq(UpdateOrderReq req, @MappingTarget SubscriptionOrder order);  // Req → Entity (增量)
}
```

> 转换器统一命名为 `<领域>Converter`，方法名遵循 `to<目标类型>` 模式。禁止在 Controller 中手写字段拷贝。

### 1.4 分层职责

```
┌─────────────┐
│ Controller   │ 职责：接收请求、参数校验（@Valid）、调用 Service、组装响应
│ (薄层)       │ 禁止：写业务逻辑、直接调 Mapper、开启事务
└──────┬──────┘
       │ 调用
┌──────▼──────┐
│ Service      │ 职责：业务编排、事务管理、权限检查、调用外部 API
│ (核心)       │ 禁止：处理 HTTP 请求/响应对象、拼接 SQL
└──────┬──────┘
       │ 调用
┌──────▼──────┐
│ Mapper       │ 职责：数据访问、SQL 映射（MyBatis-Plus BaseMapper + 自定义 XML）
│ (数据层)     │ 禁止：写业务逻辑、调用其他 Mapper（应在 Service 层编排）
└─────────────┘
```

**关键约束：**

- Controller **禁止**直接注入 Mapper
- Service **禁止**返回或接收 `HttpServletRequest`/`HttpServletResponse`
- Mapper 接口**禁止**使用 `${}` 占位符（防 SQL 注入）
- 跨模块调用：上层模块的 Service 注入下层模块的 Service，不得跨模块注入 Mapper

### 1.5 对象分类

| 类型 | 包位置 | 生命周期 | 说明 |
|------|--------|---------|------|
| Entity | `entity/` | 持久层 ↔ Service 层 | 数据库表一一对应，MyBatis-Plus `@TableName` 注解 |
| Req DTO | `dto/req/` | Controller 入参 | 含 `@Valid` 校验注解，不含数据库相关注解 |
| Resp DTO | `dto/resp/` | Controller 出参 | 不含密码/密钥等敏感字段 |
| VO | `vo/` | Service → Controller | 多表联合查询的扁平化结果 |
| BO | `bo/` | Service 内部 | 多步骤业务编排的中间对象，不暴露到 Controller |
| Enum | `enums/` | 全局 | 数据库字段映射（MyBatis-Plus `@EnumValue`），API 枚举值 |

**对象转换：**
- Entity ↔ Resp DTO：在 Controller 层或 Converter 中转换，**禁止** Entity 直接返回给客户端
- Req DTO → BO/Entity：在 Controller 或 Service 入口完成转换
- 使用 MapStruct `@Mapper(componentModel = "spring")` 或手写 `static` 工厂方法
- **禁止** `BeanUtils.copyProperties`（类型不安全，重构时无编译检查）

---

## 2. Maven 依赖与版本管理

### 2.1 父 POM 设计

父 POM 承担三项职责：版本 BOM 声明、插件统一配置、内部模块版本对齐。

```xml
<!-- pom.xml (root) 核心结构 -->
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.zhiyu</groupId>
    <artifactId>zhiyu-backend</artifactId>
    <version>${revision}</version>
    <packaging>pom</packaging>

    <modules>
        <module>ufp-common</module>
        <module>zhiyu-auth</module>
        <module>zhiyu-user</module>
        <module>zhiyu-subscription</module>
        <module>zhiyu-admin</module>
        <module>zhiyu-server</module>
    </modules>

    <properties>
        <!-- ===== 项目版本 ===== -->
        <revision>1.0.0-SNAPSHOT</revision>
        <java.version>21</java.version>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <!-- ===== 框架 BOM 版本（仅在此处声明） ===== -->
        <spring-boot.version>3.3.7</spring-boot.version>
        <spring-cloud.version>2023.0.3</spring-cloud.version>
        <spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>

        <!-- ===== 核心依赖版本 ===== -->
        <mybatis-plus.version>3.5.10</mybatis-plus.version>
        <mybatis-spring.version>3.0.4</mybatis-spring.version>
        <flyway.version>10.18.2</flyway.version>
        <jjwt.version>0.12.6</jjwt.version>
        <druid.version>1.2.24</druid.version>
        <mapstruct.version>1.6.3</mapstruct.version>

        <!-- ===== 集成依赖版本 ===== -->
        <wechat-pay.version>0.4.18</wechat-pay.version>
        <alipay-sdk.version>4.39.152</alipay-sdk.version>
        <aliyun-sms.version>2.0.24</aliyun-sms.version>
        <aliyun-oss.version>3.17.4</aliyun-oss.version>
        <lettuce.version>6.4.1.RELEASE</lettuce.version>

        <!-- ===== 测试依赖版本 ===== -->
        <testcontainers.version>1.20.4</testcontainers.version>
        <junit.version>5.11.4</junit.version>
        <mockito.version>5.14.2</mockito.version>
        <wiremock.version>3.9.2</wiremock.version>

        <!-- ===== 插件版本 ===== -->
        <maven-surefire-plugin.version>3.5.2</maven-surefire-plugin.version>
        <maven-failsafe-plugin.version>3.5.2</maven-failsafe-plugin.version>
        <maven-enforcer-plugin.version>3.5.0</maven-enforcer-plugin.version>
        <spotbugs-maven-plugin.version>4.8.6</spotbugs-maven-plugin.version>
        <jacoco-maven-plugin.version>0.8.12</jacoco-maven-plugin.version>
        <flatten-maven-plugin.version>1.6.0</flatten-maven-plugin.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot BOM -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- Spring Cloud BOM -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- Spring Cloud Alibaba BOM -->
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-alibaba-dependencies</artifactId>
                <version>${spring-cloud-alibaba.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- 内部模块 -->
            <dependency>
                <groupId>com.zhiyu</groupId>
                <artifactId>ufp-common</artifactId>
                <version>${revision}</version>
            </dependency>
            <!-- ...其他内部模块同理... -->
        </dependencies>
    </dependencyManagement>
</project>
```

### 2.2 BOM 依赖策略

项目导入 3 个 BOM，版本由 Spring Boot 主导，遇到冲突时遵循以下原则：

```
优先级（从高到低）：
1. spring-boot-dependencies (Spring Boot 3.3.x) — 基准
2. spring-cloud-dependencies (2023.0.x)      — Spring 生态
3. spring-cloud-alibaba-dependencies (2023.0.x) — 阿里生态
```

**BOM 内版本不重复声明**：已在 BOM 管理的依赖无需在 `dependencyManagement` 中重复（如 Jackson、HikariCP、Logback 等），直接声明 GAV 即可：

```xml
<!-- 正确：使用 BOM 版本，不写 version -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>

<!-- 正确：BOM 未管理的依赖，显示声明 version -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>${mybatis-plus.version}</version>
</dependency>
```

### 2.3 内部模块版本对齐

所有内部模块统一使用 `${revision}` 属性：

```xml
<!-- 子模块 pom.xml -->
<parent>
    <groupId>com.zhiyu</groupId>
    <artifactId>zhiyu-backend</artifactId>
    <version>${revision}</version>
    <relativePath>../pom.xml</relativePath>
</parent>

<artifactId>zhiyu-auth</artifactId>
<!-- version 继承自 parent，无需显式写 -->
```

发布时统一修改：
```bash
mvn versions:set -DnewVersion=1.0.0 -DremoveSnapshot=true
mvn versions:commit  # 确认
```

### 2.4 依赖冲突解决

#### 2.4.1 传递性依赖排除

```xml
<!-- 排除已知有漏洞的传递性依赖 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
    <exclusions>
        <exclusion>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>   <!-- 存在 CVE-2023-2976 -->
        </exclusion>
    </exclusions>
</dependency>
```

#### 2.4.2 依赖收敛检查

```xml
<!-- 父 POM: maven-enforcer-plugin 检查依赖版本收敛 -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <version>${maven-enforcer-plugin.version}</version>
    <executions>
        <execution>
            <id>enforce</id>
            <goals><goal>enforce</goal></goals>
            <configuration>
                <rules>
                    <dependencyConvergence/>
                    <requireJavaVersion>
                        <version>[21,)</version>
                    </requireJavaVersion>
                    <requireMavenVersion>
                        <version>[3.9,)</version>
                    </requireMavenVersion>
                    <bannedDependencies>
                        <excludes>
                            <!-- 禁止使用弃用库 -->
                            <exclude>commons-logging:commons-logging</exclude>
                            <exclude>log4j:log4j</exclude>
                            <!-- 禁止使用有严重漏洞的版本 -->
                            <exclude>com.google.guava:guava:(,32.0.0)</exclude>
                        </excludes>
                    </bannedDependencies>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

#### 2.4.3 冲突解决流程

```
mvn dependency:tree -Dverbose
  │
  ▼
 发现版本冲突
  │
  ├── 冲突在 BOM 管理范围内 → 升级 BOM 版本或显式 override
  ├── 冲突在 BOM 范围外 → <dependencyManagement> 显式声明版本
  └── 因传递性依赖引入错误版本 → <exclusions> + 显式声明正确版本
```

### 2.5 依赖 Scope 规范

| Scope | 使用场景 | 示例 |
|-------|---------|------|
| `compile` (默认) | 编译期和运行期都需要 | Spring Boot, MyBatis-Plus, jjwt |
| `runtime` | 仅运行期需要 | MySQL Driver, HikariCP (Spring Boot 默认) |
| `provided` | 运行时由容器提供 | Lombok (`provided` + `optional=true`) |
| `test` | 仅测试阶段 | JUnit, Testcontainers, Mockito, WireMock, H2 |

**关键检查：**
- `lombok` 用 `provided` + `<optional>true</optional>`以免传递给子模块
- `testcontainers` 必须是 `test` scope，不可泄露到 compile

### 2.6 依赖更新策略

| 依赖类型 | 更新频率 | 更新策略 |
|---------|:------:|---------|
| Spring Boot / Cloud | 次版本（.z 自动，.y 评估后） | Dependabot + 人工 review |
| Spring Cloud Alibaba | 跟随 Spring Boot 升级节奏 | 人工验证兼容性 |
| MyBatis-Plus | 每月检查 | Dependabot 提 PR |
| jjwt | 安全修复 → 立即 | 自动合并 (CVSS<4) |
| Testcontainers | 每月检查 | Dependabot 提 PR |
| 阿里云 SDK | 每季度检查 | 人工验证 API 兼容性 |

**自动化：**
```yaml
# .github/dependabot.yml
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
      day: monday
      time: "09:00"
      timezone: Asia/Shanghai
    open-pull-requests-limit: 10
    labels:
      - dependencies
    ignore:
      - dependency-name: "org.springframework.boot:*"
        update-types: ["version-update:semver-major"]
    groups:
      spring-ecosystem:
        patterns:
          - "org.springframework*"
          - "org.springframework.cloud*"
      test-deps:
        patterns:
          - "org.junit*"
          - "org.mockito*"
          - "org.testcontainers*"
```

### 2.7 插件版本锁定

所有插件版本在父 POM 的 `<pluginManagement>` 中锁定，确保 CI 构建可重复：

```xml
<pluginManagement>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>${maven-surefire-plugin.version}</version>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-failsafe-plugin</artifactId>
            <version>${maven-failsafe-plugin.version}</version>
        </plugin>
        <!-- flatten 插件: 解决 CI 替换 ${revision} 后子模块仍引用原始变量的问题 -->
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>flatten-maven-plugin</artifactId>
            <version>${flatten-maven-plugin.version}</version>
        </plugin>
    </plugins>
</pluginManagement>
```

---

## 3. Java 代码规范

### 2.1 类命名

| 类型 | 命名格式 | 示例 |
|------|---------|------|
| Controller | `XxxController` | `AuthController` |
| Service 接口 | `XxxService` | `TokenService` |
| Service 实现 | `XxxServiceImpl` | `TokenServiceImpl` |
| Mapper | `XxxMapper` | `UserMapper` |
| Entity | 表名单数驼峰 | `User`, `UserAuthIdentity` |
| Req DTO | `XxxReq` | `RegisterReq`, `LoginReq` |
| Resp DTO | `XxxResp` | `UserProfileResp`, `TokenResp` |
| VO | `XxxVO` | `UserSubscriptionVO` |
| BO | `XxxBO` | `PaymentResultBO` |
| Converter | `XxxConverter` | `UserConverter` |
| Enum | 描述性名称 | `UserStatus`, `IdentityType` |
| Config | `XxxConfig` | `RedisConfig` |
| Exception | `XxxException` | `AuthException`, `PaymentException` |
| Util | `XxxUtils` 或 `XxxHelper` | `JwtUtils`, `SecurityHelper` |

### 2.2 依赖注入

**强制使用构造器注入：**

```java
// 正确：Lombok 简化构造器注入
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserMapper userMapper;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
}

// 禁止：字段注入
@Service
public class AuthServiceImpl {
    @Autowired  // ❌
    private UserMapper userMapper;
}

// 禁止：Setter 注入
@Service
public class AuthServiceImpl {
    private UserMapper userMapper;

    @Autowired  // ❌
    public void setUserMapper(UserMapper userMapper) { ... }
}
```

### 2.3 方法与文件规模

| 约束 | 阈值 | 来源 |
|------|------|------|
| 方法最大行数 | 50 行 | 全局规则 |
| 文件最大行数 | 800 行 | 全局规则 |
| 参数最大数量 | 5 个（超过用 DTO/BO 封装）| 本规范 |
| 嵌套最大深度 | 4 层 | 全局规则 |

### 2.3.1 中文注释与 Clean Code 规约

所有 Java 开发人员编写的生产代码均须严格遵守以下中文注释及 Clean Code 规范：

1. **中文注释完备性**：
   * **文件头部注释**：每个 `.java` 文件的开头均须添加版权、文件名、创建时间及核心技术职责描述：
     ```java
     /*
      * Copyright (c) 2026 ZhiYu. All rights reserved.
      * 文件名: Xxx.java
      * 创建时间: 2026-05-27
      * 描述: [模块/功能具体用途描述]
      */
     ```
   * **类、接口与枚举注释**：所有类、接口与枚举的定义上一行必须加 Javadoc 注释，以明确其在业务分层中的角色和核心职责。
   * **方法注释**：所有公共方法（Public Methods）和业务核心方法必须声明 Javadoc，包括方法说明、`@param` 参数说明与 `@return` 返回值说明。
   * **关键流程行内注释**：对关键的防御性分支、事务提交、外部 API 交互或安全验证步骤，必须在代码块上方编写 `//` 行内注释。

2. **Clean Code 最佳实践**：
   * **魔鬼值硬编码消除**：禁止在业务逻辑方法体中直接硬编码魔鬼数字或中文字符串（如写死的错误消息、特定配置值、系统标识等）。必须将其重构并提取为 `private static final` 静态常量、全局常量配置或提取到 i18n 资源包。
   * **防御性编程要求**：对于批量接收的数据集合（如 `Collection`），在代入持久层条件构造器（如 MyBatis-Plus 的 `.in(...)`）前，必须进行 `null` 与 `isEmpty()` 的防御性前置校验，以防持久层执行生成语法错误的 SQL 异常。
   * **避免已过时的 API 库**：在持久层设计中，禁止使用过时废弃的批量 API（如 `selectBatchIds`），应选用更为类型安全的通用查询方法。

### 2.4 事务管理

```java
// Service 层统一管理事务，Controller 不得使用 @Transactional
@Service
public class SubscriptionServiceImpl implements SubscriptionService {

    @Transactional(rollbackFor = Exception.class)
    public OrderResp createOrder(Long userId, CreateOrderReq req) {
        // 1. 校验套餐有效性
        // 2. 创建订单
        // 3. 调用支付渠道
        // 4. 更新订单状态
    }

    // 只读操作显式标记
    @Transactional(readOnly = true)
    public List<PlanResp> listPlans() { ... }
}
```

**事务规则：**
- `@Transactional` 必须指定 `rollbackFor = Exception.class`（默认仅回滚 RuntimeException）
- 只读查询用 `@Transactional(readOnly = true)`，允许数据库读写分离优化
- 事务内**禁止**执行耗时操作（发邮件、短信、推送 → 移到事务外或异步）
- 事务边界内**禁止**调用外部 API（支付渠道、短信网关等不可回滚的操作）

#### 2.4.1 支付回调幂等

支付渠道（微信/支付宝/Apple/Google）可能**重复推送**同一笔支付回调，必须在多层级做幂等：

```
回调入口
  │
  ├── 第1层: Redis 幂等 Key
  │   SET pay:processed:<transactionId> 1 NX EX 30d
  │   NX 失败 → 直接返回 200（已处理）
  │
  ├── 第2层: DB 唯一约束
  │   uk_payment_transaction (channel, transaction_id)
  │   唯一冲突 → 直接返回 200
  │
  └── 第3层: 订单状态检查
      订单 status=PAID → 跳过，返回 200
```

```java
@Service
public class PaymentCallbackService {

    @Transactional(rollbackFor = Exception.class)
    public void handlePaymentCallback(PaymentCallbackDto dto) {
        // 第1层: Redis 幂等（快速短路）
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent("pay:processed:" + dto.getTransactionId(), "1", Duration.ofDays(30));
        if (Boolean.FALSE.equals(acquired)) {
            log.warn("Duplicate callback ignored: txn={}", dto.getTransactionId());
            return;
        }
        // 第2层: DB 唯一约束在 INSERT 时生效
        // 第3层: 订单状态检查
        SubscriptionOrder order = orderMapper.selectByOrderNo(dto.getOutTradeNo());
        if ("PAID".equals(order.getStatus())) {
            return;
        }

        // 更新订单 + 创建支付记录 + 更新订阅状态（同一事务）
        order.setStatus("PAID");
        order.setTransactionId(dto.getTransactionId());
        order.setPaidAt(LocalDateTime.now());
        orderMapper.updateById(order);

        PaymentRecord record = buildPaymentRecord(order, dto);
        paymentRecordMapper.insert(record);  // uk_payment_transaction 兜底

        updateUserSubscription(order);
    }
}
```

> **注意**：Redis 幂等 Key 写入和后续 DB 操作不在同一事务中。极端场景（Redis 写成功、DB 写失败）下回调重试时 Redis 短路导致丢回调的概率极低（回调间隔通常 > 1s），且 `uk_payment_transaction` 唯一约束提供最终一致性保障。

#### 2.4.2 外部调用的事务边界

任何事务内调用外部 API 的代码都是**反模式**。外部 API 无法回滚，事务失败后会产生脏数据：

```
❌ 错误: 事务内调支付渠道
@Transactional
public void createOrder(CreateOrderReq req) {
    orderMapper.insert(order);             // DB 写入
    String prepayId = wxPayApi.unifiedOrder(order);  // ← 外部调用
    order.setPrepayId(prepayId);
    orderMapper.updateById(order);
}  // DB 回滚 → WxPay 已下单无法取消

✅ 正确: 事务外调支付渠道
public OrderResp createOrder(CreateOrderReq req) {
    // Step 1: 事务内创建 PENDING 订单
    SubscriptionOrder order = createPendingOrder(req);
    // Step 2: 调用支付渠道（事务外）
    String prepayId;
    try {
        prepayId = wxPayApi.unifiedOrder(order);
    } catch (Exception e) {
        // Step 3a: 支付渠道调用失败 → 标记订单 CANCELLED
        cancelOrder(order.getId());
        throw new PaymentException("支付渠道调用失败", e);
    }
    // Step 3b: 更新预支付信息
    updatePrepayInfo(order.getId(), prepayId);
    return buildResponse(order, prepayId);
}

@Transactional(rollbackFor = Exception.class)
private SubscriptionOrder createPendingOrder(CreateOrderReq req) {
    SubscriptionOrder order = buildOrder(req);
    order.setStatus("PENDING");
    orderMapper.insert(order);
    return order;
}
```

**原则：** 外部调用前后各一个独立事务，失败时主动补偿。

#### 2.4.3 订单状态机

订单和退款单遵循严格的状态转换规则，禁止跨状态跳跃：

```
subscription_order 状态流转:
  PENDING ──┬──> PAID ──────> REFUNDED
            ├──> CANCELLED
            └──> EXPIRED        (超时未支付, 30 分钟)

refund_record 状态流转:
  PENDING_REVIEW ──┬──> APPROVED ──> REFUNDED
                   └──> REJECTED
```

```java
// 状态转换枚举
public enum OrderStatus {
    PENDING {
        @Override
        public Set<OrderStatus> allowedTransitions() {
            return Set.of(PAID, CANCELLED, EXPIRED);
        }
    },
    PAID {
        @Override
        public Set<OrderStatus> allowedTransitions() {
            return Set.of(REFUNDED);
        }
    },
    CANCELLED, EXPIRED, REFUNDED;

    public Set<OrderStatus> allowedTransitions() {
        return Set.of();
    }

    public void validateTransition(OrderStatus target) {
        if (!allowedTransitions().contains(target)) {
            throw new IllegalStateException(
                String.format("非法状态转换: %s → %s", this, target));
        }
    }
}
```

> 所有状态变更方法必须先调用 `validateTransition()`，不合法的转换直接抛异常。

#### 2.4.4 乐观锁并发控制

配额扣减、订单状态更新等并发写场景使用 `version` 字段做乐观锁：

```sql
-- 需要乐观锁的表在 DDL 中添加 version 字段
ALTER TABLE quota_usage ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号';
```

```java
// MyBatis-Plus @Version 注解自动处理
@Data
@TableName("quota_usage")
public class QuotaUsage {
    @Version
    private Integer version;
    // ...
}

// Service 层处理并发冲突
@Transactional(rollbackFor = Exception.class)
public boolean deductQuota(Long userId, String quotaKey, long amount) {
    int updated = quotaUsageMapper.deductWithVersion(userId, quotaKey, amount);
    if (updated == 0) {
        // 版本冲突，重试（最多 3 次）
        throw new OptimisticLockException("配额扣减冲突，需重试");
    }
    return true;
}
```

```xml
<!-- Mapper XML: 带版本号的扣减 -->
<update id="deductWithVersion">
    UPDATE quota_usage
    SET used_count = used_count + #{amount},
        version = version + 1
    WHERE user_id = #{userId}
      AND quota_key = #{quotaKey}
      AND version = #{version}
      AND used_count + #{amount} <= limit_count
</update>
```

**可选升级**：高并发场景（如秒杀）可将扣减操作移到 Redis Lua 脚本中原子执行，异步回写 DB。

#### 2.4.5 MySQL + Redis 双写一致性

Cache-Aside 模式下，采用**先更新 DB，再删除 Redis**策略避免并发写导致的缓存不一致：

```java
@Transactional(rollbackFor = Exception.class)
public void updatePlan(UpdatePlanReq req) {
    // 1. 先更新 DB
    SubscriptionPlan plan = planMapper.selectById(req.getId());
    plan.setPriceMonthly(req.getPriceMonthly());
    plan.setFeaturesJson(req.getFeatures());
    planMapper.updateById(plan);

    // 2. 事务提交后再删 Redis（避免回滚后缓存已删）
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisTemplate.delete("cache:subscription:plans");
            }
        });
}
```

**原则：**
- **写**：先 DB（事务内），后删 Redis（afterCommit）
- **读**：先 Redis，miss 后查 DB 并回填 Redis + TTL
- **不推荐**：先删 Redis 再更新 DB（并发读会回填旧值）
- **禁止**：先更新 Redis 再更新 DB（DB 回滚后 Redis 有脏数据）

> 更多缓存策略细节见 [INFRASTRUCTURE.md §2.6-2.7](../deploy-ops/INFRASTRUCTURE.md)。

#### 2.4.6 异步 Outbox 模式

支付成功、退款审批等需要发送通知的场景，不能在事务内发邮件/推送。采用 Outbox 模式：

```
事务提交
  │
  └── afterCommit
        │
        └── INSERT INTO outbox_event
              (event_type, payload_json, status, created_at)
              VALUES ('PAYMENT_SUCCESS', '{...}', 'PENDING', NOW())
                    │
                    ▼
              ┌─────────────┐
              │ 定时轮询 Job │  (每 5s 一次)
              │ SELECT ...   │
              │ WHERE status │
              │ = 'PENDING'  │
              │ LIMIT 100    │
              └──────┬───────┘
                     │
                     ▼
              ┌─────────────┐
              │ 发送通知     │
              │ 更新 status  │
              │ = 'SENT'    │
              └─────────────┘
```

```java
// 支付回调中：事务提交后创建 Outbox 事件
@Transactional(rollbackFor = Exception.class)
public void handlePaymentCallback(PaymentCallbackDto dto) {
    // ... 订单更新逻辑 ...

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                OutboxEvent event = OutboxEvent.builder()
                    .eventType("PAYMENT_SUCCESS")
                    .payloadJson(JsonUtil.toJson(buildNotificationPayload(order)))
                    .status("PENDING")
                    .build();
                outboxEventMapper.insert(event);
            }
        });
}
```

> Outbox 表 DDL 见 [DATABASE.md §3.9](../product-design/DATABASE.md#39-异步事件)，由定时 Job 轮询发送，保证通知至少发送一次（At-Least-Once），下游需做幂等。

---

### 2.5 MyBatis-Plus 使用约定

```java
// Mapper 继承 BaseMapper 获得 CRUD 能力
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 简单查询用 LambdaQueryWrapper（类型安全）
    // 复杂查询写在 XML 中

    // 分页统一用 IPage 返回值
    IPage<User> selectPageWithFilters(Page<User> page, @Param("filter") UserFilter filter);
}
```

**强制规则：**
- XML 中的 `${}` 占位符**绝对禁止**，只允许 `#{}`
- 分页查询统一返回 MyBatis-Plus `IPage<T>` 或自定义分页 VO
- 批量插入/更新用 MyBatis-Plus 的 `saveBatch`/`updateBatch`（默认每批 1000 条）
- 逻辑删除用 `@TableLogic` 注解（如用户软删除标记 `status=DELETED`）
- 自动填充用 `@TableField(fill = FieldFill.INSERT)`（createTime/updateTime）

### 2.6 参数校验

```java
// Controller 层：所有入参用 @Valid + Jakarta Bean Validation
@PostMapping("/register")
public ApiResponse<UserProfileResp> register(@Valid @RequestBody RegisterReq req) {
    return ApiResponse.success(authService.register(req));
}

// DTO 中定义校验规则
public class RegisterReq {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度 3-32 位")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "用户名仅允许字母数字下划线连字符")
    private String username;

    @NotBlank @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    @NotBlank
    private String captchaToken;
}
```

**禁止使用 `Map`、`JSONObject`、`String` 裸类型作为 Controller 入参**——所有输入必须定义专用 DTO。

### 2.7 不可变性

```java
// 正确：返回不可变集合
public List<PlanResp> listPlans() {
    return List.copyOf(plans);  // 或 Collections.unmodifiableList()
}

// 正确：Entity 使用链式 setter 创建新对象
public User withStatus(UserStatus status) {
    User copy = new User();
    copy.setId(this.id);
    copy.setUsername(this.username);
    // ... 复制其他字段
    copy.setStatus(status);
    return copy;
}

// 禁止：直接修改参数对象
public void activateUser(User user) {
    user.setStatus(UserStatus.ACTIVE);  // ❌ 修改入参
}
```

### 2.8 Lombok 使用约定

| 注解 | 使用场景 | 注意事项 |
|------|---------|---------|
| `@RequiredArgsConstructor` | Service/Component/Controller | 构造器注入 |
| `@Getter` | Entity/Req DTO/Resp DTO | Entity 不生成 Setter（用 withXxx 方法代替） |
| `@Setter` | Req DTO 仅 | Service 内部 BO 可用 |
| `@Data` | **禁止** | 同时生成 Getter+Setter+equals+hashCode，Entity 上 equals/hashCode 容易出错 |
| `@Builder` | Resp DTO、BO | 构建模式创建对象 |
| `@Slf4j` | 所有需要日志的类 | 统一用 `log.xxx()` |

### 2.9 杂项

- **禁止** `System.out.println` 和 `e.printStackTrace()`，始终用 `log`
- **禁止** 在循环中拼接 SQL 或执行数据库查询（用批量接口）
- **禁止** 捕获异常后什么都不做（至少 `log.warn` 记录）
- 字符串比较：字面量在前 `"CONSTANT".equals(variable)` 防 NPE
- Optional：仅用于返回值，**禁止**作为字段类型或方法参数

### 2.10 静态检查与规则排除规约

为了确保在多模块架构下静态规范检查（Checkstyle、SpotBugs、PMD）的顺利进行以及代码质量的严苛把关，沉淀以下重构与静态检查排除最佳实践：

1. **MapStruct 自动生成代码的 SpotBugs 排除**：MapStruct 产生的实现类（如 `*Impl`）在字节码层面可能导致 `CT_CONSTRUCTOR_THROW`（构造函数内安全终结器漏洞）假阳性警报。所有此类由生成工具控制的实现类应统一在根目录 `spotbugs-exclude.xml` 中使用 `<Class name="~.*\.converter\..*Impl" />` 进行过滤排除，杜绝随意在业务代码中滥用 `@SuppressWarnings` 压制警告。
2. **多模块路径下的静态检查配置文件寻址**：多模块 Maven 项目在子模块运行分析时易因相对路径寻址错误而构建失败。在 `pom.xml` 中配置 SpotBugs 排除文件路径时，必须采用 `${maven.multiModuleProjectDirectory}/spotbugs-exclude.xml` 来实现准确的全路径寻址。
3. **Locale 敏感的字符串大小写转换**：禁止使用 `String.toLowerCase()` 或 `String.toUpperCase()` 进行默认本地大小写转换（防范土耳其语 `I` 转小写等系统区域引发的边界不一致 Bug，PMD `UseLocaleWithCaseConversions` 规则）。必须显式指定 `Locale.ROOT`（例如：`path.toLowerCase(java.util.Locale.ROOT)`）以保证无 Locale 相关性的大小写匹配行为。
4. **工具类与 Spring Boot 启动类的声明与私有化构造**：所有仅包含 `static` 静态方法的工具类（或 Spring Boot 入口启动类），为满足 PMD 的 `UseUtilityClass` 规则，应将其声明为 `public final class`，并添加一个空的私有构造函数 `private Xxx() {}` 以防止被误实例化。同时，为规避 SpotBugs `CT_CONSTRUCTOR_THROW` 终结器攻击，禁止在该私有构造函数中抛出异常。

---

## 4. API 响应格式规范

### 3.1 统一成功响应

所有 API 返回统一 envelope 结构：

```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": 1716019200
}
```

**字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | 0 表示成功；非 0 表示错误，值为具体错误码 |
| `message` | string | 成功时固定 `"success"`；失败时为人类可读错误描述 |
| `data` | object/array/null | 成功时携带业务数据；失败时 `null` |
| `requestId` | string(UUID) | 请求追踪 ID，与日志 traceId 一致 |
| `timestamp` | long(epoch秒) | 响应生成时间 |

### 3.2 统一错误响应

```json
{
  "code": 40101,
  "message": "access_token 已过期，请使用 refresh_token 刷新",
  "data": null,
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": 1716019200
}
```

错误响应的 `data` 始终为 `null`。**禁止**在 `message` 中暴露堆栈信息或内部状态。

### 3.3 分页响应

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [...],
    "total": 1523,
    "page": 1,
    "size": 20,
    "pages": 77
  },
  "requestId": "...",
  "timestamp": 1716019200
}
```

### 3.4 Java 实现

```java
// 通用响应类（放在 ufp-common 模块）
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private String requestId;
    private long timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}

// 分页响应
public class PageData<T> {
    private List<T> records;
    private long total;
    private int page;
    private int size;
    private int pages;
}

// Controller 中使用
@GetMapping("/users")
public ApiResponse<PageData<UserListResp>> listUsers(UserPageReq req) {
    return ApiResponse.success(userService.listUsers(req));
}
```

### 3.5 requestId 注入

通过 Filter/ServletFilter 在请求入口生成 UUID 并写入 MDC：

```java
// 全局 Filter 中
String requestId = UUID.randomUUID().toString().replace("-", "");
MDC.put("requestId", requestId);

// ApiResponse 组装时从 MDC 读取
response.setRequestId(MDC.get("requestId"));
```

---

## 5. 错误码体系

### 4.1 错误码结构

错误码为 5 位数字，格式：`A B C D E`

```
A - 错误来源：4=客户端错误  5=服务端错误
B - 领域分类（按模块）：
    0=通用 (ufp-common)       1=认证 (zhiyu-auth)      2=授权 (zhiyu-admin)
    3=用户 (zhiyu-user)       4=订阅/支付 (zhiyu-subscription)
    5=通知 (zhiyu-notification) 7=限流/熔断 (横切)    8=系统内部 (横切)
CDE - 具体错误（001-999）
```

| 错误码段 | 模块 | 职责 |
|:---:|------|------|
| 40xxx | ufp-common | 通用错误 + 通知错误 |
| 41xxx | zhiyu-auth | 认证错误 |
| 42xxx | zhiyu-admin | 授权错误 |
| 43xxx | zhiyu-user | 用户错误 |
| 44xxx | zhiyu-subscription | 订阅/支付错误 |
| 45xxx | zhiyu-notification | 通知错误 |
| 47xxx | 横切 | 流控/熔断错误 |
| 50xxx | 横切 | 服务器错误 |

### 4.2 错误码清单

#### 通用错误 (40xxx)

| 错误码 | HTTP | 说明 |
|--------|------|------|
| 40001 | 400 | 参数校验失败 |
| 40002 | 400 | 请求体格式错误（JSON 解析失败） |
| 40003 | 400 | 不支持的 Content-Type |
| 40004 | 400 | 缺少必填参数 |
| 40021 | 400 | 不支持的文件类型 |
| 40022 | 400 | 文件大小超过限制 |
| 40401 | 404 | 资源不存在 |
| 40501 | 405 | HTTP 方法不允许 |
| 41501 | 415 | 不支持的 Media Type |

#### 通知错误 (40xxx / 45xxx)

通知模块错误由 ufp-common 定义通用码（40xxx）+ zhiyu-notification 定义业务码（45xxx）。

| 错误码 | HTTP | 说明 |
|--------|------|------|
| 40051 | 500 | 邮件模板不存在 |
| 40052 | 500 | 邮件发送失败（SMTP 错误） |
| 40053 | 500 | 短信发送失败（渠道错误） |
| 40054 | 500 | Push 通知发送失败 |
| 40055 | 429 | 通知发送频率超限（同 recipient 60s 内） |
| 45001 | 404 | 通知记录不存在 |
| 45002 | 500 | 通知渠道不可用 |

#### 认证错误 (41xxx)

| 错误码 | HTTP | 说明 |
|--------|------|------|
| 40101 | 401 | access_token 过期 |
| 40102 | 401 | access_token 无效/被篡改 |
| 40103 | 401 | refresh_token 过期 |
| 40104 | 401 | refresh_token 被重用（防盗用触发） |
| 40105 | 401 | 密码错误 |
| 40106 | 401 | 账号已被临时锁定（15 分钟内重试） |
| 40107 | 401 | 账号已被管理员禁用 |
| 40108 | 401 | 账户已注销（N 天内可恢复） |
| 40109 | 401 | 验证码错误或已过期 |
| 40110 | 401 | 验证码发送频率过高（60 秒后再试） |
| 40111 | 401 | CAPTCHA 验证未通过 |
| 40112 | 401 | TOTP 码错误 |
| 40113 | 401 | TOTP 未启用（登录要求 TOTP 但用户未设置） |
| 40114 | 401 | 短信验证码错误 |
| 40115 | 401 | 第三方登录授权失败 |
| 40116 | 401 | WebAuthn 认证失败 |
| 40117 | 401 | 密码重置 token 无效或已过期 |
| 40118 | 401 | 密码与用户名/邮箱相同 |

#### 授权错误 (42xxx)

| 错误码 | HTTP | 说明 |
|--------|------|------|
| 40301 | 403 | 无访问权限（用户 role 不匹配） |
| 40302 | 403 | scope=LIMITED，需绑定邮箱后操作 |
| 40303 | 403 | 邮箱未验证，请先验证邮箱 |
| 40304 | 403 | action_token 无效或已过期（敏感操作二次验证） |
| 40305 | 403 | 管理员权限不足（如 CS 试图修改用户状态） |

#### 用户错误 (43xxx)

| 错误码 | HTTP | 说明 |
|--------|------|------|
| 40901 | 409 | 用户名已被占用 |
| 40902 | 409 | 邮箱已被注册 |
| 40903 | 409 | 手机号已被注册 |
| 40904 | 409 | 已绑定过该认证方式 |
| 40031 | 400 | 不能解绑最后一个认证方式 |
| 40032 | 400 | 设备数量已到上限（5 台） |
| 40033 | 400 | 注销账户 30 天内可恢复，请勿重复操作 |
| 40034 | 400 | 已超过恢复期（30 天），无法恢复 |

#### 订阅/支付错误 (44xxx)

| 错误码 | HTTP | 说明 |
|--------|------|------|
| 40341 | 403 | 功能未开放（当前套餐不含此 Feature） |
| 40441 | 404 | 订单不存在 |
| 40941 | 409 | 订单已支付，请勿重复支付 |
| 40942 | 409 | 同一周期内不可重复购买同一套餐（退款后防刷） |
| 40041 | 400 | 套餐不存在或已下架 |
| 40042 | 400 | 目标套餐与当前套餐相同（升级/降级均适用） |
| 40043 | 400 | 已有正在处理的退款申请 |
| 40044 | 400 | 试用期仅可体验一次 |
| 42241 | 422 | 支付验签失败（回调签名不匹配） |
| 42242 | 422 | 票据验证失败（Apple/Google receipt 无效） |
| 42221 | 422 | OSS 文件校验失败（文件未上传成功或已过期） |
| 50341 | 503 | 支付服务暂时不可用 |

#### 配额与限流 (47xxx)

| 错误码 | HTTP | 说明 |
|--------|------|------|
| 42901 | 429 | 配额已用完（日/月配额超限） |
| 42902 | 429 | API 调用频率过高（Sentinel 限流） |
| 42903 | 429 | IP 级别限流触发 |
| 42904 | 429 | 同一账号请求过于频繁 |

#### 服务端错误 (50xxx)

| 错误码 | HTTP | 说明 |
|--------|------|------|
| 50001 | 500 | 服务器内部错误（未预期的异常） |
| 50301 | 503 | 服务暂时不可用 |
| 50302 | 503 | 数据库连接异常 |
| 50303 | 503 | Redis 连接异常 |
| 50304 | 503 | Nacos 配置不可达 |
| 50401 | 504 | 第三方服务超时（短信/支付/OSS） |

### 4.3 错误码分配规则

#### 4.3.1 子类别 (CDE) 分配

```
通用 (00xxx):
  001-020: 请求格式校验
  021-030: 文件上传
  031-040: 用户操作限制

认证 (01xxx — HTTP 401):
  001-010: Token 相关
  011-020: 验证码/CAPTCHA
  021-030: 密码/TOTP/WebAuthn

授权 (02xxx — HTTP 403):
  001-010: 功能权限
  011-020: 管理后台权限

用户 (03xxx — 混合 HTTP):
  001-010: 资源冲突 (409)
  031-040: 业务规则限制 (400)

订阅/支付 (04xxx — 混合 HTTP):
  001-010: 套餐/订单 (400)
  041-050: 重复操作 (409)
  201-210: 票据/OOS 校验 (422)
  211-220: 签名验证 (422)

资源 (05xxx):
  预留

第三方服务 (06xxx):
  预留

配额/限流 (07xxx):
  001-010: 业务配额
  011-020: 平台限流

系统内部 (08xxx):
  001-010: 内部异常
  011-020: 基础设施
```

#### 4.3.2 新增错误码流程

1. 在 `ErrorCode` 枚举中添加 `CODE_NEW(40XXX, "默认消息")`
2. 在本文档对应表格中新增一行
3. 在 API-SPEC.md 对应接口的「可能错误」中列出
4. 在 `messages_zh_CN.properties` 和 `messages_en_US.properties` 中添加翻译
5. 如涉及客户端展示特殊 UI（如跳转登录页），需通知客户端团队

### 4.4 字段级校验错误格式

当多个字段同时校验失败时，统一返回以下格式：

```json
{
  "code": 40001,
  "message": "参数校验失败",
  "data": {
    "fieldErrors": [
      {
        "field": "username",
        "code": "SIZE",
        "message": "用户名长度需在 3-32 位之间",
        "rejectedValue": "ab"
      },
      {
        "field": "password",
        "code": "PATTERN",
        "message": "密码需至少包含大写字母、小写字母、数字、特殊字符中的 3 类",
        "rejectedValue": null
      },
      {
        "field": "email",
        "code": "INVALID",
        "message": "邮箱格式不正确",
        "rejectedValue": "not-an-email"
      }
    ]
  },
  "requestId": "a1b2c3d4...",
  "timestamp": 1716019200
}
```

**字段校验错误码映射：**

| 校验注解 | fieldErrors[].code | 说明 |
|---------|-------------------|------|
| `@NotNull` | `NOT_NULL` | 必填字段缺失 |
| `@NotBlank` | `NOT_BLANK` | 字符串为空或纯空格 |
| `@Size(min=3, max=32)` | `SIZE` | 长度超出范围 |
| `@Pattern(regexp=...)` | `PATTERN` | 格式不匹配 |
| `@Email` | `INVALID` | 邮箱格式错误 |
| `@Min/@Max` | `RANGE` | 数值超出范围 |
| Type mismatch (int→string) | `TYPE` | 类型错误 |

### 4.5 环境差异化响应

| 环境 | 错误消息 | Stack Trace | 额外信息 |
|------|---------|:----------:|---------|
| `dev` | 完整详细消息 | ✅ 返回 `data.debug` | 包含 SQL、参数值 |
| `test` | 完整消息 | ✅ 返回 `data.debug` | 仅包含异常类名 |
| `staging` | 产品级消息 | ❌ | — |
| `release` | 产品级消息 | ❌ | 仅 `requestId` 供 Loki 查询 |

```yaml
# application-release.yml — 生产环境配置
server:
  error:
    include-stacktrace: never
    include-message: never
    include-exception: false
```

```java
// 全局异常处理器中的环境判断
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:dev}")
    private String profile;

    @ExceptionHandler(BizException.class)
    public ApiResponse<?> handleBizException(BizException ex, HttpServletRequest request) {
        ApiResponse<?> resp = ApiResponse.error(ex.getCode(), ex.getMessage(), request);
        if ("dev".equals(profile) || "test".equals(profile)) {
            resp.setData(Map.of("debug", Map.of(
                "exception", ex.getClass().getSimpleName(),
                "trace", Arrays.stream(ex.getStackTrace())
                    .limit(10).map(StackTraceElement::toString).toList()
            )));
        }
        return resp;
    }
}
```

### 4.6 Java 实现

```java
// 错误码枚举（ufp-common）
public enum ErrorCode {
    SUCCESS(0, "success"),
    // 通用
    BAD_REQUEST(40001, "参数校验失败"),
    NOT_FOUND(40401, "资源不存在"),
    // 认证
    TOKEN_EXPIRED(40101, "access_token 已过期"),
    TOKEN_INVALID(40102, "access_token 无效"),
    ACCOUNT_LOCKED(40106, "账号已被临时锁定"),
    ACCOUNT_DISABLED(40107, "账号已被管理员禁用"),
    ACCOUNT_DELETED(40108, "账户已注销"),
    CAPTCHA_FAILED(40111, "CAPTCHA 验证未通过"),
    // ... 按需扩展
    ;

    private final int code;
    private final String defaultMessage;

    public BizException exception() {
        return new BizException(this);
    }

    public BizException exception(String detail) {
        return new BizException(this, detail);
    }
}

// 业务异常
public class BizException extends RuntimeException {
    private final int code;
    private final String message;

    public BizException(ErrorCode errorCode) {
        this.code = errorCode.getCode();
        this.message = errorCode.getDefaultMessage();
    }

    public BizException(ErrorCode errorCode, String detail) {
        this.code = errorCode.getCode();
        this.message = errorCode.getDefaultMessage() + (detail != null ? ": " + detail : "");
    }
}

// 全局异常处理
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException e) {
        log.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(), "服务器内部错误");
    }
}
```

### 4.4 错误码使用示例

```java
// Service 层抛出业务异常
if (user.getStatus() == UserStatus.DISABLED) {
    throw ErrorCode.ACCOUNT_DISABLED.exception();
}

if (plan == null) {
    throw ErrorCode.PLAN_NOT_FOUND.exception("planKey=" + planKey);
}

// 带动态参数的错误
throw ErrorCode.QUOTA_EXCEEDED.exception("今日对话次数已用完，限额 " + limit + " 次");
```

### 4.5 常见 HTTP 状态码使用

| HTTP 状态码 | 使用场景 |
|-------------|---------|
| 200 | 成功响应（含分页） |
| 201 | 资源创建成功（如注册、创建订单） |
| 204 | 操作成功但无返回内容（如删除设备） |
| 400 | 请求参数错误 |
| 401 | 认证失败（未登录或 token 失效） |
| 403 | 已认证但权限不足 |
| 404 | 资源不存在 |
| 409 | 资源冲突（重复注册、重复支付） |
| 422 | 业务逻辑不满足（支付验签失败） |
| 429 | 请求频率超限 / 配额用尽 |
| 500 | 服务器内部错误 |

---

## 6. 数据库与缓存键名规范

### 5.1 表命名

| 规则 | 示例 |
|------|------|
| 全部小写 snake_case | `user`, `user_auth_identity` |
| 表名用单数 | `user` 非 `users`（与 Entity 类名对齐） |
| 关联表：`<主表>_<从表>` | `admin_role_permission` |
| 前缀隔离：admin 后台用 `admin_` | `admin_user`, `admin_role` |

**理由**：单数表名与 Java Entity 类名直接映射（`User` → `user`），MyBatis-Plus `@TableName` 可省略，减少配置。

### 5.2 字段命名

| 规则 | 示例 |
|------|------|
| 全部小写 snake_case | `email_verified`, `created_at` |
| 主键统一 `id`（BIGINT 自增） | `id` |
| 布尔字段：`is_xxx` / `has_xxx` | `email_verified`, `auto_renew` |
| 时间字段：`xxx_at` | `created_at`, `updated_at`, `deleted_at` |
| 外键：`xxx_id` | `user_id`, `plan_id` |
| JSON 字段：`xxx_json`（MySQL 5.7+ JSON 类型） | `detail_json`, `raw_notification` |
| 状态字段：`status` | `status`（用枚举值，非字符串） |

### 5.3 索引命名

| 索引类型 | 命名格式 | 示例 |
|----------|---------|------|
| 普通索引 | `idx_<table>_<column>` | `idx_user_email` |
| 联合索引 | `idx_<table>_<col1>_<col2>` | `idx_user_auth_identity_user_type` |
| 唯一索引 | `uk_<table>_<column>` | `uk_user_email`, `uk_user_username` |
| 主键 | `pk_<table>` | `pk_user` |

### 5.4 Flyway 迁移文件

```
src/main/resources/db/migration/
├── V1.0.0__init_schema.sql
├── V1.0.1__add_user_phone.sql
├── V1.1.0__add_admin_rbac.sql
└── V1.2.0__add_subscription_tables.sql
```

命名格式：`V<major>.<minor>.<patch>__<description>.sql`（双下划线分隔）

**迁移规则：**
- 每个版本一个文件，幂等（用 `CREATE TABLE IF NOT EXISTS` 或先 DROP 后 CREATE 需评估风险）
- 生产环境禁止使用 Flyway `clean` 和 `repair`
- 迁移文件一经应用，**不可修改**（新增迁移文件替代）

### 5.5 Redis Key 命名

采用层级化命名：`<domain>:<subdomain>:<identifier>`

| 用途 | Key 格式 | TTL | 示例 |
|------|---------|-----|------|
| Refresh Token | `refresh:<userId>:<deviceId>` | 7天 | `refresh:1001:ab12cd34` |
| JWT 黑名单 | `jwt:blacklist:<jti>` | 对齐 token 剩余有效期 | `jwt:blacklist:d4e5f6a7` |
| 验证码（邮箱） | `verify:email:<email>` | 5分钟 | `verify:email:user@example.com` |
| 验证码（短信） | `verify:sms:<phone>` | 5分钟 | `verify:sms:13812345678` |
| 登录失败计数 | `login:fail:<type>:<identifier>` | 滑动窗口 | `login:fail:password:admin` |
| IP 登录失败计数 | `login:fail:ip:<ip>` | 滑动窗口 | `login:fail:ip:192.168.1.1` |
| 支付幂等 | `pay:processed:<transactionId>` | 30天 | `pay:processed:420000123420230101` |
| 配额计数 | `quota:<userId>:<quotaKey>:<date>` | 当天结束 | `quota:1001:daily_chat:2026-05-17` |
| CAPTCHA State | `captcha:state:<state>` | 5分钟 | `captcha:state:a3b4c5` |
| Action Token | `action:<token>` | 5分钟 | `action:xy12zw34` |
| 密码重置 | `reset:pwd:<token>` | 15分钟 | `reset:pwd:abc-def-123` |
| 速率限制 | `ratelimit:<endpoint>:<identifier>:<window>` | 窗口结束 | `ratelimit:sms:138xxxx:min` |

**约束：**
- Key 全部小写，分隔符统一用 `:`
- 所有 Key 必须设置 TTL（`EXPIRE`），禁止持久化 Key
- Key 中不存储敏感明文（如完整手机号在后 4 位脱敏后的 key 中使用）

---

## 7. 日志规范

### 6.1 日志框架

使用 SLF4J + Logback（Spring Boot 默认）。所有类通过 Lombok `@Slf4j` 获取 Logger。

### 6.2 日志级别使用

| 级别 | 使用场景 | 示例 |
|------|---------|------|
| **ERROR** | 需要人工介入的异常（DB 断连、支付失败、第三方超时） | `log.error("Payment callback verification failed, orderNo={}", orderNo, e)` |
| **WARN** | 可恢复的异常、业务异常（登录失败、配额超限、token 过期） | `log.warn("Login failed: user locked, identifier={}", email)` |
| **INFO** | 关键业务节点（注册成功、订单创建、支付完成、管理员操作） | `log.info("User registered, userId={}", userId)` |
| **DEBUG** | 开发调试信息（SQL 参数、请求响应体、中间计算结果） | `log.debug("Captcha sent to email={}, code={}", email, code)` — **生产禁止输出 code 明文** |
| **TRACE** | 不推荐使用 | — |

### 6.3 traceId 透传

所有日志必须携带 traceId（即 requestId）：

```java
// 日志格式（logback-spring.xml）
// [%d{yyyy-MM-dd HH:mm:ss.SSS}] [%level] [%X{requestId}] [%thread] %logger{36} - %msg%n

// 示例输出
// [2026-05-18 10:30:12.345] [INFO] [a1b2c3d4-e5f6] [http-nio-8080-exec-1] c.z.auth.service.AuthServiceImpl - User login success, userId=1001
```

- MDC 在请求入口 Filter 中写入，响应完成后 `MDC.clear()`
- 调用第三方服务时创建 SpanId 并透传
- 异步任务（`@Async`）需手动传递 MDC 上下文（`MDC.getCopyOfContextMap()`）

### 6.4 敏感数据脱敏

```java
// 日志中禁止出现明文
log.info("Password verified for user={}", username);          // ✅ 不记录密码
log.info("SMS code sent to phone={}", maskPhone(phone));      // ✅ 脱敏：138****1234
log.info("Email sent to {}", maskEmail(email));               // ✅ 脱敏：u***@domain.com
log.info("Access token issued, jti={}", jti);                 // ✅ 只记 jti，不记完整 token
log.info("User login success, userId={}", userId);            // ✅ 不记录密码

// 脱敏工具方法（ufp-common）
public class MaskUtils {
    public static String maskPhone(String phone) {
        return phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
    }

    public static String maskEmail(String email) {
        return email.replaceAll("(.).*(@.*)", "$1***$2");
    }
}
```

### 6.5 日志记录约定

| 位置 | 记录内容 |
|------|---------|
| Controller 入口 | 请求方法、路径、关键参数（脱敏后）、来源 IP |
| Controller 出口 | 响应码、耗时 |
| Service 关键节点 | 注册/登录/支付/注销等业务动作 + 操作对象 ID |
| 调用第三方 | 调用前记录请求摘要，调用后记录响应摘要 + 耗时 |
| 异常捕获 | 始终传 `Throwable` 参数（`log.error(msg, e)`），保留完整堆栈 |

### 6.6 禁止事项

- **禁止** `System.out.println` 和 `System.err.println`
- **禁止** `e.printStackTrace()`
- **禁止** 在生产日志中输出验证码明文、完整手机号、完整邮箱
- **禁止** 字符串拼接日志（用 `{}` 占位符，避免无谓的字符串创建）

---

## 8. 测试规范（后端）

### 7.1 测试分层与工具

| 层级 | 框架 | 范围 | 启动 Spring | 外部依赖 | 速度 |
|------|------|------|:----------:|----------|:----:|
| 单元测试 | JUnit 5 + Mockito | Service / Utils / Converter | ❌ | 无 | 毫秒 |
| 集成测试 | SpringBootTest + Testcontainers | Mapper / Controller / 完整链路 | ✅ | MySQL, Redis 容器 | 秒 |
| E2E 测试 | SpringBootTest + Testcontainers | 核心业务流程 | ✅ | 全量容器 | 分钟 |

### 7.2 文件组织

```
src/test/java/com/zhiyu/
├── auth/
│   ├── service/
│   │   └── AuthServiceTest.java          # 单元测试（Mock Mapper）
│   ├── mapper/
│   │   └── UserMapperIT.java             # 集成测试（真实 DB）
│   └── controller/
│       └── AuthControllerIT.java         # 集成测试（MockMvc）
├── user/
│   └── ...
└── e2e/
    ├── RegistrationFlowE2E.java           # E2E：注册→登录→绑定
    └── SubscriptionFlowE2E.java          # E2E：购买→支付回调→订阅激活
```

**命名约定：**

| 后缀 | 类型 | Runner |
|------|------|--------|
| `XxxTest.java` | 单元测试 | `maven-surefire-plugin`（默认） |
| `XxxIT.java` | 集成测试 | `maven-failsafe-plugin`（`*IT.java`） |
| `XxxE2E.java` | E2E 测试 | `maven-failsafe-plugin` |

### 7.3 单元测试示例

```java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void registerShouldCreateUserWhenValidInput() {
        // Arrange
        RegisterReq req = new RegisterReq();
        req.setUsername("testuser");
        req.setEmail("test@example.com");
        req.setPassword("SecureP@ss1");

        when(userMapper.selectOne(any())).thenReturn(null);  // 无冲突
        when(passwordEncoder.encode(any())).thenReturn("hashed_password");

        // Act
        UserProfileResp result = authService.register(req);

        // Assert
        assertThat(result.getUsername()).isEqualTo("testuser");
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void registerShouldThrowWhenEmailAlreadyExists() {
        // Arrange
        RegisterReq req = new RegisterReq();
        req.setEmail("existing@example.com");

        when(userMapper.selectOne(any())).thenReturn(new User()); // 模拟已存在

        // Act & Assert
        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS.getCode());
    }
}
```

### 7.4 集成测试示例

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("zhiyu_test");

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

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerShouldReturn200WithValidInput() throws Exception {
        RegisterReq req = new RegisterReq();
        req.setUsername("newuser");
        req.setEmail("new@example.com");
        req.setPassword("SecureP@ss1");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("newuser"));
    }
}
```

### 7.5 数据库测试隔离

- 集成测试**禁止**使用生产/开发数据库，必须使用 Testcontainers
- 每个集成测试类在 `@BeforeEach` 中清理相关表（`DELETE FROM ...`），保证测试间隔离
- E2E 测试按场景准备数据（`@Sql` 注解加载 fixtures）

### 7.6 覆盖率要求

| 模块 | 最低行覆盖率 | 最低分支覆盖率 |
|------|:----------:|:------------:|
| Service 层 | 85% | 80% |
| Mapper 自定义方法 | 80% | — |
| Utils / Converter | 90% | 85% |
| Controller | 70%（集成测试覆盖） | — |
| Entity / DTO / Enum | 豁免 | — |

全局目标 ≥ 80% 行覆盖率，CI 中 JaCoCo 报告不达标则构建失败。

### 7.7 TDD 工作流

```
1. 写测试（RED）         → 先写测试方法，定义期望行为
2. 确认测试失败          → ./mvnw test -pl <module> -Dtest="XxxTest"
3. 写最小实现（GREEN）    → 只写够让测试通过的代码
4. 确认测试通过          → ./mvnw test -pl <module>
5. 重构（IMPROVE）       → 消除重复、改善命名、提取方法
6. 跑全量测试确认无回归   → ./mvnw test
```

---

## 9. 测试规范（前端）

> **注意：** 前端项目 `admin-web` 尚未创建。以下测试规范为前端开发时的目标约定。

### 8.1 测试分层与工具

| 层级 | 框架 | 范围 | 速度 |
|------|------|------|:----:|
| 单元测试 | Vitest + React Testing Library | 组件 / hooks / utils | 毫秒 |
| 集成测试 | Vitest + MSW (Mock Service Worker) | 页面 + API 交互 | 秒 |
| E2E 测试 | Playwright | 关键管理后台路径 | 分钟 |

### 8.2 文件组织

```
src/
├── components/
│   └── PermissionGate/
│       ├── index.tsx
│       └── index.test.tsx           # 组件单元测试
├── hooks/
│   ├── useAuth.ts
│   └── useAuth.test.ts              # Hook 测试
├── utils/
│   ├── format.ts
│   └── format.test.ts
├── api/
│   ├── userApi.ts
│   └── userApi.test.ts              # API 层集成测试（MSW）
└── __e2e__/
    ├── login.e2e.ts                 # 登录流程 E2E
    ├── user-management.e2e.ts       # 用户管理 E2E
    └── fixtures/                     # 测试数据
```

### 8.3 单元测试示例

```typescript
// PermissionGate.test.tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { PermissionGate } from './index';

describe('PermissionGate', () => {
  it('renders children when user has required permission', () => {
    vi.mock('@/stores/useAuthStore', () => ({
      useAuthStore: () => ({ permissions: ['users.view-identity'] }),
    }));

    render(
      <PermissionGate permission="users.view-identity">
        <button>查看身份</button>
      </PermissionGate>
    );

    expect(screen.getByText('查看身份')).toBeInTheDocument();
  });

  it('renders nothing when user lacks permission', () => {
    vi.mock('@/stores/useAuthStore', () => ({
      useAuthStore: () => ({ permissions: ['dashboard'] }),
    }));

    const { container } = render(
      <PermissionGate permission="users.view-identity">
        <button>查看身份</button>
      </PermissionGate>
    );

    expect(container).toBeEmptyDOMElement();
  });
});
```

### 8.4 MSW 集成测试示例

```typescript
// userApi.test.ts
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { fetchUsers } from './userApi';

const server = setupServer(
  http.get('/api/v1/admin/users', () => {
    return HttpResponse.json({
      code: 0,
      message: 'success',
      data: { records: [{ id: 1, username: 'testuser' }], total: 1, page: 1, size: 20, pages: 1 },
    });
  })
);

beforeAll(() => server.listen());
afterAll(() => server.close());

describe('userApi', () => {
  it('returns paginated user list on success', async () => {
    const result = await fetchUsers({ page: 1, size: 20 });
    expect(result.data.records).toHaveLength(1);
    expect(result.data.records[0].username).toBe('testuser');
  });

  it('throws on error response', async () => {
    server.use(
      http.get('/api/v1/admin/users', () => {
        return HttpResponse.json({ code: 40101, message: 'token 过期' }, { status: 401 });
      })
    );
    await expect(fetchUsers({ page: 1, size: 20 })).rejects.toThrow();
  });
});
```

### 8.5 Playwright E2E 示例

```typescript
// login.e2e.ts
import { test, expect } from '@playwright/test';

test.describe('Admin Login', () => {
  test('admin can login with password and see dashboard', async ({ page }) => {
    await page.goto('/admin/login');
    await page.fill('[data-testid="username-input"]', 'admin');
    await page.fill('[data-testid="password-input"]', 'SecureP@ss1');
    await page.click('[data-testid="login-button"]');

    await expect(page).toHaveURL('/admin/dashboard');
    await expect(page.locator('[data-testid="welcome-message"]')).toContainText('admin');
  });

  test('shows error message with wrong password', async ({ page }) => {
    await page.goto('/admin/login');
    await page.fill('[data-testid="username-input"]', 'admin');
    await page.fill('[data-testid="password-input"]', 'WrongPassword');
    await page.click('[data-testid="login-button"]');

    await expect(page.locator('[data-testid="error-message"]')).toBeVisible();
    await expect(page).toHaveURL('/admin/login');
  });
});
```

### 8.6 E2E 覆盖的核心路径

| 测试场景 | 步骤 |
|----------|------|
| 后台登录 | 登录 → Dashboard → 退出 |
| 用户管理 | 登录 → 用户列表 → 搜索 → 查看详情 → 禁用用户 |
| 退款审核 | 登录 → 退款列表 → 查看详情 → 审批通过 |
| 配置管理 | 登录 → 配置列表 → 编辑 → 确认变更 |
| 会话超时 | 登录 → 等待超时 → 确认退出 → 重新登录 |

### 8.7 测试配置

```typescript
// vitest.config.ts
export default defineConfig({
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      thresholds: {
        lines: 80,
        branches: 75,
        functions: 80,
        statements: 80,
      },
    },
  },
});
```

---

## 10. 前端代码规范（admin-web）

### 9.1 TypeScript 规范

- 严格模式 (`strict: true`)
- 禁止 `any` 类型（除非有充分理由 + `// eslint-disable-next-line` 注释）
- 优先使用 `interface` 而非 `type`（对象形状）
- 枚举用 `const enum` 或 `as const` 字面量联合类型
- 非空断言 `!` 仅在有 100% 把握时使用

```typescript
// 正确：明确的类型定义
interface UserListResp {
  id: number;
  username: string;
  email: string;
  status: 'ACTIVE' | 'DISABLED' | 'DELETED';
  createdAt: string;
}

// 禁止
const data: any = response.data;   // ❌
const name = user!.profile!.name;  // ❌（链式非空断言）
```

### 9.2 组件规范

```tsx
// 每个组件一个文件，PascalCase 命名
// components/PermissionGate/index.tsx

interface PermissionGateProps {
  permission: string;
  children: React.ReactNode;
  fallback?: React.ReactNode;  // 无权限时的降级展示
}

export const PermissionGate: React.FC<PermissionGateProps> = ({
  permission,
  children,
  fallback = null,
}) => {
  const permissions = useAuthStore((s) => s.permissions);
  const hasPermission = permissions.includes(permission);

  if (!hasPermission) return <>{fallback}</>;
  return <>{children}</>;
};
```

**组件规则：**
- 每个组件一个文件，放在独立目录中（`components/Xxx/index.tsx`）
- Props 类型同文件定义，不单独导出（除非被多处引用）
- 使用 `React.FC<Props>` 类型注解
- 优先用函数式组件 + hooks，**禁止** class 组件
- 数据展示组件与容器组件分离

### 9.3 状态管理（Zustand）

```typescript
// stores/useAuthStore.ts
interface AuthState {
  user: AdminUser | null;
  permissions: string[];
  accessToken: string | null;
  refreshToken: string | null;

  login: (req: LoginReq) => Promise<void>;
  logout: () => void;
  refresh: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  permissions: [],
  accessToken: null,
  refreshToken: null,

  login: async (req) => {
    const resp = await authApi.login(req);
    set({
      user: resp.data.user,
      permissions: resp.data.permissions,
      accessToken: resp.data.accessToken,
      refreshToken: resp.data.refreshToken,
    });
  },

  logout: () => {
    authApi.logout().finally(() => {
      set({ user: null, permissions: [], accessToken: null, refreshToken: null });
    });
  },
}));
```

**Store 规则：**
- Store 文件名 `useXxxStore.ts`
- 只通过 store 提供的 action 修改状态，不直接 set
- 不要在 store 中缓存可推导的数据
- API 调用错误在 store 中处理（try/catch → toast 提示）

### 9.4 API 封装

```typescript
// api/client.ts — 统一 Axios 实例
const apiClient = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
});

// 请求拦截器：注入 token
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截器：处理 401 → 自动刷新
let isRefreshing = false;
let failedQueue: Array<{ resolve: Function; reject: Function }> = [];

apiClient.interceptors.response.use(
  (response) => {
    const { code, message } = response.data;
    if (code !== 0) {
      // 非 0 错误码统一抛出
      return Promise.reject(new ApiError(code, message));
    }
    return response.data;
  },
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        // 刷新中，排队等待
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return apiClient(originalRequest);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const newToken = await refreshAccessToken();
        // 重放队列中的请求
        failedQueue.forEach(({ resolve }) => resolve(newToken));
        failedQueue = [];
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        failedQueue.forEach(({ reject }) => reject(refreshError));
        failedQueue = [];
        useAuthStore.getState().logout();
        window.location.href = '/admin/login';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);
```

### 9.5 数据请求（React Query）

```tsx
// 列表查询用 useQuery，写操作用 useMutation
export const useUserList = (params: UserPageReq) => {
  return useQuery({
    queryKey: ['users', params],
    queryFn: () => userApi.list(params),
    staleTime: 30_000,           // 30s 内不重新请求
    placeholderData: keepPreviousData, // 翻页时保留旧数据
  });
};

export const useUpdateUserStatus = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (req: UpdateStatusReq) => userApi.updateStatus(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] }); // 刷新用户列表
      message.success('状态更新成功');
    },
    onError: (error: ApiError) => {
      message.error(error.message);
    },
  });
};
```

### 9.6 路由与权限

```tsx
// router/index.tsx
interface RouteConfig {
  path: string;
  element: React.ReactNode;
  permission?: string;           // 需要的权限，无此字段则公开访问
  children?: RouteConfig[];
}

const routes: RouteConfig[] = [
  { path: '/admin/login', element: <LoginPage /> },
  { path: '/admin/dashboard', element: <DashboardPage />, permission: 'dashboard' },
  { path: '/admin/users', element: <UserListPage />, permission: 'users' },
  { path: '/admin/admins', element: <AdminManagementPage />, permission: 'admins' },
  // ...
];

// 路由守卫：无权限时重定向到 403 页
function ProtectedRoute({ route }: { route: RouteConfig }) {
  const permissions = useAuthStore((s) => s.permissions);

  if (!route.permission) return <>{route.element}</>;
  if (permissions.includes(route.permission)) return <>{route.element}</>;

  return <Navigate to="/admin/403" replace />;
}
```

### 9.7 eslint 配置（基线）

```json
{
  "extends": [
    "react-app",
    "plugin:@typescript-eslint/recommended",
    "plugin:react-hooks/recommended"
  ],
  "rules": {
    "@typescript-eslint/no-explicit-any": "error",
    "@typescript-eslint/no-unused-vars": ["error", { "argsIgnorePattern": "^_" }],
    "no-console": "warn",
    "react-hooks/exhaustive-deps": "warn"
  }
}
```

### 9.8 组件中 data-testid 约定

所有交互元素（按钮、输入框、链接）必须添加 `data-testid` 属性用于 E2E 测试定位：

```tsx
// 格式：<语义>-<动作>
<input data-testid="username-input" />
<button data-testid="login-button">登录</button>
<button data-testid="user-row-{id}-disable">禁用</button>
```

### 9.9 Prettier 格式化

```json
// .prettierrc
{
  "semi": true,
  "singleQuote": true,
  "tabWidth": 2,
  "trailingComma": "all",
  "printWidth": 100,
  "bracketSpacing": true,
  "arrowParens": "always",
  "endOfLine": "lf"
}
```

```json
// .prettierignore
dist/
node_modules/
coverage/
*.svg
*.html
```

**规则：**
- Prettier 负责格式化，ESLint 负责代码质量，各司其职
- 使用 `eslint-config-prettier` 关闭 ESLint 中与 Prettier 冲突的规则
- CI 中 `prettier --check` 检查格式，不自动修复
- 推荐 VSCode 安装 Prettier 插件 + `"editor.formatOnSave": true`

### 9.10 CSS / 样式方案

项目使用 **Ant Design 5 主题 Token** + **CSS Modules** 作为样式方案：

```typescript
// Ant Design 主题定制
import { ConfigProvider, theme } from 'antd';

const appTheme = {
  token: {
    colorPrimary: '#1677FF',
    borderRadius: 6,
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
  },
};

<ConfigProvider theme={appTheme}>
  <App />
</ConfigProvider>
```

| 场景 | 方案 | 说明 |
|------|------|------|
| 组件样式 | Ant Design Token | 全局主题统一，不写独立 CSS |
| 页面布局 | CSS Modules (`*.module.css`) | 页面级布局样式，作用域隔离，不污染全局 |
| 全局样式 | `src/styles/global.css` | reset、通用工具类（`.flex-center` 等）、scrollbar 美化 |
| 行内样式 | 仅动态计算值 | `style={{ width: progress + '%' }}`，避免静态写死 |
| Tailwind | **不使用** | 避免与 Ant Design 冲突，保持依赖精简 |

**禁止事项：**
- ❌ 禁止全局 CSS 污染（不写 `index.css` 中裸选择器）
- ❌ 禁止 `!important`（除非覆盖第三方库样式）
- ❌ 禁止 `styled-components`（额外依赖，与 Ant Design Token 方案冗余）

### 9.11 Import 排序

使用 `eslint-plugin-simple-import-sort` 统一 import 顺序：

```typescript
// 正确顺序（自动修复）
// 1. 第三方库 (React, antd, axios...)
import { useState } from 'react';
import { Button, Table, message } from 'antd';
import axios from 'axios';

// 2. 内部模块 (api, components, hooks, stores, utils...)
import { userApi } from '@/api/userApi';
import { PermissionGate } from '@/components/PermissionGate';
import { useAuthStore } from '@/stores/useAuthStore';

// 3. 相对路径 (./ 同级, ../ 上级)
import { formatDate } from './utils';
import type { UserListResp } from '../types';

// 4. 样式 (CSS Modules)
import styles from './UserList.module.css';
```

```json
// .eslintrc 增加规则
{
  "plugins": ["simple-import-sort"],
  "rules": {
    "simple-import-sort/imports": "error",
    "simple-import-sort/exports": "error"
  }
}
```

### 9.12 组件目录结构约定

每个组件使用独立目录，co-locate 相关文件：

```
components/
└── UserList/
    ├── index.tsx              # 组件入口（默认导出）
    ├── UserList.module.css    # 样式（CSS Modules）
    ├── UserList.test.tsx      # 单元测试
    ├── UserList.stories.tsx   # Storybook（可选）
    ├── types.ts               # 组件专用类型（若仅本组件使用）
    └── hooks/                 # 组件专用 hooks（若 >1 个）
        └── useUserFilter.ts
```

**规则：**
- 组件入口文件固定为 `index.tsx`，`import UserList from '@/components/UserList'` 无需写 `/index`
- 测试文件与组件同目录（`*.test.tsx`），不放在顶层 `__tests__/` 中
- 跨组件共享的类型提升到 `src/types/`
- 跨组件共享的 hooks 放在 `src/hooks/`

### 9.13 Error Boundary 规范

在以下层级放置 Error Boundary，防止单点错误导致整页白屏：

```
App
├── GlobalErrorBoundary          ← 兜底（捕获未预期的致命错误）
│   ├── Layout
│   │   ├── Sidebar
│   │   └── ContentArea
│   │       ├── PageErrorBoundary ← 页面级（每个路由页面独立边界）
│   │       │   └── <PageComponent />
│   │       └── PageErrorBoundary
│   └── Footer
```

```tsx
// components/ErrorBoundary/index.tsx
import { Button, Result } from 'antd';
import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error?: Error;
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('ErrorBoundary caught:', error, info.componentStack);
    // 上报到 Sentry 等服务
  }

  render() {
    if (this.state.hasError) {
      return (
        this.props.fallback || (
          <Result
            status="error"
            title="页面出错了"
            subTitle="请尝试刷新页面，或联系管理员"
            extra={
              <Button type="primary" onClick={() => window.location.reload()}>
                刷新页面
              </Button>
            }
          />
        )
      );
    }
    return this.props.children;
  }
}
```

**使用规则：**
- 每个路由页面包裹一个 `<PageErrorBoundary>`（仅当前页面崩溃，其他页面正常）
- 关键数据区域（图表、表格）可选包裹独立 ErrorBoundary（区域降级而非整页崩溃）
- **禁止**在 ErrorBoundary 的 render 中再次抛错

### 9.14 自定义 Hooks 约定

**何时提取为 Hook：**

| 条件 | 示例 |
|------|------|
| 同一逻辑在 ≥2 个组件中出现 | `usePagination` — 分页状态管理 |
| 包含副作用且有明确输入/输出 | `useDebounce(value, delay)` — 防抖 |
| 封装浏览器 API | `useLocalStorage`, `useMediaQuery` |
| 从 Zustand store 的 selector 封装 | `useCurrentUser()` |

**命名与组织：**

```typescript
// 以 use 前缀开头（全局规则）
export function useDebounce<T>(value: T, delay: number): T { ... }
export function useUserList(params: UserPageReq) { ... }

// 文件组织
src/hooks/
├── useDebounce.ts         # 通用工具 hook
├── useLocalStorage.ts
├── usePagination.ts
├── useUserList.ts         # 业务 hook (封装 React Query)
├── useSubscriptions.ts
└── index.ts               # barrel export
```

**Hook 编写规范：**
- 返回类型明确声明（便于调用方理解）
- 不要在 Hook 内部使用条件判断调用其他 Hook（违反 React Hooks 规则）
- 清理副作用（useEffect 返回 cleanup 函数）

### 9.15 静态检查工具对照

| 检查对象 | 工具 | 说明 |
|----------|------|------|
| Java 代码 | **Checkstyle** + SpotBugs + PMD (p3c) | Checkstyle 仅检查 Java，不关心前端代码 |
| TypeScript/JSX | **ESLint** + Prettier | 前端代码风格和质量 |
| CSS | **Stylelint** (可选) | CSS/SCSS 语法检查 |
| Shell 脚本 | **ShellCheck** | `.sh` 文件语法和陷阱检查 |
| Markdown | **markdownlint** (可选) | 文档格式统一 |

> **Checkstyle 不会检查前端代码**。Checkstyle 是 Java 代码风格检查工具，只扫描 `*.java` 文件。TypeScript/JSX 的对应检查由 ESLint 承担。

---

## 11. CI 检查清单

每个 PR 必须通过以下 CI 检查后才能合并：

| 检查项 | 后端 | 前端 |
|--------|:----:|:----:|
| 编译 | `./mvnw compile` | `npm run build` |
| 单元测试 | `./mvnw test` | `npm run test` |
| 集成测试 | `./mvnw verify` | `npm run test:integration` |
| 覆盖率 | JaCoCo ≥ 80% | Vitest coverage ≥ 80% |
| Java 代码风格 | Checkstyle + PMD (p3c) | — |
| JS/TS 代码风格 | — | ESLint + Prettier |
| 静态分析 | SpotBugs | — |
| Shell 脚本 | ShellCheck (`shellcheck deploy/**/*.sh`) | — |
| E2E | 核心路径 (Registration + Subscription) | 后台核心路径 (5 个场景) |
| 安全扫描 | OWASP Dependency Check | `npm audit --audit-level=high` |

---

> **引用规范**: 本文档遵循全局规则（用户级 CLAUDE.md 配置中的 coding-style 和 testing 规范），所有 Java 通用约定以全局规则为准，本文档仅补充 ZhiYu 项目特有的 Spring Boot / MyBatis-Plus / React 规范。

---

## 12. API 版本管理

### 11.1 版本策略

采用 **URL 路径版本**，格式：`/api/v{major}/...`

| 策略 | 说明 |
|------|------|
| 当前版本 | `/api/v1/` (所有新功能首选) |
| 向后兼容 | 旧版本保留 2 个大版本周期 |
| 废弃通知 | 响应头 `Sunset: Sat, 31 Dec 2026 23:59:59 GMT` + `Deprecation: true` |
| 文档 | 每个版本独立 OpenAPI 3.0 文档 (`api-spec-v1.yaml`, `api-spec-v2.yaml`) |

### 11.2 版本生命周期

```
v1 (当前) ──▶ v2 (GA 发布) ──▶ v1 标记废弃 ──▶ v1 移除
             │                  │               │
             T+0               T+6mo           T+12mo
```

### 11.3 兼容性规则

| 变更类型 | 是否需要新版本 | 示例 |
|---------|:------------:|------|
| 新增可选字段 (Request) | ❌ | 添加 `nickname?` |
| 新增响应字段 | ❌ | 添加 `avatarUrl` |
| 新增端点 | ❌ | 新增 `GET /user/profile` |
| 移除字段 | ✅ | 移除 `username` |
| 修改字段类型 | ✅ | `age: int` → `age: string` |
| 修改字段语义 | ✅ | `status: 0/1` → `status: "active"/"inactive"` |
| 修改错误码含义 | ✅ | 40001 从"缺少参数"改为"参数非法" |

### 11.4 版本路由实现

```java
// WebMvc 配置
@Configuration
public class ApiVersionConfig {
    // v1 路由映射
    @Bean
    public RouterFunction<ServerResponse> v1Routes() { ... }
    
    // 废弃版本拦截器
    @Bean
    public HandlerInterceptor deprecationInterceptor() {
        return new DeprecationInterceptor("v0", "2026-12-31");
    }
}
```

---

## 13. 国际化 (i18n) 策略

### 12.1 支持范围

| 层级 | i18n 支持 | 说明 |
|------|:--------:|------|
| 客户端 App | ✅ | iOS/Android 本地化字符串 |
| Admin 前端 | ✅ | Ant Design 内置 i18n (zh-CN/en-US) |
| API 错误消息 | ✅ | 响应根据 `Accept-Language` 头返回对应语言 |
| 数据库内容 | ❌ | 套餐名/权限名仅存中文（管理后台使用） |
| 邮件/短信 | ✅ | 模板按语言选择 |
| 日志 | ❌ | 始终英文（运维可读性） |

### 12.2 语言优先级

1. 用户设置 (`user_profile.locale`)
2. 请求头 `Accept-Language`
3. 默认 `zh-CN`

### 12.3 后端实现

```java
// resources/i18n/messages_zh_CN.properties
error.40001=请求参数校验失败: {0}
error.40101=认证失败，请重新登录
error.40901=用户名已存在

// resources/i18n/messages_en_US.properties
error.40001=Request parameter validation failed: {0}
error.40101=Authentication failed, please log in again
error.40901=Username already exists

// MessageSource 配置
@Bean
public MessageSource messageSource() {
    ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
    ms.setBasename("classpath:i18n/messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setCacheSeconds(3600);
    return ms;
}

// 使用
@Autowired
private MessageSource messageSource;

String msg = messageSource.getMessage("error.40901", null, locale);
```

### 12.4 前端 i18n

```typescript
// Admin: react-intl 或 antd ConfigProvider
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';

// 切换
<ConfigProvider locale={locale === 'en' ? enUS : zhCN}>
  <App />
</ConfigProvider>

// iOS: Localizable.strings / Android: strings.xml
// "login_title" = "欢迎回来" / "Welcome Back"
```

---

## 14. 前后端联调方案

> 前端详细设计见 [FRONTEND-DESIGN.md](../product-design/FRONTEND-DESIGN.md)。本节仅描述后端开发者需要知道的前后端协作约定。

### 14.1 本地联调拓扑

```
┌─────────────────────────┐     ┌──────────────────────────┐
│  Vite Dev Server         │     │  Spring Boot (dev profile)│
│  http://localhost:5173   │────▶│  http://localhost:8080    │
│                          │     │                           │
│  前端页面                 │     │  → MySQL (K8s NodePort)   │
│  fetch('/api/v1/...')    │     │  → Redis (K8s NodePort)   │
│                          │     │  → Nacos (K8s NodePort)   │
└─────────────────────────┘     └──────────────────────────┘
```

前端的 Vite dev server 将 `/api` 前缀的请求代理到后端 Spring Boot。因为同源代理，不触发浏览器 CORS 检查。

### 14.2 后端 CORS 配置

本地开发虽然走 Vite proxy 不跨域，但生产环境前后端分离部署（不同域名/端口），需要后端配置 CORS：

```java
// ufp-common/src/main/java/com/zhiyu/common/config/CorsConfig.java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${zhiyu.security.cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns(allowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

```yaml
# application-dev.yml
zhiyu:
  security:
    cors:
      allowed-origins: http://localhost:5173

# application-release.yml
zhiyu:
  security:
    cors:
      allowed-origins: ${CORS_ALLOWED_ORIGINS:https://admin.zhiyu.app}
```

### 14.3 前后端协作约定

| 约定 | 说明 |
|------|------|
| API 规范 | 以 [API-SPEC.md](../product-design/API-SPEC.md) 为唯一权威，后端实现和前端调用必须一致 |
| 字段命名 | JSON 使用 camelCase，与 Java 字段名不一致时用 `@JsonProperty` 映射 |
| 日期格式 | 所有日期时间返回 ISO 8601 字符串 (`yyyy-MM-ddTHH:mm:ss`)，不返回时间戳数字 |
| 空值处理 | `null` 字段不序列化（Jackson `NON_NULL`），前端需做好 `undefined` 防御 |
| 分页 | 统一使用 `page`(从1开始) + `size`，响应含 `total`/`pages` |
| 错误处理 | 前端检查 `response.data.code !== 0` 而非 HTTP 状态码。HTTP 200 + code≠0 也是错误 |
| 文件上传 | `multipart/form-data`，后端限制 10MB (`spring.servlet.multipart.max-file-size`) |
| WebSocket | 如使用 STOMP over WebSocket，端点 `/ws`，前端通过 `SockJS` 连接 |

### 14.4 开发阶段 Mock 策略

| 阶段 | 后端状态 | 前端如何处理 |
|------|---------|------------|
| API 已实现 | ✅ Controller 就绪 | 直连后端，MSW 关闭 |
| API 规格已定，代码未写 | 📋 API-SPEC 已定 | MSW mock 返回符合 API-SPEC 的数据 |
| API 规格未定 | ❓ 还在设计 | 先确定 API 契约再开发 |

**MSW mock 的前提**：API-SPEC.md 中对应的请求/响应格式已确定。Mock 数据通过 `VITE_ENABLE_MOCK=true` 环境变量控制开关（详见 [FRONTEND-DESIGN.md §7.2](../product-design/FRONTEND-DESIGN.md#72-无后端时的前端独立开发msw-mock)）。

---

## 15. API 类型共享

### 15.1 生成流水线

```
API-SPEC.md (权威源)
     │
     ▼ (手动维护)
openapi.yaml (OpenAPI 3.0 规范)
     │
     ├──▶ openapi-typescript → frontend/src/api/schema.d.ts (前端类型)
     │
     └──▶ openapi-generator → (可选) 生成 Java DTO 用于合约测试
```

### 15.2 Java 端约束

后端 Controller 的返回类型必须与 OpenAPI 定义一致：

```java
// 正确: Resp DTO 字段名与 OpenAPI schema 对齐
@GetMapping("/users")
public ApiResponse<PageData<UserListResp>> listUsers(UserPageReq req) {
    // UserListResp 中的字段名(camelCase) 必须与 openapi.yaml 中的 schema 一致
    return ApiResponse.success(userService.listUsers(req));
}
```

### 15.3 类型漂移预防

| 检查点 | 工具 | 说明 |
|--------|------|------|
| CI 静态检查 | `npm run typecheck` | TypeScript 编译检查，引用不存在的字段会报错 |
| 合约测试 | Spring Cloud Contract 或手写断言 | 验证后端响应结构符合 schema |
| PR Review | 人工 | 修改 API 时必须同时更新 API-SPEC.md 和 openapi.yaml |

### 15.4 错误码同步

前后端共享错误码常量。后端以 `ErrorCode.java` 枚举为权威源，前端手动维护对应的 TypeScript 常量：

```java
// Java (权威源)
public enum ErrorCode {
    TOKEN_EXPIRED(40101, "access_token 已过期"),
    ACCOUNT_LOCKED(40106, "账号已被临时锁定"),
    // ...
}
```

```typescript
// TypeScript (手动同步，新增错误码时同步更新)
export const ErrorCodes = {
  TOKEN_EXPIRED: 40101,
  ACCOUNT_LOCKED: 40106,
} as const;
```

> 错误码变更时，PR 必须同时修改两个文件。CI 中可通过脚本比对 `ErrorCode.java` 和 `errorCodes.ts` 校验是否遗漏。详见 [FRONTEND-DESIGN.md §8](../product-design/FRONTEND-DESIGN.md#8-api-类型生成与共享)。
