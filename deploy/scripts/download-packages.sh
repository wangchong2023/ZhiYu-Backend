#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: download-packages.sh
# 脚本功能: 离线安装包下载与分类归档脚本。
#           负责在有网环境中一键下载各平台所需的 JDK、Docker、kubectl、Argo Rollouts CLI
#           以及项目依赖的容器镜像，并按操作系统和架构分类归档至 packages/目录下。
#           下载完成后可将 packages/ 目录打包拟利用到离线环境中，由 os-init.sh 进行离线安装。
# 编 写 人: 资深架构师 & 高级开发工程师 (Antigravity AI)
# 编写时间: 2026-05-20
# 调用方式:
#   ./deploy/scripts/download-packages.sh              # 下载当前操作系统对应平台的包
#   ./deploy/scripts/download-packages.sh all          # 下载全平台（macOS + Linux x64/arm64）的所有包
#   ./deploy/scripts/download-packages.sh verify       # 校验已下载包的 SHA256 完整性
# 下载目录结构:
#   deploy/packages/
#   ├── darwin/
#   │   ├── arm64/{jdk, kubectl, argo-rollouts}  # macOS Apple Silicon
#   │   └── amd64/{jdk, kubectl, argo-rollouts}  # macOS Intel
#   ├── linux/
#   │   ├── amd64/{jdk, kubectl, argo-rollouts, docker}  # Linux x86_64
#   │   └── arm64/{jdk, kubectl, argo-rollouts, docker}  # Linux ARM64
#   └── common/images/     # Docker 镜像 tar 卡文件（经济批量拉取，只拉一次）
# 依赖工具: curl 或 aria2c（多线程加速）、docker（镜像导出）、shasum/sha256sum（校验）
# 注意事项: 需要在联网环境下执行，离线环境请先在联网机运行后将 packages/ 拷贝到离线节点
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PACKAGES_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)/packages"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()  { echo -e "\n${CYAN}━━━ $* ━━━${NC}"; }

OS="$(uname -s)"
ARCH="$(uname -m)"

# ── 版本定义 ──────────────────────────────────────────────────
JDK_MAJOR="21"
JDK_VERSION="${JDK_MAJOR}.0.9"
JDK_BUILD="10"
TEMURIN_BASE="https://github.com/adoptium/temurin${JDK_MAJOR}-binaries/releases/download/jdk-${JDK_VERSION}%2B${JDK_BUILD}"

KUBECTL_VERSION="v1.31.0"
KUBECTL_BASE="https://dl.k8s.io/release/${KUBECTL_VERSION}"

DOCKER_STATIC_VERSION="29.5.1"
DOCKER_STATIC_BASE="https://download.docker.com/linux/static/stable"

# Docker 镜像版本（与 deploy/infra/*.yaml 保持一致）
REDIS_IMAGE="redis:7-alpine"
NACOS_IMAGE="nacos/nacos-server:v2.4.0"
MYSQL_IMAGE="mysql:8.0"
ARGO_ROLLOUTS_IMAGE="quay.io/argoproj/argo-rollouts:latest"

# 监控镜像版本（与 deploy/monitoring/*.yaml 保持一致）
PROMETHEUS_IMAGE="${PROMETHEUS_IMAGE:-prom/prometheus:v3.7.0}"
GRAFANA_IMAGE="${GRAFANA_IMAGE:-grafana/grafana:11.6.0}"
NODE_EXPORTER_IMAGE="${NODE_EXPORTER_IMAGE:-prom/node-exporter:v1.9.0}"
KUBE_STATE_METRICS_IMAGE="${KUBE_STATE_METRICS_IMAGE:-registry.k8s.io/kube-state-metrics/kube-state-metrics:v2.15.0}"

# Argo Rollouts CLI 版本
ARGO_ROLLOUTS_VERSION="v1.7.2"

# ── 创建分类目录 ──────────────────────────────────────────────
mkdir -p "${PACKAGES_DIR}"/{darwin/{amd64,arm64}/{jdk,kubectl,argo-rollouts},linux/{amd64,arm64}/{jdk,kubectl,argo-rollouts,docker},common/images}

# ── 下载工具 ──────────────────────────────────────────────────
download() {
  local url="$1"
  local dest="$2"
  local label="${3:-$(basename "$dest")}"

  if [ -f "$dest" ]; then
    log_info "  ✓ 已存在: $label"
    return 0
  fi

  log_info "  ↓ 下载: $label"
  if command -v aria2c &>/dev/null; then
    aria2c -q -x4 -s4 -d "$(dirname "$dest")" -o "$(basename "$dest")" "$url"
  else
    curl -fSL --progress-bar -o "$dest" "$url"
  fi
  echo ""
}

# ═══════════════════════════════════════════════════════════════
# JDK 21 — Eclipse Temurin
# ═══════════════════════════════════════════════════════════════
download_jdk() {
  log_step "JDK ${JDK_MAJOR} (Eclipse Temurin)"

  local platform subdir
  case "$1" in
    macos-arm64) platform="aarch64_mac";     subdir="darwin/arm64" ;;
    macos-x64)   platform="x64_mac";         subdir="darwin/amd64" ;;
    linux-x64)   platform="x64_linux";       subdir="linux/amd64" ;;
    linux-arm64) platform="aarch64_linux";   subdir="linux/arm64" ;;
    *) log_error "未知平台: $1"; return 1 ;;
  esac

  local filename="OpenJDK${JDK_MAJOR}U-jdk_${platform}_hotspot_${JDK_VERSION}_${JDK_BUILD}.tar.gz"
  download "${TEMURIN_BASE}/${filename}" "${PACKAGES_DIR}/${subdir}/jdk/${filename}" "JDK ($1)"
}

# ═══════════════════════════════════════════════════════════════
# kubectl
# ═══════════════════════════════════════════════════════════════
download_kubectl() {
  log_step "kubectl ${KUBECTL_VERSION}"

  case "$1" in
    macos-arm64) download "${KUBECTL_BASE}/bin/darwin/arm64/kubectl" "${PACKAGES_DIR}/darwin/arm64/kubectl/kubectl-darwin-arm64" "kubectl";;
    macos-x64)   download "${KUBECTL_BASE}/bin/darwin/amd64/kubectl" "${PACKAGES_DIR}/darwin/amd64/kubectl/kubectl-darwin-amd64" "kubectl";;
    linux-x64)   download "${KUBECTL_BASE}/bin/linux/amd64/kubectl"   "${PACKAGES_DIR}/linux/amd64/kubectl/kubectl-linux-amd64"   "kubectl";;
    linux-arm64) download "${KUBECTL_BASE}/bin/linux/arm64/kubectl"  "${PACKAGES_DIR}/linux/arm64/kubectl/kubectl-linux-arm64"  "kubectl";;
  esac
}

# ═══════════════════════════════════════════════════════════════
# Docker Static 二进制（所有 Linux 发行版通用，离线优先）
# ═══════════════════════════════════════════════════════════════
download_docker_static() {
  local arch="$1"  # x86_64 or aarch64
  local subdir
  case "$arch" in
    x86_64)  subdir="amd64" ;;
    aarch64) subdir="arm64" ;;
  esac
  log_step "Docker Static ${DOCKER_STATIC_VERSION} (${arch})"
  local filename="docker-${DOCKER_STATIC_VERSION}-${arch}.tgz"
  download "${DOCKER_STATIC_BASE}/${arch}/docker-${DOCKER_STATIC_VERSION}.tgz" \
           "${PACKAGES_DIR}/linux/${subdir}/docker/${filename}" \
           "Docker (${arch})"
}

# ═══════════════════════════════════════════════════════════════
# Docker .deb (Ubuntu/Debian 在线安装备用)
# ═══════════════════════════════════════════════════════════════
download_docker_deb() {
  log_step "Docker .deb 包 (Ubuntu/Debian)"

  if command -v apt-get &>/dev/null; then
    mkdir -p "${PACKAGES_DIR}/linux/deb"
    log_info "  下载 Docker .deb 依赖包..."
    (
      cd "${PACKAGES_DIR}/linux/deb"
      apt-get download docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin 2>/dev/null || \
        log_warn "  apt-get download 失败，请在联网 Ubuntu 上运行此脚本"
    )
  else
    log_warn "  当前非 Ubuntu 环境，跳过 Docker .deb 下载"
    log_info "  离线部署推荐使用 Docker static 二进制: ./download-packages.sh all"
  fi
}

# ═══════════════════════════════════════════════════════════════
# Docker .rpm (CentOS 7 在线安装备用)
# ═══════════════════════════════════════════════════════════════
download_docker_rpm() {
  log_step "Docker .rpm 包 (CentOS 7)"

  if command -v yum &>/dev/null; then
    mkdir -p "${PACKAGES_DIR}/linux/rpm"
    log_info "  安装 yum-utils..."
    sudo yum install -y yum-utils 2>/dev/null || true
    sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo 2>/dev/null || true
    log_info "  下载 Docker RPM 包..."
    (
      cd "${PACKAGES_DIR}/linux/rpm"
      yumdownloader --resolve docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin 2>/dev/null || \
        log_warn "  yumdownloader 下载失败，请在 CentOS 7 联网机器上运行此脚本"
    )
  else
    log_warn "  当前非 CentOS 环境，跳过 Docker RPM 下载"
    log_info "  离线部署推荐使用 Docker static 二进制: ./download-packages.sh all"
  fi
}

# ═══════════════════════════════════════════════════════════════
# Argo Rollouts CLI + Install Manifest
# ═══════════════════════════════════════════════════════════════
download_argo_rollouts() {
  log_step "Argo Rollouts CLI ${ARGO_ROLLOUTS_VERSION}"

  local argo_base="https://github.com/argoproj/argo-rollouts/releases/download/${ARGO_ROLLOUTS_VERSION}"

  case "$1" in
    macos-arm64)
      download "${argo_base}/kubectl-argo-rollouts-darwin-arm64" \
        "${PACKAGES_DIR}/darwin/arm64/argo-rollouts/kubectl-argo-rollouts-darwin-arm64" "argo-rollouts CLI"
      ;;
    macos-x64)
      download "${argo_base}/kubectl-argo-rollouts-darwin-amd64" \
        "${PACKAGES_DIR}/darwin/amd64/argo-rollouts/kubectl-argo-rollouts-darwin-amd64" "argo-rollouts CLI"
      ;;
    linux-x64)
      download "${argo_base}/kubectl-argo-rollouts-linux-amd64" \
        "${PACKAGES_DIR}/linux/amd64/argo-rollouts/kubectl-argo-rollouts-linux-amd64" "argo-rollouts CLI"
      ;;
    linux-arm64)
      download "${argo_base}/kubectl-argo-rollouts-linux-arm64" \
        "${PACKAGES_DIR}/linux/arm64/argo-rollouts/kubectl-argo-rollouts-linux-arm64" "argo-rollouts CLI"
      ;;
  esac

  # 下载 install manifest（用于离线部署 Argo Rollouts controller）
  local manifest_file="${PACKAGES_DIR}/common/argo-rollouts-install.yaml"
  if [ ! -f "$manifest_file" ]; then
    log_info "  ↓ 下载: Argo Rollouts install manifest"
    curl -fsSL -o "$manifest_file" \
      "https://github.com/argoproj/argo-rollouts/releases/download/${ARGO_ROLLOUTS_VERSION}/install.yaml"
  else
    log_info "  ✓ 已存在: argo-rollouts-install.yaml"
  fi
}

# ═══════════════════════════════════════════════════════════════
# Docker 镜像导出（离线部署用）
# ═══════════════════════════════════════════════════════════════
download_docker_images() {
  log_step "Docker 镜像导出"

  if ! command -v docker &>/dev/null; then
    log_warn "  Docker 未安装，跳过镜像下载"
    log_info "  请在已安装 Docker 的联网机器上运行此脚本"
    return 0
  fi

  save_image() {
    local image="$1"
    local output="$2"
    if [ -f "${PACKAGES_DIR}/common/images/${output}" ]; then
      log_info "  ✓ 已存在: ${output}"
      return 0
    fi
    log_info "  ↓ 拉取: ${image}"
    docker pull "$image"
    log_info "  ↓ 导出: ${output}"
    docker save -o "${PACKAGES_DIR}/common/images/${output}" "$image"
  }

  save_image "$REDIS_IMAGE" "redis-7-alpine.tar"
  save_image "$NACOS_IMAGE" "nacos-server-v2.4.0.tar"
  save_image "$MYSQL_IMAGE" "mysql-8.0.tar"
  save_image "$ARGO_ROLLOUTS_IMAGE" "argo-rollouts-latest.tar"

  # Docker 构建基础镜像（Dockerfile FROM）
  save_image "eclipse-temurin:21-jdk-alpine" "eclipse-temurin-21-jdk-alpine.tar"
  save_image "eclipse-temurin:21-jre-alpine" "eclipse-temurin-21-jre-alpine.tar"

  # 监控镜像（Prometheus + Grafana + exporters）
  save_image "$PROMETHEUS_IMAGE" "prometheus-v3.7.0.tar"
  save_image "$GRAFANA_IMAGE" "grafana-v11.6.0.tar"
  save_image "$NODE_EXPORTER_IMAGE" "node-exporter-v1.9.0.tar"
  save_image "$KUBE_STATE_METRICS_IMAGE" "kube-state-metrics-v2.15.0.tar"

  log_info "镜像导出完成 (images/)"
}

# ═══════════════════════════════════════════════════════════════
# 生成校验清单 & 统计
# ═══════════════════════════════════════════════════════════════
generate_checksums() {
  log_step "生成 SHA256 校验清单"

  cd "$PACKAGES_DIR"
  find . -type f -not -name "SHA256SUMS" -not -name "*.md" | sort | while read -r f; do
    if command -v shasum &>/dev/null; then
      shasum -a 256 "$f"
    else
      sha256sum "$f"
    fi
  done > SHA256SUMS

  log_info "校验清单: ${PACKAGES_DIR}/SHA256SUMS"
}

print_summary() {
  echo ""
  echo "================================================"
  echo " 离线安装包归档完成"
  echo "================================================"
  echo ""
  echo " 分类目录:"
  for dir in darwin/amd64 darwin/arm64 linux/amd64 linux/arm64 common; do
    local count=$(find "${PACKAGES_DIR}/${dir}" -type f 2>/dev/null | wc -l | tr -d ' ')
    local size=$(du -sh "${PACKAGES_DIR}/${dir}" 2>/dev/null | cut -f1 || echo "0")
    printf "   %-20s %2s 个文件  %s\n" "$dir/" "$count" "$size"
  done
  echo ""
  echo " 总大小: $(du -sh "$PACKAGES_DIR" | cut -f1)"
  echo ""
  echo " 离线部署流程:"
  echo "   1. tar -czf bootstrap-packages.tar.gz bootstrap/"
  echo "   2. 复制 tar.gz 到目标机器"
  echo "   3. tar -xzf bootstrap-packages.tar.gz"
  echo "   4. ./bootstrap/bootstrap.sh               # 安装系统依赖"
  echo "   5. ./deploy/deploy.sh dev all             # 部署应用"
}

# ═══════════════════════════════════════════════════════════════
# 校验已下载的包
# ═══════════════════════════════════════════════════════════════
do_verify() {
  log_step "校验已下载的安装包"
  cd "$PACKAGES_DIR"
  if [ -f SHA256SUMS ]; then
    if command -v shasum &>/dev/null; then
      shasum -a 256 -c SHA256SUMS --ignore-missing
    else
      sha256sum -c SHA256SUMS --ignore-missing
    fi
  else
    log_warn "SHA256SUMS 不存在，重新生成..."
    generate_checksums
  fi
}

# ═══════════════════════════════════════════════════════════════
# 按平台下载完整集合
# ═══════════════════════════════════════════════════════════════
download_platform() {
  local platform="$1"

  case "$platform" in
    macos-arm64)
      download_jdk macos-arm64
      download_kubectl macos-arm64
      download_argo_rollouts macos-arm64
      ;;
    macos-x64)
      download_jdk macos-x64
      download_kubectl macos-x64
      download_argo_rollouts macos-x64
      ;;
    linux-x64)
      download_jdk linux-x64
      download_kubectl linux-x64
      download_argo_rollouts linux-x64
      download_docker_static x86_64
      ;;
    linux-arm64)
      download_jdk linux-arm64
      download_kubectl linux-arm64
      download_argo_rollouts linux-arm64
      download_docker_static aarch64
      ;;
  esac
}

# ═══════════════════════════════════════════════════════════════
# 主入口
# ═══════════════════════════════════════════════════════════════
echo "================================================"
echo " 离线安装包下载 & 分类归档"
echo " OS/Arch: ${OS} ${ARCH}"
echo " 目标:    ${PACKAGES_DIR}"
echo "================================================"
echo ""

case "${1:-current}" in
  verify)
    do_verify
    exit 0
    ;;
  all)
    download_platform macos-arm64
    download_platform macos-x64
    download_platform linux-x64
    download_platform linux-arm64
    download_docker_images

    ;;
  centos|centos7|rhel7)
    download_jdk linux-x64
    download_kubectl linux-x64
    download_docker_static x86_64
    download_docker_images

    ;;
  macos|darwin)
    case "$ARCH" in
      arm64|aarch64) download_platform macos-arm64 ;;
      x86_64)        download_platform macos-x64 ;;
      *)             download_platform macos-arm64 macos-x64 ;;
    esac
    ;;
  linux)
    case "$ARCH" in
      x86_64|amd64) download_platform linux-x64 ;;
      arm64|aarch64) download_platform linux-arm64 ;;
      *)            download_platform linux-x64 linux-arm64 ;;
    esac
    download_docker_images

    ;;
  current|*)
    case "$OS" in
      Darwin)
        case "$ARCH" in
          arm64|aarch64) download_platform macos-arm64 ;;
          x86_64)        download_platform macos-x64 ;;
          *)             download_platform macos-arm64 ;;
        esac
        ;;
      Linux)
        case "$ARCH" in
          x86_64|amd64) download_platform linux-x64 ;;
          arm64|aarch64) download_platform linux-arm64 ;;
          *)            download_platform linux-x64 ;;
        esac
        ;;
      *) log_error "不支持的操作系统: $OS" && exit 1 ;;
    esac
    download_docker_images

    ;;
esac

generate_checksums
print_summary
