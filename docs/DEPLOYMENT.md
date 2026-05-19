# ZhiYu-Backend 部署指南

> 本文档定义从零到一的完整部署流程，涵盖 OS 级依赖初始化（bootstrap）、离线包归档（packages/）和一键应用部署（deploy.sh）。架构决策见 [ARCHITECTURE.md](ARCHITECTURE.md#5-部署架构)，CI/CD 流水线见 [CI-CD.md](CI-CD.md)。

## 目录

- [1. 部署架构概览](#1-部署架构概览)
- [2. 支持矩阵](#2-支持矩阵)
- [3. 快速开始](#3-快速开始)
- [4. Bootstrap — 系统依赖初始化](#4-bootstrap--系统依赖初始化)
- [5. 离线包归档](#5-离线包归档)
- [6. Deploy — 应用部署](#6-deploy--应用部署)
- [7. Ansible — 服务器批量初始化](#7-ansible--服务器批量初始化)
- [8. 离线部署完整流程](#8-离线部署完整流程)
- [9. 环境变量参考](#9-环境变量参考)
- [10. 已知限制](#10-已知限制)

---

## 1. 部署架构概览

### 1.1 两阶段部署

```
┌─────────────────────────────────────────────────────────────┐
│                    阶段 1: Bootstrap                        │
│  安装 OS 级依赖：Docker, kubectl, JDK 21                   │
│  ./bootstrap/bootstrap.sh                 (离线默认)       │
│  ./bootstrap/download-packages.sh         (预下载离线包)    │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────┴───────────────────────────────────┐
│                    阶段 2: Deploy                           │
│  部署基础设施 → 初始化配置 → 构建镜像 → 部署应用             │
│  ./deploy/deploy.sh <env> <action>                          │
└─────────────────────────────────────────────────────────────┘
```

**离线优先（Offline-First）**：默认所有操作从 `bootstrap/packages/` 本地目录读取预下载的安装包，不依赖外部网络。`--online` 标志切换为在线模式。

### 1.2 K8s Pod 拓扑

```
Namespace: zhiyu-{env}
│
├── 基础设施 Pod ────────────────────────────
│   ├── mysql-0                  StatefulSet (1 副本)
│   │   PVC: data-mysql-0 (volumeClaimTemplates)
│   │   端口: 3306
│   │   探活: mysqladmin ping
│   │
│   ├── redis-{hash}             Deployment (1 副本)
│   │   PVC: redis-data
│   │   端口: 6379
│   │   探活: redis-cli ping (智能处理空密码)
│   │
│   └── nacos-{hash}             Deployment (1 副本, standalone)
│       PVC: nacos-data
│       端口: 8848 (http) + 9848 (gRPC)
│       认证: 默认启用
│
├── 应用 Pod ────────────────────────────────
│   └── zhiyu-backend-{hash}     Deployment (dev) / Rollout (staging/release)
│       端口: 8080
│       initContainers: wait-for-mysql → wait-for-nacos
│       配置: ConfigMap + Secret (JWT/DB/Redis)
│       dev: 1 副本, staging: 2 副本, release: 4 副本
│
├── Argo Rollouts (staging/release) ──────────
│   └── argo-rollouts-{hash}     Deployment (argo-rollouts namespace)
│       金丝雀发布: 20% → 40% → 100% (AnalysisTemplate 验证健康状态)
│
└── 辅助资源 ────────────────────────────────
    ├── HPA                       基于 CPU 70% + Memory 80%
    ├── PDB                       minAvailable ≥ 1
    ├── NetworkPolicy             最小 egress (MySQL/Redis/Nacos/DNS/HTTPS)
    └── ServiceAccount            为 Alibaba Cloud IRSA 做准备
```

**启动顺序**：MySQL → Redis → Nacos → Argo Rollouts Controller → `zhiyu-backend`（应用内 Flyway 自动迁移）

**发布策略**：
- dev 环境：Deployment 滚动更新（maxSurge=1, maxUnavailable=0）
- staging/release 环境：Argo Rollouts 金丝雀发布（20%→40%→100%，每步 AnalysisTemplate 健康检查，CI 手动 promote）

### 1.3 Docker 镜像

| 镜像 | Dockerfile | 大小（约） | 用途 |
|------|-----------|-----------|------|
| `zhiyu-backend` | `Dockerfile` | ~200 MB | 应用运行时（JRE + Spring Boot 分层 JAR，内嵌 Flyway 迁移） |
| `argo-rollouts` | 官方镜像 | ~80 MB | Argo Rollouts Controller（金丝雀发布控制器，staging/release 环境） |

---

## 2. 支持矩阵

### 2.1 操作系统与架构

| 操作系统 | 架构 | Bootstrap | 离线包 | 部署 | 备注 |
|----------|------|:---------:|:------:|:----:|------|
| macOS (Apple Silicon) | arm64 | ✅ | ✅ | ✅ | Docker Desktop 需手动安装 |
| macOS (Intel) | amd64 | ✅ (在线) | ✅ | ✅ | Docker Desktop 需手动安装 |
| Ubuntu 22.04+ | amd64 | ✅ | ✅ | ✅ | |
| Ubuntu 22.04+ | arm64 | ✅ | ✅ | ✅ | |
| Debian 12+ | amd64 | ✅ | ✅ | ✅ | |
| Debian 12+ | arm64 | ✅ | ✅ | ✅ | |
| CentOS 7 | amd64 | ✅ | ✅ | ✅ | 需 EPEL |
| CentOS 7 | arm64 | — | — | — | CentOS 7 无官方 ARM64 支持 |

### 2.2 组件版本与离线包

| 组件 | 版本 | 包类型 | 大小（约） | amd64 | arm64 |
|------|------|--------|-----------|:-----:|:-----:|
| JDK (Temurin) | 21.0.9+10 | tar.gz | ~185 MB | ✅ | ✅ |
| Maven | 3.9.16 | tar.gz | ~9 MB | ✅ | ✅ |
| Maven 离线仓库 | — | tar.gz | ~500-800 MB | ✅ | ✅ |
| Docker Engine | 29.5.1 | static .tgz | ~78 MB | ✅ | ✅ |
| kubectl | v1.31.0 | 二进制 | ~50 MB | ✅ | ✅ |
| Redis (镜像) | 7-alpine | docker tar | ~30 MB | ✅ | ✅ |
| Nacos (镜像) | v2.4.0 | docker tar | ~1 GB | ✅ | ✅ |
| MySQL (镜像) | 8.0 | docker tar | ~600 MB | ✅ | ✅ |
| Temurin JDK (镜像) | 21-jdk-alpine | docker tar | ~200 MB | ✅ | ✅ |
| Temurin JRE (镜像) | 21-jre-alpine | docker tar | ~150 MB | ✅ | ✅ |
| Argo Rollouts CLI | v1.7.2 | 二进制 | ~45 MB | ✅ | ✅ |
| Argo Rollouts (镜像) | latest | docker tar | ~80 MB | ✅ | ✅ |

### 2.3 安装模式

| 模式 | 标志 | Docker | kubectl | JDK | Maven | 镜像 |
|------|------|--------|---------|-----|-------|------|
| **离线**（默认） | — | static .tgz → /usr/local/bin | 本地二进制 | 本地 tar.gz | 本地 tar.gz + .m2 | docker load |
| **在线** | `--online` | apt/yum 官方仓库 | curl 下载 | apt/yum 仓库 | — | docker pull |
| **仅 Docker** | `--docker-only` | ✅ | ✅ | ✗ | ✗ | ✗ |
| **干运行** | `--dry-run` | 仅打印命令 | 仅打印 | 仅打印 | — | — |

---

## 3. 快速开始

### 3.1 在线环境（有网络）

```bash
# 一键安装所有系统依赖
./bootstrap/bootstrap.sh --online

# 一键部署应用（dev 环境全流程）
./deploy/deploy.sh dev all
```

### 3.2 离线环境（无网络）

```bash
# 1. 在联网机器上：下载所有离线包
./bootstrap/download-packages.sh

# 2. 打包传输到目标机器
tar -czf bootstrap-offline.tar.gz bootstrap/
scp bootstrap-offline.tar.gz user@target:/tmp/

# 3. 在目标机器上：解压并安装
ssh user@target 'cd /path/to/zhiyu-backend && tar -xzf /tmp/bootstrap-offline.tar.gz'
ssh user@target 'cd /path/to/zhiyu-backend && ./bootstrap/bootstrap.sh'

# 4. 部署应用
./deploy/deploy.sh dev all
```

---

## 4. Bootstrap — 系统依赖初始化

### 4.1 bootstrap.sh

```
用法: ./bootstrap/bootstrap.sh [选项]

选项:
  (无)              离线模式 — 从 packages/ 安装所有依赖
  --online          在线模式 — 从网络下载并安装
  --offline         显式指定离线模式
  --dry-run         仅检查并打印操作，不实际执行
  --docker-only     仅安装 Docker + kubectl，跳过 JDK
```

**安装内容（完整模式）**：

| 步骤 | macOS | Ubuntu/Debian | CentOS 7 |
|------|-------|---------------|----------|
| 1 | 安装 Homebrew（如未安装） | `apt update` | 安装 EPEL + `yum makecache` |
| 2 | Docker Desktop (brew cask) | Docker Engine (apt/static) | Docker Engine (yum/static) |
| 3 | kubectl (brew/本地) | kubectl (curl/本地) | kubectl (本地二进制) |
| 4 | JDK 21 (brew/本地) | JDK 21 (apt/本地) | JDK 21 (yum/本地) |
| 5 | openssl, wget | openssl, curl, jq, envsubst | openssl, curl, wget, jq, envsubst |
| 7 | — | — | — |
| 离线额外 | — | Docker 镜像导入 + Maven 仓库解压 | 同左 |

**架构自动检测**：

脚本通过 `uname -m` 自动检测 CPU 架构并映射为包命名后缀：

| `uname -m` | PKG_ARCH | JDK_ARCH | 示例模式 |
|------------|----------|----------|----------|
| `x86_64` / `amd64` | `amd64` | `x64` | `kubectl-linux-amd64`, `OpenJDK21U-jdk_x64_linux_*` |
| `arm64` / `aarch64` | `arm64` | `aarch64` | `kubectl-linux-arm64`, `OpenJDK21U-jdk_aarch64_linux_*` |

### 4.2 download-packages.sh

```
用法: ./bootstrap/download-packages.sh [目标]

目标:
  (无)              下载当前 OS/Arch 的包
  all               下载所有平台的包（跨平台部署用）
  linux             下载 Ubuntu/Debian Linux 包（含 Docker static）
  centos            下载 CentOS 7/RHEL 7 包（含 Docker static）
  macos             下载 macOS 当前架构的包
  verify            校验已下载包的 SHA256
```

**按平台下载内容**：

| 平台 | JDK | Maven | Docker | kubectl | 镜像 |
|------|:---:|:-----:|:------:|:-------:|:----:|
| macos-arm64 | ✅ aarch64_mac | ✅ | — | ✅ darwin-arm64 | ✅ |
| macos-x64 | ✅ x64_mac | ✅ | — | ✅ darwin-amd64 | ✅ |
| linux-x64 | ✅ x64_linux | ✅ | ✅ static x86_64 | ✅ linux-amd64 | ✅ |
| linux-arm64 | ✅ aarch64_linux | ✅ | ✅ static aarch64 | ✅ linux-arm64 | ✅ |

---

## 5. 离线包归档

### 5.1 目录结构

```
bootstrap/packages/
├── README.md
├── SHA256SUMS                          # 所有文件 SHA256 校验清单
├── jdk/                                # JDK 21 (Eclipse Temurin)
│   ├── OpenJDK21U-jdk_aarch64_mac_*.tar.gz
│   ├── OpenJDK21U-jdk_aarch64_linux_*.tar.gz
│   └── OpenJDK21U-jdk_x64_linux_*.tar.gz
├── maven/                              # Maven 3.9.16
│   ├── apache-maven-3.9.16-bin.tar.gz
│   └── maven-offline-repo.tar.gz       # 离线 Maven 依赖仓库
├── docker/                             # Docker Engine (Static 二进制)
│   ├── docker-29.5.1-x86_64.tgz
│   └── docker-29.5.1-aarch64.tgz
├── kubectl/                            # kubectl 二进制
│   ├── kubectl-darwin-arm64
│   ├── kubectl-linux-amd64
│   └── kubectl-linux-arm64
└── images/                             # Docker 镜像 tar
    ├── redis-7-alpine.tar              # Redis 7 Alpine
    ├── nacos-server-v2.4.0.tar         # Nacos Server 2.4.0
    ├── mysql-8.0.tar                   # MySQL 8.0
    ├── eclipse-temurin-21-jdk-alpine.tar  # Dockerfile Stage 1
    └── eclipse-temurin-21-jre-alpine.tar  # Dockerfile Stage 2
```

### 5.2 SHA256 校验

```bash
# 下载后自动生成校验清单
./bootstrap/download-packages.sh
# → 生成 packages/SHA256SUMS

# 随时校验包完整性
./bootstrap/download-packages.sh verify
```

### 5.3 Maven 离线仓库

离线环境下 Maven 编译需要预下载所有依赖。`prepare_maven_offline()` 自动执行：

```bash
cd zhiyu-backend
./mvnw dependency:go-offline -Dmaven.repo.local=/tmp/m2-offline -pl zhiyu-server -am
tar czf bootstrap/packages/maven/maven-offline-repo.tar.gz -C /tmp/m2-offline .
```

目标机器上 `bootstrap.sh` 自动解压到 `~/.m2/repository`，之后 `mvn package` 无需联网。

### 5.4 Docker 镜像离线

镜像通过 `docker save` 导出、`docker load` 导入：

```bash
# 导出（联网机器）
docker pull redis:7-alpine
docker save -o bootstrap/packages/images/redis-7-alpine.tar redis:7-alpine

# 导入（离线机器）
docker load -i bootstrap/packages/images/redis-7-alpine.tar
```

**架构注意**：`docker save` 保存当前机器架构的镜像。如需同时支持 amd64 和 arm64，需分别在两种架构的机器上执行 `download-packages.sh`。

---

## 6. Deploy — 应用部署

### 6.1 deploy.sh

```
用法: ./deploy/deploy.sh <env> <action>

环境:
  dev        开发环境（K8s 内 MySQL/Redis/Nacos）
  staging    预发布环境（外部 RDS/Redis/Nacos）
  release    生产环境（外部 RDS/Redis/Nacos）

操作:
  check      验证前置条件（kubectl, docker, openssl, kubectl-argo-rollouts, 集群连接）
  infra      部署基础设施（MySQL StatefulSet + Redis + Nacos 到 K8s）
  init       初始化数据（DB 库 + Nacos 配置 + JWT 密钥）
  build      Maven 编译 + Docker 构建 + 推送镜像
  deploy     部署应用到 K8s（ConfigMap/Secret → Deployment/Rollout → HPA/PDB/NetworkPolicy）
  all        按序执行 check → infra → init → build → deploy → status
  status     显示组件运行状态和健康检查

选项:
  --dry-run  客户端验证模式（kubectl --dry-run=client），不实际部署

示例:
  ./deploy/deploy.sh dev all                    # 开发环境一键部署
  ./deploy/deploy.sh staging infra              # 仅部署预发布基础设施
  ./deploy/deploy.sh release status             # 查看生产环境状态
  ./deploy/deploy.sh release deploy --dry-run   # 验证生产环境配置（不实际部署）
```

### 6.2 操作流程详解

#### check — 前置条件检查

验证 `kubectl`、`docker`、`openssl` 已安装，且 `kubectl` 已连接到集群。任一条件不满足即中止。

#### infra — 部署基础设施

根据环境变量中的 `MYSQL_STORAGE`、`REDIS_STORAGE`、`NACOS_STORAGE` 决定是否在 K8s 内部署：

| 条件 | 行为 |
|------|------|
| `MYSQL_STORAGE` 非空 | 部署 MySQL **StatefulSet** + Headless Service + ClusterIP Service + Secret |
| `MYSQL_STORAGE` 为空 | 跳过 MySQL（使用外部 RDS） |
| `REDIS_STORAGE` 非空 | 部署 Redis Deployment + PVC + Service + Secret（智能处理空密码） |
| `NACOS_STORAGE` 非空 | 部署 Nacos Deployment + PVC + Service + Secret（认证默认启用） |

YAML 模板通过 `envsubst` 替换 `${VAR}` 占位符后 `kubectl apply`。

#### init — 初始化数据

1. **init-db.sh** — 在 K8s MySQL Pod 中创建 `nacos` 和 `${MYSQL_DATABASE}` 数据库，授权给应用用户
2. **init-nacos.sh** — 通过 Nacos Open API 推送 5 个配置文件。若 Nacos 未部署（`NACOS_STORAGE` 为空）且 `NACOS_CONFIG_ENABLED` 与 `NACOS_DISCOVERY_ENABLED` 均为 `false`，则直接跳过，不阻塞后续部署
3. **gen-jwt-keys.sh** — 使用 `openssl` 生成 RS256 RSA 2048 位密钥对

#### build — 构建镜像

```
预编译 JAR → 分层提取 → Docker 构建 → 推送镜像
```

- **离线部署 (kubeadm)**：JAR 在本地预编译后 scp 上传到远端，远端仅执行 Docker 构建 + ctr 导入到 containerd
- **在线部署 (ACK)**：JAR 在 CI 中预编译，Docker 构建后推送到 ACR
- Docker 镜像：单阶段 `eclipse-temurin:21-jre-alpine`，COPY 预提取的分层 JAR
- 推送到 `${DOCKER_REGISTRY}/${DOCKER_IMAGE}:${DOCKER_TAG}` 和 `:latest`

#### deploy — 部署应用

1. **ConfigMap + Secret** — 模板化部署 ConfigMap；创建/更新 Secret（含 JWT 密钥 + DB 凭据）
2. **核心资源** — dev 环境 `kubectl apply` Deployment；staging/release 环境 `kubectl apply` Rollout（Argo Rollouts 金丝雀发布）+ canary Service + AnalysisTemplate
3. **辅助资源** — `kubectl apply` HPA、PDB、NetworkPolicy、ServiceAccount
4. **等待就绪** — dev 用 `kubectl wait`；staging/release 用 `kubectl argo rollouts status`（Flyway 在应用启动时自动执行迁移）

#### status — 状态检查

显示 Namespace、Pods、Services、Ingress 状态，并通过 `/actuator/health` 端点检查应用健康。

### 6.3 环境差异

| 配置项 | dev | staging | release |
|--------|-----|---------|---------|
| `K8S_NAMESPACE` | `zhiyu-dev` | `zhiyu-staging` | `zhiyu` |
| `APP_REPLICAS` | 1 | 2 | 4 |
| MySQL | K8s StatefulSet (5Gi) | 外部 RDS | 外部 RDS |
| Redis | K8s Deployment (1Gi) | 外部 Redis | 外部 Redis |
| Nacos | K8s Deployment (1Gi) | 外部集群 | 外部集群 |
| `HPA_MIN` / `HPA_MAX` | 1 / 4 | 2 / 8 | 2 / 12 |
| `PDB_MIN_AVAILABLE` | 1 | 1 | 2 |
| 发布策略 | Deployment 滚动更新 | Argo Rollouts 金丝雀 | Argo Rollouts 金丝雀 |
| Ingress TLS | 无 | ✓ | ✓ |
| JWT 密钥 | 本地生成 | 本地生成 | 外部管理 |

---

## 7. Ansible — 服务器批量初始化

```
用法:
  ansible-playbook -i bootstrap/ansible/inventory/dev bootstrap/ansible/site.yml
  ansible-playbook -i inventory/dev site.yml -e offline=false   # 在线模式
```

### 7.1 Role 结构

| Role | 在线 (Debian) | 在线 (RedHat) | 离线 |
|------|:------------:|:------------:|:----:|
| `common` | apt install 基础工具 | yum install 基础工具 + EPEL | — |
| `docker` | Docker APT 仓库 | Docker YUM 仓库 | static .tgz |
| `kubectl` | 官方 curl 下载 | 官方 curl 下载 | 本地二进制 |
| `jdk` | Adoptium APT 仓库 | Adoptium YUM 仓库 | tar.gz 解压 |

### 7.2 库存模板

```ini
# 开发环境
[dev]
dev-server-1 ansible_host=192.168.1.10 ansible_user=ubuntu
dev-centos-1 ansible_host=192.168.1.11 ansible_user=centos

[dev:vars]
env=dev

[all:vars]
ansible_python_interpreter=/usr/bin/python3
```

---

## 8. 离线部署完整流程

### Step 1: 在联网机器上下载离线包

```bash
cd zhiyu-backend

# 下载当前平台的包（自动检测 OS/Arch）
./bootstrap/download-packages.sh

# 或下载全部平台（跨平台部署用）
./bootstrap/download-packages.sh all

# 或指定平台
./bootstrap/download-packages.sh linux      # Ubuntu/Debian
./bootstrap/download-packages.sh centos     # CentOS 7
```

### Step 2: 打包传输

```bash
tar -czf bootstrap-offline.tar.gz bootstrap/
scp bootstrap-offline.tar.gz user@target:/tmp/
```

### Step 3: 离线安装系统依赖

```bash
ssh user@target 'cd /path/to/zhiyu-backend && tar -xzf /tmp/bootstrap-offline.tar.gz'

# 完整安装（默认离线）
ssh user@target 'cd /path/to/zhiyu-backend && ./bootstrap/bootstrap.sh'

# 仅安装 Docker + kubectl
ssh user@target 'cd /path/to/zhiyu-backend && ./bootstrap/bootstrap.sh --docker-only'

# 预检查（不实际安装）
ssh user@target 'cd /path/to/zhiyu-backend && ./bootstrap/bootstrap.sh --dry-run'
```

### Step 4: 部署应用

```bash
# 检查前置条件
./deploy/deploy.sh dev check

# 部署基础设施
./deploy/deploy.sh dev infra

# 初始化数据和配置
./deploy/deploy.sh dev init

# 构建并推送镜像
./deploy/deploy.sh dev build

# 部署应用
./deploy/deploy.sh dev deploy

# 检查状态
./deploy/deploy.sh dev status

# 或一步到位
./deploy/deploy.sh dev all
```

### 离线部署检查清单

- [ ] `bootstrap/packages/jdk/` — 目标 OS/Arch 的 JDK tar.gz
- [ ] `bootstrap/packages/maven/` — Maven tar.gz + `maven-offline-repo.tar.gz`
- [ ] `bootstrap/packages/docker/` — Docker static .tgz（目标 Arch）
- [ ] `bootstrap/packages/kubectl/` — 目标平台的 kubectl 二进制
- [ ] `bootstrap/packages/images/` — 6 个 Docker 镜像 tar
- [ ] `bootstrap/packages/SHA256SUMS` — 校验清单已生成且验证通过
- [ ] 目标机器 `kubectl` 已连接到 K8s 集群
- [ ] 目标机器有足够的磁盘空间（建议 ≥10GB 空闲）

---

## 9. 环境变量参考

### 9.1 环境文件 (`deploy/envs/<env>.env`)

```bash
# ── K8s ───────────────────────────
K8S_NAMESPACE="zhiyu-dev"
APP_REPLICAS=1
APP_CPU_REQUEST="250m"
APP_CPU_LIMIT="500m"
APP_MEM_REQUEST="256Mi"
APP_MEM_LIMIT="512Mi"

# ── 镜像仓库 ───────────────────────
DOCKER_REGISTRY="registry.example.com"
DOCKER_IMAGE="zhiyu-backend"
DOCKER_TAG="dev-$(date +%Y%m%d-%H%M%S)"

# ── 基础设施存储（空 = 使用外部服务）─
MYSQL_STORAGE="5Gi"          # 非空 = 在 K8s 内创建 MySQL StatefulSet
REDIS_STORAGE="1Gi"          # 非空 = 在 K8s 内创建 Redis Deployment
NACOS_STORAGE="1Gi"          # 非空 = 在 K8s 内创建 Nacos Deployment

# ── 数据库 ─────────────────────────
MYSQL_HOST="mysql.${K8S_NAMESPACE}"
MYSQL_PORT="3306"
MYSQL_ROOT_PASSWORD="<auto-generated>"
MYSQL_USER="zhiyu"
MYSQL_PASSWORD="<auto-generated>"
MYSQL_DATABASE="zhiyu"

# ── Redis ──────────────────────────
REDIS_HOST="redis.${K8S_NAMESPACE}"
REDIS_PORT="6379"
REDIS_PASSWORD="<auto-generated>"   # Redis AUTH 密码

# ── Nacos ──────────────────────────
NACOS_HOST="nacos.${K8S_NAMESPACE}"
NACOS_PORT="8848"
NACOS_NAMESPACE="dev"
NACOS_USERNAME="nacos"
NACOS_PASSWORD="<auto-generated>"

# ── 应用 ───────────────────────────
SPRING_PROFILES_ACTIVE="dev"
JWT_KEY_DIR="./deploy/secrets/dev"

# ── HPA ────────────────────────────
HPA_MIN_REPLICAS="1"
HPA_MAX_REPLICAS="4"

# ── PDB ────────────────────────────
PDB_MIN_AVAILABLE="1"

# ── Ingress ────────────────────────
INGRESS_HOST="dev.zhiyu.app"
INGRESS_CLASS="nginx"
INGRESS_SSL_REDIRECT="false"
```

### 9.2 deploy.sh 使用的关键变量

| 变量 | 使用者 | 说明 |
|------|--------|------|
| `K8S_NAMESPACE` | 所有步骤 | K8s Namespace 名称 |
| `MYSQL_STORAGE` / `REDIS_STORAGE` / `NACOS_STORAGE` | `infra` | 非空触发 K8s 内部署 |
| `DOCKER_REGISTRY` / `DOCKER_IMAGE` / `DOCKER_TAG` | `build`, `deploy` | 镜像路径 |
| `JWT_KEY_DIR` | `init`, `deploy` | JWT 密钥对存储目录 |
| `HPA_MIN_REPLICAS` / `HPA_MAX_REPLICAS` | `deploy` | HPA 自动扩缩容范围 |
| `PDB_MIN_AVAILABLE` | `deploy` | PodDisruptionBudget 最小可用数 |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | `init` | Nacos 认证（init-nacos.sh 使用） |
| `INGRESS_HOST` | `deploy`, `status` | Ingress 域名 |

---

## 10. 已知限制

### Docker Desktop (macOS)

macOS 上 Docker Desktop 无法离线安装。`bootstrap.sh` 离线模式下会提示手动下载。在线模式通过 `brew install --cask docker` 安装。

### Docker 镜像架构单一路径

`docker save` 导出单架构镜像。离线部署到不同架构的机器时，需在对应架构上重新执行 `download-packages.sh` 导出镜像。

### Maven 离线仓库体积

Maven 离线仓库（`maven-offline-repo.tar.gz`）包含 Spring Boot + Spring Cloud + Spring Cloud Alibaba 全套依赖，体积约 500-800 MB。首次下载需稳定的网络连接。

### CentOS 7 ARM64

CentOS 7 无官方 ARM64 版本。如需 ARM64 Linux 离线部署，请使用 Ubuntu 22.04+ 或 Debian 12+。

### Nacos 版本兼容性

`nacos/nacos-server:v2.4.0` 已验证支持 `linux/amd64` 和 `linux/arm64`。如升级 Nacos 版本，请确认新版本的多架构支持。

### 离线 K8s 集群镜像

首次 `kubeadm init` 仍需拉取 K8s 控制平面镜像。建议使用 `kubeadm config images pull` 预拉取所需镜像。

### Flyway 多副本并发

多副本 Deployment 同时启动时，Flyway 会在每个 Pod 中执行。Flyway 10.x 内置行级锁机制，只有第一个获得锁的 Pod 执行迁移，其余等待锁释放后跳过。功能正确，仅在首次启动时有短暂延迟。

### Argo Rollouts 前置依赖

staging/release 环境的金丝雀发布需要 Argo Rollouts Controller 已部署在 K8s 集群中。安装方法：

```bash
# 在线安装
kubectl create namespace argo-rollouts
kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml

# 离线安装（bootstrap 会自动处理）
./bootstrap/download-packages.sh  # 预下载 argo-rollouts 镜像 + CLI + install manifest
./bootstrap/bootstrap.sh          # 自动部署 Argo Rollouts Controller
```

此外需要 `kubectl-argo-rollouts` CLI 插件（CI 和开发机都需要）：

```bash
# macOS
brew install argoproj/tap/kubectl-argo-rollouts

# Linux / GitHub Actions
curl -sLO https://github.com/argoproj/argo-rollouts/releases/latest/download/kubectl-argo-rollouts-linux-amd64
chmod +x kubectl-argo-rollouts-linux-amd64
sudo mv kubectl-argo-rollouts-linux-amd64 /usr/local/bin/kubectl-argo-rollouts
```

### NetworkPolicy 依赖 CNI

`NetworkPolicy` 需要 CNI 插件支持（Calico、Cilium、Weave 等）。Alibaba Cloud ACK 默认使用 Terway（兼容 NetworkPolicy），其他 K8s 发行版可能不支持。`deploy.sh` 在 NetworkPolicy apply 失败时仅警告，不中止部署。

---

## 11. 默认安装路径

### 11.1 项目目录

```
/opt/zhiyu-backend/                  # 推荐 clone 位置
├── deploy/                          # 部署清单和脚本
├── docs/                            # 设计文档
├── pom.xml                          # Maven 构建
└── ...
```

### 11.2 K8s 数据路径（kubeadm 集群）

| 路径 | 用途 |
|------|------|
| `/var/lib/kubelet/` | kubelet 配置和数据 |
| `/var/lib/containerd/` | containerd 镜像和容器存储 |
| `/var/lib/rancher/local-path-provisioner/` | 动态 PV 默认存储路径（local-path-provisioner） |
| `/etc/containerd/config.toml` | containerd 运行时配置 |
| `/etc/kubernetes/manifests/` | 静态 Pod 清单（控制平面组件） |

### 11.3 系统二进制

| 二进制 | 路径 | 来源 |
|--------|------|------|
| `kubectl` | `/usr/local/bin/kubectl` | apt / binary |
| `kubeadm` | `/usr/bin/kubeadm` | apt |
| `kubelet` | `/usr/bin/kubelet` | apt |
| `containerd` | `/usr/bin/containerd` | apt |
| `ctr` | `/usr/bin/ctr` | containerd 内置 |
| `docker` | `/usr/local/bin/docker` | static .tgz |
| `envsubst` | `/usr/bin/envsubst` | apt (gettext-base) |

### 11.4 K8s 持久卷（PVC）

| PVC | Namespace | 物理路径 |
|-----|-----------|---------|
| `mysql-0-data-0` | `zhiyu-dev` | `/var/lib/rancher/local-path-provisioner/pvc-*` |
| `redis-data-redis-0` | `zhiyu-dev` | 同上 |
| `prometheus-data-prometheus-0` | `monitoring` | 同上 |
| `grafana-data` | `monitoring` | 同上 |

### 11.5 JWT 密钥

```
deploy/secrets/<env>/
├── jwt-private.pem                  # RS256 私钥
└── jwt-public.pem                   # RS256 公钥
```

生成：`./deploy/deploy.sh <env> init`

---

## 相关文档

| 文档 | 内容 |
|------|------|
| [ARCHITECTURE.md §5](ARCHITECTURE.md#5-部署架构) | 部署拓扑和基础设施决策 |
| [CI-CD.md](CI-CD.md) | GitHub Actions 流水线和 K8s 部署清单 |
| [INFRASTRUCTURE.md](INFRASTRUCTURE.md) | Nacos 配置、Redis 键设计、MySQL Schema |
| [OPS.md](OPS.md) | SLO/SLI、Grafana 面板、告警、灾备 |
| [DEVELOPMENT-STANDARDS.md](DEVELOPMENT-STANDARDS.md) | 编码规范、K8s 本地开发指南 |
| `bootstrap/packages/README.md` | 离线包目录说明和下载命令 |
