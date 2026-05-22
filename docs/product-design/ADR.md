# ZhiYu-Backend 架构决策记录 (ADR)

> 本文档记录 ZhiYu-Backend 项目的所有架构决策，每条 ADR 包含背景、决策、理由、后果和已考虑的替代方案。
> 共 14 条决策，从 ARCHITECTURE.md 提取独立维护。

## 1. 决策总览

| # | 模块分类 | 业务分类 | 议题 | 决策结论 | 决策状态 | 日期 | 详情 |
|:--:|:--:|:--:|------|------|:--:|:----:|:----:|
| ADR-001 | 构建与工程 | 横切关注点 | 单模块 vs 多 Maven 模块 | 多 Maven 模块，编译期强制依赖方向 | 已决策 | 2025-04 | [§2.1](#adr-001) |
| ADR-002 | 认证与安全 | 认证安全 | JWT RS256 vs HS256 | RS256 非对称签名，公钥可分发 | 已决策 | 2025-04 | [§2.2](#adr-002) |
| ADR-003 | 认证与安全 | 认证安全 | Redis 黑名单 + RT 轮换 vs 纯 Session | Redis 黑名单 + Refresh Token 轮换 | 已决策 | 2025-04 | [§2.3](#adr-003) |
| ADR-004 | 数据与持久化 | 数据层 | Testcontainers vs H2 | Testcontainers 真实 MySQL 8.0 + Redis 7 | 已决策 | 2025-04 | [§2.4](#adr-004) |
| ADR-005 | 构建与工程 | 横切关注点 | Maven vs Gradle | Maven，团队熟悉 + Spring Boot 支持成熟 | 已决策 | 2025-04 | [§2.5](#adr-005) |
| ADR-006 | 构建与工程 | 横切关注点 | 构造器注入 vs 字段注入 | 强制构造器注入 + `@RequiredArgsConstructor` | 已决策 | 2025-04 | [§2.6](#adr-006) |
| ADR-007 | 数据与持久化 | 数据层 | MyBatis-Plus vs JPA/Hibernate | MyBatis-Plus，SQL 可控性优先 | 已决策 | 2025-04 | [§2.7](#adr-007) |
| ADR-008 | 构建与工程 | 横切关注点 | Flyway vs Liquibase | Flyway，SQL-first 方式 | 已决策 | 2025-04 | [§2.8](#adr-008) |
| ADR-009 | 流控与韧性 | 横切关注点 | Sentinel vs Guava RateLimiter | Sentinel，Spring Cloud Alibaba 原生集成 | 已决策 | 2025-04 | [§2.9](#adr-009) |
| ADR-010 | 事务与一致性 | 支付事务 | Outbox+补偿 vs Seata/Saga | Outbox + 补偿，不引入分布式事务框架 | 已决策 | 2025-05 | [§2.10](#adr-010) |
| ADR-011 | 认证与安全 | 认证安全 | 纯 JWT Filter vs SecurityFilterChain | 纯 JWT Filter（OncePerRequestFilter） | 已决策 | 2025-05 | [§2.11](#adr-011) |
| ADR-012 | 认证与安全 | 认证安全 | CAPTCHA 验证码环境降级策略 | Profile 控制 skip，release 物理不可跳过 | 已决策 | 2025-05 | [§2.12](#adr-012) |
| ADR-013 | 认证与安全 | 认证安全 | CORS 跨域策略 | 不配置全局 CORS，同源部署 + 按需放行 | 已决策 | 2025-05 | [§2.13](#adr-013) |
| ADR-014 | 数据与持久化 | 可观测性 | 应用日志存储方案 | MySQL `app_log` 单表 + 30 天 TTL 定时清理 | 已决策 | 2026-05 | [§2.14](#adr-014) |

---

## 2. 决策详情

### 2.1 ADR-001: 单模块领域隔离 vs 多 Maven 模块

**状态：** 已决策

**背景：** 需要组织认证、用户、订阅、管理后台、通知五个领域的代码。可选方案：(A) 单 Maven 模块 + 包隔离，(B) 多 Maven 模块（当前 9 模块：ufp-common / ufp-auth / zhiyu-common / zhiyu-auth / zhiyu-user / zhiyu-notification / zhiyu-subscription / zhiyu-admin / zhiyu-server）。

**决策：** 选择方案 B — 多 Maven 模块。

**理由：**
- 编译期强制依赖方向（上层 → 下层，不可逆），防止循环依赖
- 可独立构建和测试每个模块，缩短 CI 时间
- 日后拆分微服务时，模块可平滑升级为独立服务
- 公共代码（ufp-common）被所有模块共享，版本统一管理

**后果：**
- 模块间接口需明确定义（Service 接口在各自模块中，仅通过注入调用）
- 初期多 8 个 `pom.xml` 需要维护
- 横切关注点（如安全 Filter）放在 ufp-common 模块

**替代方案（已拒绝）：**
- 方案 A 过于灵活，团队扩张后容易产生循环依赖
- 直接多微服务：初期不需要分布式复杂度

---

### 2.2 ADR-002: JWT RS256 非对称签名 vs HS256 对称签名

**状态：** 已决策

**背景：** 需要签发和验证 JWT access_token。可选方案：(A) HS256 共享密钥，(B) RS256 RSA 公私钥对。

**决策：** 选择方案 B — RS256 非对称签名。

**理由：**
- 私钥仅认证服务持有，公钥可分发——若日后拆微服务，其他服务仅需公钥即可验签
- 泄露公钥不导致 token 可被伪造（与 HS256 泄露密钥 = 全破坏不同）
- jjwt 库对 RS256 的支持成熟

**后果：**
- 密钥管理：私钥通过 K8s Secret 注入为环境变量 `JWT_PRIVATE_KEY`
- 比 HMAC 略慢（但可忽略，RSA 签名微秒级）
- 公钥存储在 Nacos 共享配置中

**替代方案（已拒绝）：**
- HS256 在单模块场景下可行但无扩展空间，切换成本高

---

### 2.3 ADR-003: Redis 黑名单 + Refresh Token 轮换 vs 纯有状态 Session

**状态：** 已决策

**背景：** access_token 无状态，退出登录时需要失效机制。可选方案：(A) Redis 黑名单（jti），(B) 纯 Redis Session（access_token 仅存标识符）。

**决策：** 选择方案 A — Redis 黑名单 + Refresh Token 轮换。

**理由：**
- access_token 短期（15min），无状态直接验签，不查 Redis（低延迟）
- 仅退出时 jti 进黑名单（key 过期时间对齐剩余有效期，自动清理）
- refresh_token 轮换机制防止长期 token 被盗用

**后果：**
- 正常请求不依赖 Redis（高可用性）
- Redis 不可用时黑名单降级为内存 LRU 缓存（接受少量失效延迟）
- 需维护黑名单 TTL 对齐 access_token 剩余时间

**替代方案（已拒绝）：**
- 纯 Session：每次请求都查 Redis，增加延迟和 Redis 依赖
- 纯无状态（无可注销）：不支持即时退出

---

### 2.4 ADR-004: Testcontainers vs H2 内存数据库

**状态：** 已决策

**背景：** 集成测试需要数据库和 Redis。可选方案：(A) H2 内存数据库 + 内嵌 Redis，(B) Testcontainers Docker 容器。

**决策：** 选择方案 B — Testcontainers。

**理由：**
- H2 与 MySQL 行为差异多（SQL 方言、索引策略、JSON 类型），曾导致生产问题
- Testcontainers 使用真实 MySQL 8.0 + Redis 7 镜像，测试环境与生产一致
- Docker 已成开发标配，CI 环境原生支持

**后果：**
- 集成测试启动慢 5-15 秒（容器拉取镜像）
- CI 需 Docker 环境
- 首次拉取镜像需网络

**替代方案（已拒绝）：**
- H2 适合纯单元测试场景，但本项目的 MyBatis XML 和 JSON 类型依赖 MySQL

---

### 2.5 ADR-005: Maven vs Gradle

**状态：** 已决策

**背景：** 需要选择 Java 构建工具。

**决策：** 选择 Maven。

**理由：**
- 团队更熟悉 Maven 的 POM 模式和插件生态
- Spring Boot 对 Maven 的支持最成熟
- 多模块项目 Maven 的 `<dependencyManagement>` 模式清晰

**后果：**
- XML 冗长但可预测
- 构建速度略慢于 Gradle（但增量编译可缓解）

---

### 2.6 ADR-006: 构造器注入 vs 字段注入

**状态：** 已决策

**背景：** Spring DI 有三种方式：字段 `@Autowired`、Setter、构造器。

**决策：** 强制构造器注入，配合 Lombok `@RequiredArgsConstructor`。

**理由：**
- 字段注入导致依赖隐藏（一个类注入 10 个字段时无人察觉）
- 构造器注入在编译期保证依赖不缺失
- 方便单元测试（无需 Spring 容器即可手动注入 Mock）
- `@RequiredArgsConstructor` 省去手写构造器

**后果：**
- 类注入超过 5 个时构造器过长 → 触发重构（拆分类）
- 禁止 `@Autowired` 字段注入（在 CI 中通过 Checkstyle/ArchUnit 检查）

---

### 2.7 ADR-007: MyBatis-Plus vs JPA/Hibernate

**状态：** 已决策

**背景：** 需要选择 Java ORM 框架。

**决策：** 选择 MyBatis-Plus。

**理由：**
- 团队对 MyBatis SQL 可控性更有信心，复杂查询不依赖 Hibernate 生成的 SQL
- MyBatis-Plus 提供 ActiveRecord 风格的 CRUD 开箱即用，减少重复 Mapper XML
- 手写 SQL 在分页、多表关联、JSON 字段操作等场景更透明
- 中国互联网行业 MyBatis 生态最成熟，招聘匹配度高

**后果：**
- 部分简单查询仍需手写 Mapper（不如 JPA 方法命名推导方便）
- 需额外注意 N+1 查询（JPA 的 `@EntityGraph` 不存在，需手动 JOIN）
- 乐观锁、逻辑删除、自动填充等通过 MyBatis-Plus 注解完成

**替代方案（已拒绝）：**
- JPA/Hibernate：自动 DDL 与 Flyway 冲突，复杂查询 SQL 不可控

---

### 2.8 ADR-008: Flyway vs Liquibase

**状态：** 已决策

**背景：** 需要数据库迁移工具。

**决策：** 选择 Flyway。

**理由：**
- SQL-first 方式，迁移脚本就是标准 SQL 文件，DBA 可直接审查
- Spring Boot 内建 `flyway-core` 自动配置，零代码集成
- 版本号命名清晰（`V<major>.<minor>.<patch>__<desc>.sql`），支持回滚（需 Enterprise 版或手写 undo）
- 社区版功能对本项目完全够用

**后果：**
- 不支持 XML/JSON/YAML 格式的变更定义（Liquibase 支持），但本项目不需要
- 社区版无 undo 功能，回滚依赖备份恢复
- 迁移文件必须按版本号顺序执行，不可跳过

**替代方案（已拒绝）：**
- Liquibase：学习成本更高，changeSet XML 冗长，SQL 审查不直观

---

### 2.9 ADR-009: Sentinel vs Guava RateLimiter / Bucket4j

**状态：** 已决策

**背景：** 需要接口限流和熔断降级能力。

**决策：** 选择 Sentinel。

**理由：**
- 与 Spring Cloud Alibaba 生态原生集成，配置可通过 Nacos 动态下发
- 支持 QPS/线程数/热点参数/授权等多种限流模式
- 提供 Dashboard 可视化监控，支持实时规则调整
- 内置降级策略（慢调用比例/异常比例/异常数）与 @SentinelResource 注解

**后果：**
- 引入额外 Sentinel Dashboard 部署（轻量，1C/512Mi 即可）
- 规则存储在 Nacos，需保证 Nacos 可用（降级：本地快照）
- 比 Guava RateLimiter 重但能力更强

**替代方案（已拒绝）：**
- Guava RateLimiter：无分布式协调能力，无 Dashboard，规则修改需重启
- Bucket4j：类似 Guava，无控制台

---

### 2.10 ADR-010: Outbox + 补偿 vs Seata / Saga

**状态：** 已决策

**背景：** 支付场景涉及 DB 写入和第三方 API 调用（微信/支付宝），需要保证数据一致性。

**决策：** 选择 **Outbox + 补偿** 模式，不引入 Seata 或 Saga 框架。

**理由：**

1. **当前是单体应用，不是微服务。** Seata 解决的核心问题是跨服务、各自独立 DB 的分布式事务。ZhiYu-Backend 所有业务表在同一 MySQL，`@Transactional` 已覆盖本地事务。引入 Seata 是 30-50% 性能损失换一个当前不存在的需求。

2. **真正的痛点不是跨库，是外部 API 不可回滚。** 支付渠道（微信/支付宝/Apple/Google）的预支付订单在 DB 回滚后无法撤回——Seata AT 模式的 undo_log 也解决不了外部系统的问题。这个场景只能用补偿（标记订单 CANCELLED + 15 分钟后微信侧自动过期）。

3. **支付回调的并发风险是幂等，不是事务。** 支付渠道可能重复推送回调，这不属于分布式事务范畴。三层幂等（Redis + DB 唯一约束 + 订单状态检查）足以处理。

4. **Seata 的代价超过收益。** AT 模式需要为每张写表配置 undo_log 表（20+ 张额外表）、部署 Seata Server（1-2 Pod + 独立 DB）、全局锁在高并发下退化严重。当前场景下这些代价无回报。

5. **Outbox 模式解决核心痛点：事务后异步通知。** 支付成功后发送邮件/推送不能在 `@Transactional` 内执行（违反耗时操作禁令），Outbox 通过在事务提交后写入事件表，由定时 Job 轮询发送，保证 at-least-once。

**当前策略映射：**

| 场景 | 策略 | 工具 |
|------|------|------|
| 单 DB 内多表写入 | 本地事务 | `@Transactional` |
| 调用支付渠道 API | 事务外调用 + 失败补偿 | 补偿方法 |
| 回调/通知幂等 | 三层幂等 | Redis + DB UK + 状态检查 |
| 事务后异步通知 | Outbox 模式 | `outbox_event` 表 + 定时 Job |
| 并发写冲突 | 乐观锁 | MyBatis-Plus `@Version` |

**后果：**

- 补偿逻辑需手工编写（每个外部 API 调用点配一个补偿方法），但量很小——当前仅 4 个支付渠道的下单接口需要
- Outbox 事件由定时 Job 轮询（每 5s），非实时（可接受：邮件/推送延迟 < 10s）
- 后续如果拆成微服务，需重新评估 Seata/Saga 的适用性（届时仍是单 DB，但跨服务调用需要协调）

**替代方案（已拒绝）：**

- **Seata AT 模式**：性能损失 30-50%，需 20+ undo_log 表 + Seata Server 集群，无法回滚外部 API，收益为零
- **Seata Saga 模式**：适合跨微服务的长事务编排，当前不需要；在单体中引入状态机编排框架是过度设计
- **纯手工补偿（无 Outbox）**：回调处理 + 通知发送耦合在事务内，耗时操作阻塞 DB 连接池

> 事务设计细节详见 [DEVELOPMENT-STANDARDS.md §2.4](../dev-test/DEVELOPMENT-STANDARDS.md#24-事务管理)。

---

### 2.11 ADR-011: 纯 JWT Filter vs SecurityFilterChain

**状态：** 已决策

**背景：** Spring Boot 3.x 提供了 `SecurityFilterChain`（基于 Spring Security 的完整安全链路）和手动 `OncePerRequestFilter` 两种认证方式。需要为纯 REST API 后端选择认证架构。

**决策：** 选择 **纯 JWT Filter（OncePerRequestFilter）**，不使用 `SecurityFilterChain`。

**理由：**

1. **项目是纯 REST API，无服务端渲染。** Spring Security 的 `SecurityFilterChain` 为服务端渲染场景（formLogin、session、CSRF、logout）提供了大量默认配置，这些对 REST API 全是无用噪音——需要显式禁用每一项。

2. **JWT 天然无状态，与 Session/Cookie 机制对立。** SecurityContextHolder 默认策略是 `ThreadLocal`，每个请求结束即清除——这对 JWT 完全够用。引入 `SecurityFilterChain` 不会增加任何 JWT 相关能力，只增加配置复杂度。

3. **Spring Security 的认证异常会被包装成 403 Forbidden，而我们需要自定义错误码（i18n）。** 要让 Spring Security 返回 `ApiResponse` 格式的错误，需要自定义 `AuthenticationEntryPoint` + `AccessDeniedHandler`，反而比直接 throw 自定义异常更绕。

4. **减少依赖耦合。** 仅依赖 `spring-security-crypto`（BCrypt 密码哈希），不引入 `spring-security-web` / `spring-security-config`。后续如需引入 Spring Security（如 OAuth2 Resource Server），JWT Filter 可以自然迁移为 `SecurityFilterChain` 的一个 filter 节点。

**实现要点：**

```java
// ufp-common/filter/JwtAuthenticationFilter.java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) {
        String token = extractToken(request);
        if (token != null) {
            Claims claims = jwtProvider.verifyToken(token);
            // 设置 ThreadLocal 上下文，请求结束自动清除
            SecurityContextHolder.set(claims);
        }
        chain.doFilter(request, response);
    }
}
```

**后果：**

- 无全局方法级安全注解（`@PreAuthorize` 等），权限校验在 Service 层手工编码
- 无内置 CSRF 防护（REST API 天然不需要，Token 在 Header 中自动免疫 CSRF）
- 无 Session 固定攻击防护（无 Session 可攻击）
- 后续如需引入 OAuth2/OIDC，可在 Filter 层叠加，不影响现有架构

**替代方案（已拒绝）：**

- **SecurityFilterChain + JWT**：需显式禁用 formLogin、session、CSRF、logout，配置 `AuthenticationEntryPoint` + `AccessDeniedHandler` 适配 `ApiResponse` 格式，引入 `spring-security-web` + `spring-security-config` 依赖，复杂度过高，收益为零
- **Spring Security OAuth2 Resource Server**：当前无 OAuth2 需求，JWT RS256 自主签发已足够。后续引入 OAuth2 时再评估

---

### 2.12 ADR-012: CAPTCHA 验证码环境降级策略

**状态：** 已决策

**背景：** 开发/测试环境每次注册/登录都需发送邮件验证码，严重拖慢开发效率。需要设计环境感知的验证码降级策略。

**决策：** `zhiyu.security.captcha.skip=true` 时跳过验证码校验（dev/test profile 默认 true），release profile 不配置该 key（默认 false），生产环境严禁跳过。

**理由：**

1. **环境差异化，而非全局开关。** 通过 Spring Boot profile 自动控制，开发者无需手动切换。`application-dev.yml` 设 `skip=true`，`application-release.yml` 不写该 key，确保生产不可能意外跳过。

2. **跳过 = 完全跳过校验，不接受固定 token。** 固定 token 方案（如 "000000"）看似方便，实则引入安全风险：冒烟测试可能误用固定 token 的代码路径，隐藏真正的验证码校验 bug。完全跳过意味着测试环境走的是"无验证码"代码路径，与生产路径分区清晰。

3. **集成测试可独立控制。** `application-test.yml` 同样设 `skip=true`，`@SpringBootTest` 不会触发真实的邮件发送。

**配置：**

```yaml
# application-dev.yml（dev profile）
zhiyu:
  captcha:
    skip: true   # 跳过验证码校验

# application-release.yml（release profile）
# 不配置 zhiyu.security.captcha.skip，默认 false，验证码强制校验
```

**后果：**

- 开发环境：注册/登录无需等邮件，加快开发迭代
- 冒烟测试：需单独写一个验证码校验的集成测试（用 `@SpringBootTest(properties = "zhiyu.security.captcha.skip=false")` 覆盖）
- 生产环境：物理上不可能跳过验证码（无该配置项）

**替代方案（已拒绝）：**

- **固定 token（如 "000000" 始终放行）**：污染代码路径，生产环境如果忘记移除就是安全漏洞
- **全局开关（环境变量）**：可能被运维误设，不如 profile 文件物理隔离可靠

---

### 2.13 ADR-013: CORS 跨域策略

**状态：** 已决策

**背景：** 管理后台（React SPA）和移动端（iOS/Android Native）需要访问后端 API，需确定 CORS 策略。

**决策：** **不配置全局 CORS。** 管理后台通过 Nginx 同源部署（`/api/` 反向代理），移动端 Native 不受浏览器同源策略限制。仅在特定跨域场景（如第三方回调、外部 iframe 嵌入）按需在 Controller 方法上加 `@CrossOrigin`。

**理由：**

1. **管理后台同源部署。** 前端 `admin-web` 容器内 Nginx 将 `/api/` 代理到 `zhiyu-backend:8080`，浏览器看到的请求是同源的（同 host + 同 port）。无需任何 CORS 头。

2. **移动端不受 CORS 限制。** iOS（URLSession）/Android（OkHttp）是 Native HTTP 客户端，CORS 是浏览器强制执行的安全策略，对非浏览器客户端无效。

3. **无全局 CORS 配置 = 安全的默认策略。** 不配置 `WebMvcConfigurer.addCorsMappings()` 或 `CorsFilter`，浏览器跨域请求自然被拒绝。相比全局 `allowedOrigins("*")` 的常见错误，这是最安全的选择。

4. **按需放行，最小权限。** 如果未来需要开放特定 API 给第三方前端（如 H5 支付页面、外部数据看板嵌入），在单个 Controller 上加 `@CrossOrigin(origins = "https://trusted-partner.com")`，权限受限、可审计。

**配置策略：**

| 场景 | CORS 配置 | 原因 |
|------|-----------|------|
| 管理后台 | 无需 CORS | Nginx 同源代理 |
| iOS/Android App | 无需 CORS | Native 客户端无同源限制 |
| 第三方 H5 页面 | `@CrossOrigin` 按 Controller 放行 | 限制 origin 范围 |
| 公开 API | 通过 API Gateway 层处理 | 不在业务代码中配置 |

**后果：**

- 无需在 `application-*.yml` 或 Java Config 中维护 CORS 白名单
- 如需支持跨域（如外部集成），需在具体 Controller 上显式声明，变更可追踪
- dev 环境若使用 Vite 独立前端（`localhost:5173` → `localhost:8080`），需在 Vite 配置中设置 proxy，而非在后端开 CORS

**替代方案（已拒绝）：**

- **全局 CORS `allowedOrigins("*")`**：常见安全反模式，OAuth2 隐式流程下直接违反 RFC 6454
- **Profile 条件 CORS（dev 放开，release 收紧）**：同一代码在不同环境行为不同，增加排查成本；Vite proxy 即可解决 dev 跨域，不需要后端配合
- **Spring Cloud Gateway 统一处理 CORS**：当前无 Gateway 部署，且同源架构不需要

---

### 2.14 ADR-014: 应用日志存储方案

**状态：** 已决策

**背景：** 应用运行时日志（SLF4J/Logback 输出）需要持久化存储，便于问题排查和运维审计。需确定存储方案和保留策略。

**决策：** MySQL `app_log` 单表存储，保留 30 天，定时任务清理。

**表结构：**

```sql
CREATE TABLE app_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at  DATETIME(3)  NOT NULL,
    level       VARCHAR(10)  NOT NULL,    -- INFO/WARN/ERROR/DEBUG
    logger      VARCHAR(255) NOT NULL,    -- 类全限定名
    message     MEDIUMTEXT   NOT NULL,    -- 日志消息
    stack_trace MEDIUMTEXT   NULL,        -- 异常堆栈
    thread_name VARCHAR(100) NULL,
    trace_id    CHAR(32)     NULL,        -- 请求链路追踪 (MDC)
    user_id     BIGINT       NULL,        -- 关联用户
    module      VARCHAR(50)  NULL         -- 模块: auth/user/subscription/admin/notification

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_app_log_ts    ON app_log(created_at);
CREATE INDEX idx_app_log_level ON app_log(level, created_at);
CREATE INDEX idx_app_log_trace ON app_log(trace_id);
```

**清理策略：** 定时任务每日凌晨 3:00 执行 `DELETE FROM app_log WHERE created_at < DATE_SUB(NOW(), INTERVAL 30 DAY) LIMIT 5000`，分批删除避免长事务锁表。

**Logback 集成：** 自定义 `DBAppender`，异步批量写入（`ArrayBlockingQueue` 缓冲，每 5s 或满 500 条刷盘），不阻塞业务线程。通过 MDC `traceId` 关联请求链路。

**理由：**

- 单表足够满足 30 天保留需求，无需分表增加复杂度
- 按月分表适合保留 3 个月以上的场景，当前需求不需要
- 分批删除避免 `DELETE` 大事务导致的锁等待和主从延迟
- 异步写入确保日志 I/O 不影响业务响应时间

**替代方案（已拒绝）：**

- **ELK/EFK 栈（Elasticsearch + Logstash + Kibana）**：运维成本高，单节点 K8s 资源不足
- **Loki + Grafana**：已文档化但未部署，后续可补充作为日志可视化层
- **按月分表 `app_log_YYYYMM`**：当前 30 天保留不需要分表，单表 `DELETE + LIMIT` 分批清理即可

---

## 3. 相关文档

- [ARCHITECTURE.md](ARCHITECTURE.md) — 系统架构视图与关键流程
- [ARCHITECTURE-UFP.md](ARCHITECTURE-UFP.md) — UFP 平台模块架构设计
- [PRD.md](PRD.md) — 产品需求文档
- [DEVELOPMENT-STANDARDS.md](../dev-test/DEVELOPMENT-STANDARDS.md) — 编码规范
