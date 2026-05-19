#!/bin/bash
# ============================================================
# 离线安装包下载 & 分类归档脚本
#
# 用法:
#   ./bootstrap/download-packages.sh              # 下载当前 OS 的包
#   ./bootstrap/download-packages.sh all          # 下载所有平台的包
#   ./bootstrap/download-packages.sh verify       # 校验已下载的包
#
# 下载后目录结构:
#   packages/
#   ├── jdk/               # JDK 21 (Temurin)
#   ├── maven/             # Maven 3.9
#   ├── docker/            # Docker .deb (Linux)
#   ├── kubectl/           # kubectl
#   ├── minikube/          # Minikube
#   ├── kind/              # Kind
#   └── SHA256SUMS         # 所有文件的校验和
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PACKAGES_DIR="${SCRIPT_DIR}/packages"

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

MAVEN_VERSION="3.9.16"
MAVEN_BASE="https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries"

KUBECTL_VERSION="v1.31.0"
KUBECTL_BASE="https://dl.k8s.io/release/${KUBECTL_VERSION}"

MINIKUBE_VERSION="v1.34.0"
MINIKUBE_BASE="https://github.com/kubernetes/minikube/releases/download/${MINIKUBE_VERSION}"

KIND_VERSION="v0.24.0"
KIND_BASE="https://github.com/kubernetes-sigs/kind/releases/download/${KIND_VERSION}"

DOCKER_STATIC_VERSION="29.5.1"
DOCKER_STATIC_BASE="https://download.docker.com/linux/static/stable"

# Docker 镜像版本（与 deploy/infra/*.yaml 保持一致）
REDIS_IMAGE="redis:7-alpine"
NACOS_IMAGE="nacos/nacos-server:v2.4.0"
MYSQL_IMAGE="mysql:8.0"
ARGO_ROLLOUTS_IMAGE="quay.io/argoproj/argo-rollouts:latest"

# Argo Rollouts CLI 版本
ARGO_ROLLOUTS_VERSION="v1.7.2"

# ── 创建分类目录 ──────────────────────────────────────────────
mkdir -p "${PACKAGES_DIR}"/{jdk,maven,docker,kubectl,minikube,kind,images}

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

  local platform
  case "$1" in
    macos-arm64) platform="aarch64_mac" ;;
    macos-x64)   platform="x64_mac" ;;
    linux-x64)   platform="x64_linux" ;;
    linux-arm64) platform="aarch64_linux" ;;
    *) log_error "未知平台: $1"; return 1 ;;
  esac

  local filename="OpenJDK${JDK_MAJOR}U-jdk_${platform}_hotspot_${JDK_VERSION}_${JDK_BUILD}.tar.gz"
  download "${TEMURIN_BASE}/${filename}" "${PACKAGES_DIR}/jdk/${filename}" "JDK ($1)"
}

# ═══════════════════════════════════════════════════════════════
# Maven
# ═══════════════════════════════════════════════════════════════
download_maven() {
  log_step "Maven ${MAVEN_VERSION}"
  local filename="apache-maven-${MAVEN_VERSION}-bin.tar.gz"
  download "${MAVEN_BASE}/${filename}" "${PACKAGES_DIR}/maven/${filename}" "Maven"
}

# ═══════════════════════════════════════════════════════════════
# kubectl
# ═══════════════════════════════════════════════════════════════
download_kubectl() {
  log_step "kubectl ${KUBECTL_VERSION}"

  case "$1" in
    macos-arm64) download "${KUBECTL_BASE}/bin/darwin/arm64/kubectl" "${PACKAGES_DIR}/kubectl/kubectl-darwin-arm64" "kubectl";;
    macos-x64)   download "${KUBECTL_BASE}/bin/darwin/amd64/kubectl" "${PACKAGES_DIR}/kubectl/kubectl-darwin-amd64" "kubectl";;
    linux-x64)   download "${KUBECTL_BASE}/bin/linux/amd64/kubectl"   "${PACKAGES_DIR}/kubectl/kubectl-linux-amd64"   "kubectl";;
    linux-arm64) download "${KUBECTL_BASE}/bin/linux/arm64/kubectl"  "${PACKAGES_DIR}/kubectl/kubectl-linux-arm64"  "kubectl";;
  esac
}

# ═══════════════════════════════════════════════════════════════
# Minikube
# ═══════════════════════════════════════════════════════════════
download_minikube() {
  log_step "Minikube ${MINIKUBE_VERSION}"

  case "$1" in
    macos-arm64) download "${MINIKUBE_BASE}/minikube-darwin-arm64" "${PACKAGES_DIR}/minikube/minikube-darwin-arm64" "Minikube";;
    macos-x64)   download "${MINIKUBE_BASE}/minikube-darwin-amd64" "${PACKAGES_DIR}/minikube/minikube-darwin-amd64" "Minikube";;
    linux-x64)   download "${MINIKUBE_BASE}/minikube-linux-amd64"   "${PACKAGES_DIR}/minikube/minikube-linux-amd64"   "Minikube";;
    linux-arm64) download "${MINIKUBE_BASE}/minikube-linux-arm64"  "${PACKAGES_DIR}/minikube/minikube-linux-arm64"  "Minikube";;
  esac
}

# ═══════════════════════════════════════════════════════════════
# Kind
# ═══════════════════════════════════════════════════════════════
download_kind() {
  log_step "Kind ${KIND_VERSION}"

  case "$1" in
    macos-arm64) download "${KIND_BASE}/kind-darwin-arm64" "${PACKAGES_DIR}/kind/kind-darwin-arm64" "Kind";;
    macos-x64)   download "${KIND_BASE}/kind-darwin-amd64" "${PACKAGES_DIR}/kind/kind-darwin-amd64" "Kind";;
    linux-x64)   download "${KIND_BASE}/kind-linux-amd64"   "${PACKAGES_DIR}/kind/kind-linux-amd64"   "Kind";;
    linux-arm64) download "${KIND_BASE}/kind-linux-arm64"  "${PACKAGES_DIR}/kind/kind-linux-arm64"  "Kind";;
  esac
}

# ═══════════════════════════════════════════════════════════════
# Docker Static 二进制（所有 Linux 发行版通用，离线优先）
# ═══════════════════════════════════════════════════════════════
download_docker_static() {
  local arch="$1"  # x86_64 or aarch64
  log_step "Docker Static ${DOCKER_STATIC_VERSION} (${arch})"
  local filename="docker-${DOCKER_STATIC_VERSION}-${arch}.tgz"
  download "${DOCKER_STATIC_BASE}/${arch}/docker-${DOCKER_STATIC_VERSION}.tgz" \
           "${PACKAGES_DIR}/docker/${filename}" \
           "Docker (${arch})"
}

# ═══════════════════════════════════════════════════════════════
# Docker .deb (Ubuntu/Debian 在线安装备用)
# ═══════════════════════════════════════════════════════════════
download_docker_deb() {
  log_step "Docker .deb 包 (Ubuntu/Debian)"

  if command -v apt-get &>/dev/null; then
    log_info "  下载 Docker .deb 依赖包..."
    (
      cd "${PACKAGES_DIR}/docker"
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
    log_info "  安装 yum-utils..."
    sudo yum install -y yum-utils 2>/dev/null || true
    sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo 2>/dev/null || true
    log_info "  下载 Docker RPM 包..."
    (
      cd "${PACKAGES_DIR}/docker"
      yumdownloader --resolve docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin 2>/dev/null || \
        log_warn "  yumdownloader 下载失败，请在 CentOS 7 联网机器上运行此脚本"
    )
  else
    log_warn "  当前非 CentOS 环境，跳过 Docker RPM 下载"
    log_info "  离线部署推荐使用 Docker static 二进制: ./download-packages.sh all"
  fi
}

# ═══════════════════════════════════════════════════════════════
# Maven 离线仓库（离线编译必需）
# ═══════════════════════════════════════════════════════════════
prepare_maven_offline() {
  log_step "Maven 离线仓库准备"

  local project_dir
  project_dir="$(cd "$SCRIPT_DIR/.." && pwd)"
  local temp_m2="/tmp/m2-offline-$$"
  local output="${PACKAGES_DIR}/maven/maven-offline-repo.tar.gz"

  if [ -f "$output" ]; then
    log_info "  ✓ 已存在: maven-offline-repo.tar.gz"
    return 0
  fi

  if [ ! -f "$project_dir/pom.xml" ]; then
    log_warn "  pom.xml 未找到 (${project_dir})，跳过 Maven 离线仓库"
    return 0
  fi

  log_info "  下载所有 Maven 依赖到临时仓库..."
  (
    cd "$project_dir"
    if [ -f "./mvnw" ]; then
      ./mvnw dependency:go-offline -Dmaven.repo.local="$temp_m2" -pl zhiyu-server -am -B -q 2>&1
    elif command -v mvn &>/dev/null; then
      mvn dependency:go-offline -Dmaven.repo.local="$temp_m2" -pl zhiyu-server -am -B -q 2>&1
    else
      log_warn "  Maven 未安装，无法准备离线仓库"
      return 0
    fi
  ) || {
    log_warn "  Maven 依赖下载可能不完整 (部分插件需要网络)"
  }

  if [ -d "$temp_m2" ] && [ "$(ls -A "$temp_m2" 2>/dev/null)" ]; then
    log_info "  打包离线仓库..."
    tar czf "$output" -C "$temp_m2" .
    rm -rf "$temp_m2"
    log_info "  ✓ maven-offline-repo.tar.gz ($(du -sh "$output" | cut -f1))"
  else
    log_warn "  ✗ Maven 离线仓库为空，请检查网络连接"
    rm -rf "$temp_m2"
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
        "${PACKAGES_DIR}/kubectl/kubectl-argo-rollouts-darwin-arm64" "argo-rollouts CLI"
      ;;
    macos-x64)
      download "${argo_base}/kubectl-argo-rollouts-darwin-amd64" \
        "${PACKAGES_DIR}/kubectl/kubectl-argo-rollouts-darwin-amd64" "argo-rollouts CLI"
      ;;
    linux-x64)
      download "${argo_base}/kubectl-argo-rollouts-linux-amd64" \
        "${PACKAGES_DIR}/kubectl/kubectl-argo-rollouts-linux-amd64" "argo-rollouts CLI"
      ;;
    linux-arm64)
      download "${argo_base}/kubectl-argo-rollouts-linux-arm64" \
        "${PACKAGES_DIR}/kubectl/kubectl-argo-rollouts-linux-arm64" "argo-rollouts CLI"
      ;;
  esac

  # 下载 install manifest（用于离线部署 Argo Rollouts controller）
  local manifest_file="${PACKAGES_DIR}/images/argo-rollouts-install.yaml"
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
    if [ -f "${PACKAGES_DIR}/images/${output}" ]; then
      log_info "  ✓ 已存在: ${output}"
      return 0
    fi
    log_info "  ↓ 拉取: ${image}"
    docker pull "$image"
    log_info "  ↓ 导出: ${output}"
    docker save -o "${PACKAGES_DIR}/images/${output}" "$image"
  }

  save_image "$REDIS_IMAGE" "redis-7-alpine.tar"
  save_image "$NACOS_IMAGE" "nacos-server-v2.4.0.tar"
  save_image "$MYSQL_IMAGE" "mysql-8.0.tar"
  save_image "$ARGO_ROLLOUTS_IMAGE" "argo-rollouts-latest.tar"

  # Docker 构建基础镜像（Dockerfile FROM）
  save_image "eclipse-temurin:21-jdk-alpine" "eclipse-temurin-21-jdk-alpine.tar"
  save_image "eclipse-temurin:21-jre-alpine" "eclipse-temurin-21-jre-alpine.tar"

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
  for dir in jdk maven docker kubectl minikube kind images; do
    local count=$(find "${PACKAGES_DIR}/${dir}" -type f 2>/dev/null | wc -l | tr -d ' ')
    local size=$(du -sh "${PACKAGES_DIR}/${dir}" 2>/dev/null | cut -f1 || echo "0")
    printf "   %-12s %2s 个文件  %s\n" "$dir/" "$count" "$size"
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
      download_minikube macos-arm64
      download_kind macos-arm64
      download_argo_rollouts macos-arm64
      ;;
    macos-x64)
      download_jdk macos-x64
      download_kubectl macos-x64
      download_minikube macos-x64
      download_kind macos-x64
      download_argo_rollouts macos-x64
      ;;
    linux-x64)
      download_jdk linux-x64
      download_kubectl linux-x64
      download_minikube linux-x64
      download_kind linux-x64
      download_argo_rollouts linux-x64
      download_docker_static x86_64
      ;;
    linux-arm64)
      download_jdk linux-arm64
      download_kubectl linux-arm64
      download_minikube linux-arm64
      download_kind linux-arm64
      download_argo_rollouts linux-arm64
      download_docker_static aarch64
      ;;
  esac
  download_maven
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
    prepare_maven_offline
    ;;
  centos|centos7|rhel7)
    download_jdk linux-x64
    download_kubectl linux-x64
    download_minikube linux-x64
    download_kind linux-x64
    download_maven
    download_docker_static x86_64
    download_docker_images
    prepare_maven_offline
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
    prepare_maven_offline
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
    prepare_maven_offline
    ;;
esac

generate_checksums
print_summary
