# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在此仓库中工作时提供指导。

## 项目：ZhiYu-Backend

AI 原生应用平台后端。Java 21 + Spring Boot 3.3.x + Spring Cloud Alibaba，Maven 多模块，MySQL 8.0 + Redis 7，部署于 kubeadm K8s 单节点（阿里云 ACK 为后续目标）。

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Java 21 (Eclipse Temurin) |
| 框架 | Spring Boot 3.3.7 + Spring Cloud 2023.0.3 + Spring Cloud Alibaba 2023.0.1.0 |
| 构建 | Maven 3.9+（多模块，mvnw wrapper） |
| ORM | MyBatis-Plus 3.5.10 |
| 数据库迁移 | Flyway 10.18.2 |
| 数据库 | MySQL 8.0 (RDS) |
| 缓存 | Redis 7 (Sentinel) |
| 配置中心 | Nacos 2.x |
| 认证 | JWT RS256 + BCrypt（TOTP + WebAuthn 为 P1，未实现）|
| 流控 | Sentinel |
| 工具库 | Hutool 5.8.35 + Apache Commons Lang3 3.17.0 |
| 代码规范 | Checkstyle（自定义）+ SpotBugs + 阿里 p3c-pmd 2.1.1 |
| 测试 | JUnit 5 + Mockito + Testcontainers |
| CI/CD | Woodpecker v3.4.0 + Gitea（本地 kubeadm 部署）|
| 监控 | Prometheus + Grafana（Loki 已文档化，未部署）|

## 开发工具链

| 工具 | 地址 | 说明 |
|------|------|------|
| Gitea | `http://192.168.0.105:3000` | Git 仓库托管 + OAuth 认证 |
| Woodpecker CI | `http://localhost:8000` | CI/CD 控制台（登录走 Gitea OAuth） |
| Nexus Maven | `http://192.168.0.105:8081` | 私有 Maven 制品库 (`admin` / `admin123`) |
| Docker 镜像缓存 | `~/.m2/repository` | Maven 本地依赖缓存 |

> 注意：Gitea 和 Nexus 使用宿主机 LAN IP (`192.168.0.105`)，如 IP 变更需同步更新 `woodpecker/bin/docker-compose.yml` 和 `backend/.mvn/settings.xml`。

## 模块布局

```
ZhiYu-Backend/
├── backend/                       # Maven 多模块项目
│   ├── pom.xml                    # 父 POM（版本 BOM + 插件管理）
│   ├── ufp/                       # UFP 统一基础平台（规划独立拆分）
│   │   ├── ufp-common/            # 平台基础设施（零ORM/缓存）：工具类、异常、Filter、DTO、i18n、缓存、验证码、校验、验证/审计/存储抽象、事件、Provider注册、@UfpDS数据源注解、API文档
│   │   └── ufp-auth/              # 认证库（含ORM+Redis）：JWT/BCrypt/TOTP/WebAuthn/OAuth/实体+Mapper+Service/多数据源自动配置（library，→ ufp-common）
│   ├── zhiyu-common/              # ZhiYu 业务公共模块（MyBatis-Plus/Redis 配置，→ ufp-common）
│   ├── zhiyu-auth/                # 业务认证：注册/登录/验证码/密码重置/OAuth Provider实现/TOTP/WebAuthn/设备管理（→ ufp-auth + zhiyu-common）
│   ├── zhiyu-user/                # 用户资料模块：个人信息/偏好/注销（纯资料，不含认证）（→ zhiyu-auth + zhiyu-common）
│   ├── zhiyu-notification/        # 通知模块：邮件/SMS/Push 统一发送（→ zhiyu-common）
│   ├── zhiyu-subscription/        # 订阅模块：套餐、订单、支付、配额、退款
│   ├── zhiyu-admin/               # 管理模块：管理员认证、RBAC、用户管理、审计日志
│   └── zhiyu-server/              # 入口模块：Spring Boot 启动、Flyway 迁移（Phase 1 含 V1.0.0~V1.5.0）、打包
├── frontend/                      # 前端项目（预留）
├── docs/                          # 完整设计文档（PRD、API-SPEC、ARCHITECTURE 等）
├── deploy/                        # K8s 清单（manifests/）、Dockerfiles、环境变量（envs/）、部署脚本（scripts/）
│   ├── packages/                  # 离线依赖归档（JDK、Docker、K8s CLI 二进制、容器镜像 tar）
│   └── scripts/                   # 微服务集成部署与离线打包控制脚本
└── artifact/                      # 【Git 忽略】统一制品输出库（离线发布包归档存放目录）
```

依赖方向（单向，无循环）：
```
server → admin → subscription → user → auth → zhiyu-common → ufp-common
                                            → ufp-auth ────┘
```

> **依赖边界**：`ufp-common` 零 ORM/缓存（无 MyBatis-Plus、Redis）。`ufp-auth` 允许直接依赖 MyBatis-Plus + Redis（管理 ufp_auth 库）。`zhiyu-common` 含 MyBatis-Plus + Redis（管理 zhiyu 业务库）。

## 常用命令

```bash
# 编译 + 单元测试
./mvnw -f backend/pom.xml clean test

# 全量测试（集成测试需要 Docker）
./mvnw -f backend/pom.xml clean verify

# 启动开发服务器
./mvnw -f backend/pom.xml spring-boot:run -pl zhiyu-server -Dspring.profiles.active=dev

# 打包
./mvnw -f backend/pom.xml clean package -DskipTests

# Docker 构建（多阶段，amd64/arm64 多架构）
docker build -t zhiyu-backend:$(cat .version) .
docker buildx build --platform linux/amd64,linux/arm64 -t zhiyu-backend:$(cat .version) --push .

# Docker 构建（预提取分层 JAR — kubeadm 离线部署）
docker build -t zhiyu-backend:$(cat .version) -f deploy/docker/Dockerfile.kubeadm .

# 离线打包（编译 JAR → 构建镜像 → 导出部署包，不含源码）
./deploy/scripts/offline-pack.sh kubeadm

# 远端离线部署（解压后执行）
./offline-deploy.sh            # 完整部署
./offline-deploy.sh --dry-run  # 仅校验

# 本地控制端快捷集成调度（推荐：Mac 本地打 JAR 包同步并远程单节点部署）
./deploy/deploy-to-remote.sh                # 零参数降维自举：本地极速编译、rsync同步，远程一键集成部署全套（含基础组件、微服务）
./deploy/deploy-to-remote.sh --reset-kubeadm# 终极一键开荒：远程彻底物理重置（kubeadm reset --force），重新初始化，部署核心服务与可观测监控栈
./deploy/deploy-to-remote.sh status         # 智能自检回测：深度诊断核心微服务 Actuator、MySQL、Redis 及监控实例的 UP 状态
./deploy/deploy-to-remote.sh cleanup        # 卸载远程所有部署资源，清理有状态及无状态资源，并等待命名空间安全释放
./deploy/deploy-to-remote.sh show-secrets   # 安全读取并以整齐表格显示本地和 Kubernetes 实时 Secret 中的解密运维密码

# 远端主机统一分发网关 deploy.sh (缺省环境默认为 kubeadm，缺省动作默认为 all)
./deploy/deploy.sh                # 零参数：默认 kubeadm 环境，按序执行 check-env → infra → init → build → deploy
./deploy/deploy.sh dev all        # 指定开发环境（阿里云 ACK）一键集成部署全部资源
./deploy/deploy.sh status         # 运行就绪自检探测，对微服务、数据库、缓存进行深度诊断
./deploy/deploy.sh show-secrets   # 安全解密并整齐显示 MySQL, Redis, Nacos, Grafana 的实时运维密码
./deploy/deploy.sh monitoring     # 部署 Prometheus + Grafana 监控栈（含 node-exporter 与 4 大预置离线大盘）
./deploy/deploy.sh cleanup        # 物理清理卸载当前环境下所有部署资源

# 代码检查（Checkstyle + SpotBugs + 阿里 p3c-pmd）
./mvnw -f backend/pom.xml checkstyle:check spotbugs:check pmd:check

# Woodpecker CI 流水线本地校验
/Users/constantine/devs/rnd-cicd/bin/woodpecker-cli lint .woodpecker/.woodpecker.yml
```

## 核心规范

- **仅构造器注入** — 使用 Lombok `@RequiredArgsConstructor`，禁止 `@Autowired` 字段注入
- **Controller 薄层** — 禁止在 Controller 中注入 Mapper，禁止编写业务逻辑
- **Entity → Resp DTO** — 通过 MapStruct Converter 转换，禁止直接暴露 Entity
- **事务** — `@Transactional(rollbackFor = Exception.class)` 始终使用，只读查询显式标记
- **错误码** — i18n 国际化：`messages.properties`（英文兜底）+ `messages_zh_CN.properties`
- **前端 i18n 强制** — 所有用户可见文本（菜单、按钮、提示、标签、placeholder、面包屑）必须通过 `t()` 函数引用 i18n key，禁止硬编码中文或英文。新增 key 需同步添加到 `zh-CN.json` 和 `en-US.json`（key 名保持一致）。例外：日志、数据库内容、后端控制台输出
- **API 响应格式**：`{ "code": 0, "message": "success", "data": {...}, "requestId": "uuid", "timestamp": 1716019200 }`
- **测试命名**：`*Test.java`（单元测试，Surefire），`*IT.java`（集成测试，Failsafe）
- **不可变数据** — 创建新对象，禁止修改已有对象
- **文件权限** — `.sh` 可执行脚本 `755`，`.yaml`/`.env` `644`，密钥文件 `600`，密钥目录 `700`（详 docs/dev-test/SECURITY.md §2.0）

### 前端共享模式（必须）

| 场景 | 使用 | 禁止 |
|------|------|------|
| API 响应解包 | `unwrap(res)` from `utils/unwrap.ts` | `res.data?.data` 裸调用 |
| 单次异步数据 | `useAsyncData(fetcher, deps)` from `hooks/useAsyncData` | 手写 `loading/error/fetchData` 样板 |
| 分页表格数据 | `usePaginatedData(fetcher, extraDeps)` from `hooks/usePaginatedData` | 手写 `page/size/total/onChange` 样板 |
| 页面加载态/错误态 | `<PageLoader loading error onRetry>` from `components/PageLoader` | 手写 `if(loading) return Spin; if(error) return Alert` |
| 统计卡片 | `<CosmicStatCard title value color stagger>` from `components/CosmicStatCard` | 手写 `glass-panel cosmic-stat-card` div |
| ECharts 主题 | `registerEcharts()` + `CHART_COLORS` from `utils/chartTheme` | 每页面独立注册 echarts / 定义颜色常量 |
| 标签颜色映射 | `PROVIDER_LABELS`, `TYPE_LABELS`, `RESULT_COLORS` from `constants/labels` | 每文件重复定义相同映射表 |
| 路由鉴权 | `<RequireAuth>` from `components/RequireAuth` | 未包裹的受保护路由 |
| 国际化文本 | `t('section.key')` from `useTranslation()` | 硬编码中文字符串 |
| i18n 翻译维护 | 同步写入 `zh-CN.json` + `en-US.json`，key 一致 | 仅写一种语言 / key 不一致 |

### 后端共享模式（可选，新端点优先使用）

- **分页查询** — 使用 `PageQuery`（`zhiyu-common` `com.zhiyu.common.web`）替代重复的 `@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size`。现有端点可逐步迁移

## 模块规则

- `ufp-common`：仅通用基础设施，**零 ORM/缓存依赖**（不含 MyBatis-Plus、Redis）。提供 `@UfpDS` 注解 + `UfpDSContextHolder`（纯 Java，零框架依赖），消费模块自行实现数据源路由。i18n 资源文件在此
- `ufp-auth`：认证库（library jar），提供 JWT/BCrypt/TOTP/WebAuthn/OAuth/Token黑名单/实体+Mapper+Service 等平台级认证能力。**允许直接依赖 MyBatis-Plus + Redis**（管理 ufp_auth 库 23 张表），自行实现 `AbstractRoutingDataSource` + AOP 切面完成 ufp_auth 数据源自动配置。Phase 2 独立部署为微服务
- `zhiyu-common`：ZhiYu 业务公共配置（MyBatis-Plus / Redis），自行实现数据源路由配置 zhiyu 业务库数据源，依赖 ufp-common
- `zhiyu-auth`：ZhiYu 业务认证（注册/登录/验证码/密码重置/OAuth Provider 实现/TOTP/WebAuthn/设备管理），依赖 ufp-auth + zhiyu-common
- `zhiyu-user`：纯用户资料管理（profile/偏好/注销），不含认证功能。依赖 zhiyu-auth + zhiyu-common
- `zhiyu-notification`：通知模块（邮件/SMS/Push 统一发送），所有业务模块可注入使用。依赖 zhiyu-common
- `zhiyu-server`：无业务代码，仅 Spring Boot 入口 + Flyway + 打包。Phase 1 共用所有 Flyway 迁移（V1.0.0~V1.5.0），Phase 2 ufp_auth 迁移（V1.4.x/V1.5.x）拆分至 ufp-auth-server
- Mapper 接口禁止跨模块边界
- 跨模块调用仅通过 Service 接口注入

## CodeGraph

本项目已初始化 `.codegraph/`。使用 `codegraph_search`、`codegraph_context`、`codegraph_callers`、`codegraph_callees` 进行代码探索。

## 文档索引

| 文档 | 内容 |
|------|------|
| `docs/product-design/PRD.md` | 产品需求、用户故事、KPI、P0/P1/P2 范围 |
| `docs/product-design/ARCHITECTURE.md` | 4+1 视图、L0-L2 分层、时序图、部署拓扑 |
| `docs/product-design/ARCHITECTURE-UFP.md` | UFP 平台模块架构设计（平台级，不绑定 ZhiYu） |
| `docs/product-design/ADR.md` | 架构决策记录（ADR-001 ~ ADR-014） |
| `docs/product-design/API-SPEC.md` | 完整 API 规范，含请求/响应结构 |
| `docs/product-design/DATABASE.md` | 完整 DDL、ER 关系、索引设计 |
| `docs/dev-test/DEVELOPMENT-STANDARDS.md` | 编码规范、包结构、错误码、命名 |
| `docs/dev-test/SECURITY.md` | 安全测试、OWASP、个人信息保护合规 |
| `docs/dev-test/TEST-PLAN.md` | 各模块测试用例（单元 + 集成 + E2E） |
| `docs/deploy-ops/CI-CD.md` | Woodpecker CI 流水线、Docker 构建、K8s 部署 |
| `docs/deploy-ops/OPS.md` | SLO/SLI、Grafana 看板、告警、灾备 |
| `docs/deploy-ops/INFRASTRUCTURE.md` | Nacos 配置、Redis Key、MySQL Schema |
| `docs/deploy-ops/RATE-LIMITING.md` | 三层流控架构 |
| `docs/product-design/APP-DESIGN.md` | iOS/Android 原生应用设计规范 |
| `docs/product-design/FRONTEND-DESIGN.md` | 管理后台前端组件树与状态 |
