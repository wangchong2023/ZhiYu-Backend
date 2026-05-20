# 离线安装包归档

本目录存放 ZhiYu-Backend 所需的全部系统依赖安装包。按 **OS → CPU 架构 → 软件功能** 分层管理。

## 目录结构

```
packages/
├── darwin/                    # macOS
│   ├── amd64/                 # Intel Mac
│   │   ├── jdk/               # JDK 21 (Eclipse Temurin) .tar.gz
│   │   ├── kubectl/           # kubectl 二进制
│   │   └── argo-rollouts/     # Argo Rollouts CLI
│   └── arm64/                 # Apple Silicon
│       ├── jdk/
│       ├── kubectl/
│       └── argo-rollouts/
├── linux/                     # Linux
│   ├── amd64/                 # x86_64
│   │   ├── jdk/
│   │   ├── kubectl/
│   │   ├── argo-rollouts/
│   │   └── docker/            # Docker Static 二进制 .tgz
│   ├── arm64/                 # aarch64
│   │   ├── jdk/
│   │   ├── kubectl/
│   │   ├── argo-rollouts/
│   │   └── docker/
│   ├── deb/                   # Docker .deb（Ubuntu/Debian 在线安装备用）
│   └── rpm/                   # Docker .rpm（CentOS 7 在线安装备用）
├── common/                    # 跨平台共享
│   ├── docker-gpg             # Docker APT/RPM GPG 公钥
│   ├── argo-rollouts-install.yaml  # Argo Rollouts Controller 安装清单
│   └── images/                # Docker 镜像 tar（多架构兼容）
│       ├── redis-7-alpine.tar
│       ├── nacos-server-v2.4.0.tar
│       ├── mysql-8.0.tar
│       ├── argo-rollouts-latest.tar
│       ├── eclipse-temurin-21-jdk-alpine.tar
│       ├── eclipse-temurin-21-jre-alpine.tar
│       ├── prometheus-v3.7.0.tar
│       ├── grafana-v11.6.0.tar
│       ├── node-exporter-v1.9.0.tar
│       ├── kube-state-metrics-v2.15.0.tar
│       └── metrics-server-v0.7.2.tar
└── SHA256SUMS                 # 所有文件 SHA256 校验
```

## 离线部署流程

### 1. 在联网机器上：下载安装包

```bash
cd ZhiYu-Backend

# 下载当前平台的包
./bootstrap/download-packages.sh

# 下载指定平台
./bootstrap/download-packages.sh linux      # Ubuntu/Debian (含 .deb)
./bootstrap/download-packages.sh centos     # CentOS 7 (含 .rpm)

# 下载全部平台（跨平台部署用）
./bootstrap/download-packages.sh all

# 校验已下载的包
./bootstrap/download-packages.sh verify
```

### 2. 打包传输到目标机器

```bash
# 打包整个 bootstrap 目录
tar -czf bootstrap-packages.tar.gz bootstrap/

# 传输到目标机器
scp bootstrap-packages.tar.gz user@target-host:/tmp/

# 在目标机器上解压
ssh user@target-host 'cd /path/to/ZhiYu-Backend && tar -xzf /tmp/bootstrap-packages.tar.gz'
```

### 3. 在目标机器上：离线安装

```bash
# 默认离线模式 — 从 packages/ 安装所有系统依赖
./bootstrap/bootstrap.sh

# 仅安装 Docker + kubectl
./bootstrap/bootstrap.sh --docker-only

# 模拟运行（查看将执行的操作）
./bootstrap/bootstrap.sh --dry-run

# 在线模式（备选 — 从网络下载）
./bootstrap/bootstrap.sh --online
```

### 4. 一键部署应用

```bash
./deploy/deploy.sh dev all
```

## 版本信息

| 组件 | 版本 |
|------|------|
| JDK | 21.0.9+10 (Eclipse Temurin) |
| kubectl | v1.31.0 |
| Docker | 29.5.1 (Static 二进制，所有 Linux 通用) |
| Argo Rollouts CLI | v1.7.2 |
| Redis (镜像) | 7-alpine |
| Nacos (镜像) | v2.4.0 |
| MySQL (镜像) | 8.0 |
| Prometheus (镜像) | v3.7.0 |
| Grafana (镜像) | 11.6.0 |
| Node Exporter (镜像) | v1.9.0 |
| kube-state-metrics (镜像) | v2.15.0 |
| metrics-server (镜像) | v0.7.2 |

版本定义在 `download-packages.sh` 顶部，可按需修改后重新下载。
