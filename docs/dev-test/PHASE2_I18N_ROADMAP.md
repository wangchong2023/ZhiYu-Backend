# ZhiYu-Backend Phase 2 微服务拆分与前端多语言 i18n 技术路线图

> [!IMPORTANT]
> **制定角色**：资深 Java 架构师、前端专家、测试专家  
> **制定时间**：2026-05-28 17:48  
> **规划目标**：将 Phase 2 平台微服务拆分（微服务独立化进程、分布式事务、服务治理）与前端端到端多语言 i18n 控制（硬编码扫描、Key一致性对齐、运行时持久化）细化为具备强落地执行力的任务矩阵，并结合当前代码库实际情况（Grep 检查工具现状、Outbox 雏形等）进行 100% 对齐。

---

## 🗺️ 架构路线图整体排期矩阵

| 任务领域 | 任务 ID | 任务名称 | 优先级 | 核心改进方案与重构内容 | 难度 | 估时 (人天) |
| :--- | :--- | :--- | :---: | :--- | :---: | :---: |
| **微服务拆分** | **MS-001** | 数据库物理隔离与 Flyway 独立化 | **P0** (高) | 将 `ufp_auth` 认证库与 `zhiyu` 业务库彻底解耦为独立物理库，分离 Flyway 迁移脚本，避免跨库强联查。 | 🟡 中等 | 2.5 |
| **微服务拆分** | **MS-002** | `ufp-auth-service` 纯净化与去业务解耦 | **P0** (高) | 目前该微服务虽已建立启动类，但其组件扫描强加载了 `com.zhiyu.auth` 业务模块，须剥离业务，实现纯净化。 | 🟢 简单 | 1.5 |
| **多语言 i18n** | **LN-001** | 前端硬编码中文静态审计与 i18n 替换 | **P0** (高) | 扩展已有的 `check-i18n.sh` 正则扫描机制，在 CI/CD 中配置 ESLint AST 语法树级硬编码中文阻断。 | 🟡 中等 | 3.0 |
| **多语言 i18n** | **LN-002** | i18n 资源文件双端自动对齐校验工具 | **P0** (高) | 编写自动检测脚本（Node.js / Python），在 CI/CD 流程中对 `zh-CN.json` 与 `en-US.json` 执行 Key 对齐校验。 | 🟢 简单 | 1.0 |
| **微服务拆分** | **MS-003** | 服务间 Feign RPC 封装与凭证透传重构 | **P1** (中) | 将原有的进程内 Service 注入修改为声明式 Feign 客户端调用，通过网关或拦截器透传分布式 TraceId 与 JWT 凭证。 | 🟡 中等 | 3.5 |
| **多语言 i18n** | **LN-003** | 双端国际化协同与动态语言偏好持久化 | **P1** (中) | 贯通“前端 LocalStorage ⇄ 网关语言识别头 ⇄ 后端 i18n Filter”链路，同步持久化语言偏好至用户个人资料数据库。 | 🟡 中等 | 2.0 |
| **微服务拆分** | **MS-004** | 分布式事务治理（本地消息表最终一致性落地）| **P2** (低) | 针对跨服务写入，将目前日志 Mock 级的 `OutboxPublisher` 重构为基于 `outbox_event` 表的原子写入与高可靠异步 MQ 投递。 | 🔴 复杂 | 4.0 |

---

## 📋 任务详细实施方案与步骤指南 (深度匹配代码现状)

### P0 级任务：紧急且核心基础设施搭建 (阻断与开荒)

#### MS-001：数据库物理隔离与 Flyway 独立化 (微服务拆分)
*   **工程现状**：目前，所有的数据库表迁移（包括 23 张 `ufp_auth` 认证表及业务表）均以 `V1.x.x` 系列脚本形式混杂存放在 [zhiyu-server 模块下](file:///Users/constantine/Documents/work/code/projects/ZhiYu-Backend/backend/zhiyu-server/src/main/resources/db/migration)。这表明底层的 Flyway 数据库迁移并未实现解耦，是单库单体架构。
*   **具体实施步骤**：
    1.  **物理拆分数据库**：在 MySQL 中，创建独立的 `ufp_auth` 与 `zhiyu_business` 两个物理 Schema。
    2.  **隔离 Flyway 脚本**：将 `V1.4.0__ufp_auth_schema.sql`、`V1.4.1__ufp_auth_seed.sql`、`V1.5.0__ufp_auth_multi_auth.sql` 以及 `V1.7.0` 等平台级认证脚本，物理转移到 `ufp-auth` 模块或独立的 `ufp-auth-service` 中，由该服务独立进行表结构升级。
    3.  **消除跨库强联查 SQL**：重构后台管理模块 `zhiyu-admin` 和 `zhiyu-subscription` 的 XML Mapper，禁止使用 MySQL Native 的跨 Schema 连表 Join（如 `FROM zhiyu.user JOIN ufp_auth.device`）。修改为两次独立的查询由 Service 层内存组装。

#### MS-002：`ufp-auth-service` 纯净化与去业务解耦 (微服务拆分)
*   **工程现状**：[UfpAuthApplication.java](file:///Users/constantine/Documents/work/code/projects/ZhiYu-Backend/backend/ufp/ufp-auth-service/src/main/java/com/zhiyu/ufp/auth/UfpAuthApplication.java#L15-L20) 确实已存在，但是在启动类的配置中，它通过 `@SpringBootApplication(scanBasePackages = {...})` 强行加载了 `"com.zhiyu.common"` 和 `"com.zhiyu.auth"` 业务模块，未做到真正的去业务化。
*   **具体实施步骤**：
    1.  重构 `UfpAuthApplication.java` 启动类，重构其 `@SpringBootApplication` 的 `scanBasePackages` 属性，**剔除业务包路径**，仅保留平台级底座包扫描。
    2.  重构 `@MapperScan`，**剔除 `"com.zhiyu.auth.mapper"`**，仅保留 `com.zhiyu.ufp.auth.mapper`。
    3.  引入 `spring-cloud-starter-alibaba-nacos-discovery` 并声明 `spring.application.name=ufp-auth-service`。

#### LN-001：前端硬编码中文静态审计与 i18n 替换 (多语言 i18n)
*   **工程现状**：前端在 [frontend/scripts/check-i18n.sh](file:///Users/constantine/Documents/work/code/projects/ZhiYu-Backend/frontend/scripts/check-i18n.sh) 中已经配备了一个通过正则表达式匹配 TSX/TS 文件的硬编码中文的 shell 校验脚本。但正则对复杂模版字面量、三元拼接的容错率低，且尚未集成到流水线强制阻断。
*   **具体实施步骤**：
    1.  **升级为 ESLint AST 语法树审计**：在 `frontend/.eslintrc.json` 中配置 `eslint-plugin-i18n-check`，提升检测精度，杜绝正则漏报。
    2.  **补齐漏网之鱼**：依据扫描结果，将 TSX 中漏掉的硬编码中文字符串，统一提炼入 `zh-CN.json` 并用 `t('key')` 替换。

#### LN-002：i18n 资源文件双端自动对齐校验工具 (多语言 i18n)
*   **工程现状**：前端配备了 `en-US.json` 与 `zh-CN.json`（各约 17KB，资源庞大），但目前**完全缺失**多语言 Key 的双向对齐校验，极易因为中文包增加了 Key 但英文包漏掉导致运行时向用户泄露裸露 JSON 占位符。
*   **具体实施步骤**：
    1.  编写本地检测脚本 `frontend/scripts/validate-i18n.js`。
    2.  双向比对中英文 JSON 字典 Key 的 Difference。若不一致，执行 `process.exit(1)` 退出并报错。
    3.  配置该脚本在 PR 和 CI/CD 流程中自动拦截。

---

### P1 级任务：服务交互治理与双端协同 (流畅度与稳定性)

#### MS-003：服务间 Feign RPC 封装与凭证透传重构 (微服务拆分)
*   **工程现状**：目前微服务间基本通过直接调用内存中的 Service 实现，尚未配置跨服务的 Feign 调用。
*   **具体实施步骤**：
    1.  在公共 SPI 模块中定义 Feign 远程客户端接口 `AuthUserClient` 寻址指向目标服务。
    2.  编写 Spring Cloud 统一 `RequestInterceptor` 将凭证（Bearer Token）和 TraceId 自动装配进 Feign 请求头中，实现无损传播。

#### LN-003：双端国际化协同与动态语言偏好持久化 (多语言 i18n)
*   **工程现状**：前端已预留多语言，但后端尚未完全打通 Locale 上下文传递与偏好持久化。
*   **具体实施步骤**：
    1.  在 `ufp-gateway-service` 提取 `Accept-Language` 请求头。
    2.  在 `ufp-common` 中引入 `LocaleFilter`。根据提取出的语言标识，执行 `LocaleContextHolder.setLocale(locale)` 切换当前线程的国际化区域上下文，让后端抛出的异常提示（i18n properties）自适应匹配前端语言。
    3.  用户手动切换语言时，异步调用后端偏好接口，将该语言偏好持久化存入 `auth_user_identity` 的 preferences 物理列中。

---

### P2 级任务：高级微服务数据一致性 (高吞吐防御)

#### MS-004：分布式事务治理（本地消息表最终一致性落地）
*   **工程现状**：在代码库中虽然已预留了 **[OutboxPublisher.java](file:///Users/constantine/Documents/work/code/projects/ZhiYu-Backend/backend/zhiyu-notification/src/main/java/com/zhiyu/notification/service/OutboxPublisher.java)**，但其仅是一个打印 `log.info("[Outbox] ...")` 的 **Mock（Dummy）简易实现**。真正的基于 `outbox_event` 本地表插入和高可靠的定时/事务轮询器尚未开始搭建。
*   **具体架构实施方案**：
    1.  **消息落库原子化**：废弃 `OutboxPublisher` 的 Mock 打印，将其重构为向 `outbox_event` 本地发件箱表写入 JSON 消息。
    2.  **定时轮询或 CDC 监听**：编写独立的事务调度器（定时轮询或基于 Canal 监听 Binlog），将未派发的事件异步投递至 RocketMQ 消息队列。
    3.  **消费幂等表设计**：在下游消费端，配置基于唯一事件 ID 的消费幂等表，防范消息重复投递导致的数据错乱。
