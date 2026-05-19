# 离线安装包归档

本目录存放 ZhiYu-Backend 所需的全部系统依赖安装包。按类别分目录管理。

## 目录结构

```
packages/
├── jdk/           # JDK 21 (Eclipse Temurin) .tar.gz
├── docker/        # Docker Static 二进制 .tgz（所有 Linux 通用）
├── kubectl/       # kubectl 二进制文件
├── images/        # Docker 镜像 tar（Redis, Nacos, MySQL）
└── SHA256SUMS     # 所有文件 SHA256 校验
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
ssh user@target-host 'cd /path/to/zhiyu-backend && tar -xzf /tmp/bootstrap-packages.tar.gz'
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
| Redis (镜像) | 7-alpine |
| Nacos (镜像) | v2.4.0 |
| MySQL (镜像) | 8.0 |

版本定义在 `download-packages.sh` 顶部，可按需修改后重新下载。
