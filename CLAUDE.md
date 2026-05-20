# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在此仓库中工作时提供指导。

## 项目：ZhiYu-Backend

AI 原生应用平台后端。Java 21 + Spring Boot 3.3.x + Spring Cloud Alibaba，Maven 多模块，MySQL 8.0 + Redis 7，部署于阿里云 ACK（K8s）。

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
| 认证 | JWT RS256 + BCrypt + TOTP + WebAuthn |
| 流控 | Sentinel |
| 测试 | JUnit 5 + Mockito + Testcontainers |
| CI/CD | GitHub Actions → 阿里云 ACR → ACK |
| 监控 | Prometheus + Grafana + Loki |

## 模块布局

```
ZhiYu-Backend/
├── backend/                       # Maven 多模块项目
│   ├── pom.xml                    # 父 POM（版本 BOM + 插件管理）
│   ├── zhiyu-common/              # 公共模块：工具类、异常、过滤器、DTO、i18n
│   ├── zhiyu-auth/                # 认证模块：注册、登录、JWT、验证码、密码重置
│   ├── zhiyu-user/                # 用户模块：个人信息、设备、TOTP、WebAuthn、账号注销
│   ├── zhiyu-subscription/        # 订阅模块：套餐、订单、支付、配额、退款
│   ├── zhiyu-admin/               # 管理模块：管理员认证、RBAC、用户管理、审计日志
│   └── zhiyu-server/              # 入口模块：Spring Boot 启动、Flyway 迁移、打包
│       └── src/main/resources/db/migration/  # V1.0.0 ~ V1.2.0
├── frontend/                      # 前端项目（预留）
├── docs/                          # 完整设计文档（PRD、API-SPEC、ARCHITECTURE 等）
├── deploy/                        # K8s 清单（manifests/）、Dockerfiles、环境变量（envs/）、部署脚本（scripts/）
│   ├── packages/                  # 离线依赖归档（JDK、Docker、K8s CLI 二进制、容器镜像 tar）
│   └── scripts/                   # 微服务集成部署与离线打包控制脚本
└── artifact/                      # 【Git 忽略】统一制品输出库（离线发布包归档存放目录）
```

依赖方向（单向，无循环）：
```
server → admin → subscription → user → auth → common
```

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

# 代码检查（Checkstyle + SpotBugs）
./mvnw -f backend/pom.xml checkstyle:check spotbugs:check
```

## 核心规范

- **仅构造器注入** — 使用 Lombok `@RequiredArgsConstructor`，禁止 `@Autowired` 字段注入
- **Controller 薄层** — 禁止在 Controller 中注入 Mapper，禁止编写业务逻辑
- **Entity → Resp DTO** — 通过 MapStruct Converter 转换，禁止直接暴露 Entity
- **事务** — `@Transactional(rollbackFor = Exception.class)` 始终使用，只读查询显式标记
- **错误码** — i18n 国际化：`messages.properties`（英文兜底）+ `messages_zh_CN.properties`
- **API 响应格式**：`{ "code": 0, "message": "success", "data": {...}, "requestId": "uuid", "timestamp": 1716019200 }`
- **测试命名**：`*Test.java`（单元测试，Surefire），`*IT.java`（集成测试，Failsafe）
- **不可变数据** — 创建新对象，禁止修改已有对象
- **文件权限** — `.sh` 可执行脚本 `755`，`.yaml`/`.env` `644`，密钥文件 `600`，密钥目录 `700`（详 docs/SECURITY.md §2.0）

## 模块规则

- `zhiyu-common`：零业务依赖，仅第三方库
- `zhiyu-auth` → `zhiyu-server`：跨模块调用仅通过 Service 接口注入
- `zhiyu-server`：无业务代码，仅 Spring Boot 入口 + Flyway + 打包
- Mapper 接口禁止跨模块边界

## CodeGraph

本项目已初始化 `.codegraph/`。使用 `codegraph_search`、`codegraph_context`、`codegraph_callers`、`codegraph_callees` 进行代码探索。

## 文档索引

| 文档 | 内容 |
|------|------|
| `docs/PRD.md` | 产品需求、用户故事、KPI、P0/P1/P2 范围 |
| `docs/ARCHITECTURE.md` | ADR（10 项决策）、时序图、部署拓扑 |
| `docs/API-SPEC.md` | 完整 API 规范，含请求/响应结构 |
| `docs/DATABASE.md` | 完整 DDL、ER 关系、索引设计 |
| `docs/DEVELOPMENT-STANDARDS.md` | 编码规范、包结构、错误码、命名 |
| `docs/SECURITY.md` | 安全测试、OWASP、个人信息保护合规 |
| `docs/TEST-PLAN.md` | 各模块测试用例（单元 + 集成 + E2E） |
| `docs/CI-CD.md` | GitHub Actions 流水线、分支策略、K8s 部署 |
| `docs/OPS.md` | SLO/SLI、Grafana 看板、告警、灾备 |
| `docs/INFRASTRUCTURE.md` | Nacos 配置、Redis Key、MySQL Schema |
| `docs/RATE-LIMITING.md` | 三层流控架构 |
| `docs/APP-DESIGN.md` | iOS/Android 原生应用设计规范 |
| `docs/FRONTEND-DESIGN.md` | 管理后台前端组件树与状态 |
