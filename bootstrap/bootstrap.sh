#!/bin/bash
# ============================================================
# ZhiYu-Backend 环境初始化脚本
# 在全新机器上安装所有开发/部署所需的系统依赖
#
# 用法:
#   ./bootstrap/bootstrap.sh              # 离线安装（默认，使用 packages/ 缓存）
#   ./bootstrap/bootstrap.sh --online     # 在线安装（从网络下载）
#   ./bootstrap/bootstrap.sh --dry-run    # 仅检查，不安装
#   ./bootstrap/bootstrap.sh --docker-only # 仅安装 Docker + kubectl
#
# 支持: macOS (brew) / Ubuntu 22.04+ / Debian 12+
#
# 离线流程:
#   1. 在联网机器上: ./bootstrap/download-packages.sh
#   2. 将整个 bootstrap/ 目录复制到目标机器
#   3. 在目标机器上: ./bootstrap/bootstrap.sh
# ============================================================
set -euo pipefail

# ── 颜色 ──────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()  { echo -e "\n${CYAN}━━━ $* ━━━${NC}"; }

# ── 参数 ──────────────────────────────────────────────────────
MODE="offline"
DOCKER_ONLY=false
DRY_RUN=false

for arg in "$@"; do
  case "$arg" in
    --online)      MODE="online" ;;
    --offline)     MODE="offline" ;;
    --dry-run)     DRY_RUN=true ;;
    --docker-only) DOCKER_ONLY=true ;;
  esac
done

# ── 路径 ──────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PACKAGES_DIR="${SCRIPT_DIR}/packages"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

OS="$(uname -s)"
ARCH="$(uname -m)"
DISTRO=""

# 离线包架构后缀映射
case "$ARCH" in
  x86_64|amd64)   PKG_ARCH="amd64"; JDK_ARCH="x64" ;;
  arm64|aarch64)  PKG_ARCH="arm64"; JDK_ARCH="aarch64" ;;
  *)              PKG_ARCH="amd64"; JDK_ARCH="x64" ;;
esac

# ── 离线模式前置检查 ──────────────────────────────────────────
if [ "$MODE" = "offline" ] && [ ! -d "$PACKAGES_DIR" ]; then
  log_error "离线安装包目录不存在: $PACKAGES_DIR"
  log_error ""
  log_error "请先在联网机器上运行:"
  log_error "  ./bootstrap/download-packages.sh"
  log_error ""
  log_error "然后将整个 bootstrap/ 目录复制到目标机器:"
  log_error "  tar -czf bootstrap-packages.tar.gz bootstrap/"
  log_error "  scp bootstrap-packages.tar.gz target-host:"
  log_error "  ssh target-host 'tar -xzf bootstrap-packages.tar.gz'"
  exit 1
fi

if [ "$MODE" = "offline" ]; then
  PKG_COUNT=$(find "$PACKAGES_DIR" -type f -not -name "*.md" -not -name "SHA256SUMS" 2>/dev/null | wc -l | tr -d ' ')
  log_info "离线包: ${PKG_COUNT} 个文件 (${PACKAGES_DIR})"
fi
[ "$OS" = "Linux" ] && DISTRO=$(grep -oP '^ID=\K.*' /etc/os-release 2>/dev/null | tr -d '"' || echo "unknown")

echo "================================================"
echo " ZhiYu-Backend 环境初始化"
echo " OS:      ${OS} ${ARCH}"
echo " Distro:  ${DISTRO:-N/A}"
echo " Mode:    ${MODE}"
echo " Dry-run: ${DRY_RUN}"
echo "================================================"

$DRY_RUN && log_warn "干运行模式 — 仅检查，不安装"

# ═══════════════════════════════════════════════════════════════
# 工具函数
# ═══════════════════════════════════════════════════════════════

check_cmd() {
  if command -v "$1" &>/dev/null; then
    local ver
    ver=$("$1" --version 2>&1 | head -1 | cut -c1-80)
    log_info "  ✓ $1 — $ver"
    return 0
  else
    log_warn "  ✗ $1 — 未安装"
    return 1
  fi
}

need_install() {
  local cmd="$1"
  local version_pattern="${2:-}"
  if ! command -v "$cmd" &>/dev/null; then
    return 0
  fi
  if [ -n "$version_pattern" ]; then
    "$cmd" --version 2>&1 | grep -q "$version_pattern" && return 1 || return 0
  fi
  return 1
}

run() {
  if $DRY_RUN; then
    echo "  [DRY-RUN] $*"
    return 0
  fi
  "$@"
}

# 从本地 packages/ 安装（离线模式）
# 参数: category subdir_name pattern [dest_dir] [strip_components]
install_from_local() {
  local category="$1"
  local pattern="$2"
  local dest_dir="${3:-/usr/local/bin}"
  local strip="${4:-0}"

  local search_dir="${PACKAGES_DIR}/${category}"
  local file
  file=$(find "$search_dir" -maxdepth 1 -name "$pattern" -type f 2>/dev/null | head -1)

  if [ -z "$file" ]; then
    log_error "  ✗ 离线包未找到: ${category}/${pattern}"
    log_error "  请先运行: ./bootstrap/download-packages.sh"
    return 1
  fi

  log_info "  ✓ 从离线包安装: ${category}/$(basename "$file")"

  case "$file" in
    *.tar.gz|*.tgz)
      run sudo mkdir -p "$dest_dir"
      run sudo tar xzf "$file" -C "$dest_dir" --strip-components="$strip"
      ;;
    *)
      run sudo cp "$file" "$dest_dir/"
      run sudo chmod +x "${dest_dir}/$(basename "$file")"
      ;;
  esac
}

# ═══════════════════════════════════════════════════════════════
# macOS (Homebrew)
# ═══════════════════════════════════════════════════════════════
setup_macos() {
  log_step "macOS 环境初始化"

  # Homebrew
  if ! command -v brew &>/dev/null; then
    log_info "安装 Homebrew..."
    $DRY_RUN || /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
  else
    log_info "Homebrew 已安装"
  fi

  if $DOCKER_ONLY; then
    install_macos_docker
    install_macos_kubectl
    return
  fi

  install_macos_docker
  install_macos_kubectl
  install_macos_argo_rollouts
  install_macos_minikube
  install_macos_jdk
  install_macos_tools

  log_step "macOS 环境初始化完成"
}

install_macos_docker() {
  log_info "--- Docker Desktop ---"
  if check_cmd docker; then return; fi
  if [ "$MODE" = "offline" ]; then
    log_warn "Docker Desktop 无法离线安装，请手动下载: https://www.docker.com/products/docker-desktop/"
  else
    run brew install --cask docker
    log_info "Docker Desktop 已安装，请手动启动 Docker 应用"
  fi
}

install_macos_kubectl() {
  log_info "--- kubectl ---"
  if check_cmd kubectl; then return; fi
  if [ "$MODE" = "offline" ]; then
    install_from_local "kubectl" "kubectl-darwin-${PKG_ARCH}" /usr/local/bin
    sudo mv "/usr/local/bin/kubectl-darwin-${PKG_ARCH}" /usr/local/bin/kubectl
  else
    run brew install kubernetes-cli
  fi
}

install_macos_minikube() {
  log_info "--- Minikube ---"
  if check_cmd minikube; then return; fi
  if [ "$MODE" = "offline" ]; then
    install_from_local "minikube" "minikube-darwin-${PKG_ARCH}" /usr/local/bin
    sudo mv "/usr/local/bin/minikube-darwin-${PKG_ARCH}" /usr/local/bin/minikube
  else
    run brew install minikube
  fi
}

install_macos_jdk() {
  log_info "--- JDK 21 (Temurin) ---"
  if command -v java &>/dev/null && java -version 2>&1 | grep -q "21\."; then
    log_info "  ✓ JDK 21 已安装"
    return
  fi
  if [ "$MODE" = "offline" ]; then
    install_from_local "jdk" "OpenJDK21U-jdk_${JDK_ARCH}_mac_*.tar.gz" "/Library/Java/JavaVirtualMachines/" 1
    log_info "JDK 21 已安装到 /Library/Java/JavaVirtualMachines/"
  else
    run brew install temurin21
  fi
}

install_macos_argo_rollouts() {
  log_info "--- Argo Rollouts CLI ---"
  if check_cmd kubectl-argo-rollouts; then return; fi
  if [ "$MODE" = "offline" ]; then
    install_from_local "kubectl" "kubectl-argo-rollouts-darwin-${PKG_ARCH}" /usr/local/bin
    sudo mv "/usr/local/bin/kubectl-argo-rollouts-darwin-${PKG_ARCH}" /usr/local/bin/kubectl-argo-rollouts
  else
    local argo_url
    case "$ARCH" in
      arm64|aarch64) argo_url="https://github.com/argoproj/argo-rollouts/releases/latest/download/kubectl-argo-rollouts-darwin-arm64" ;;
      *)            argo_url="https://github.com/argoproj/argo-rollouts/releases/latest/download/kubectl-argo-rollouts-darwin-amd64" ;;
    esac
    curl -fsSLo /tmp/kubectl-argo-rollouts "$argo_url"
    run sudo install /tmp/kubectl-argo-rollouts /usr/local/bin/kubectl-argo-rollouts
    rm -f /tmp/kubectl-argo-rollouts
  fi
}

install_macos_tools() {
  log_info "--- 其他工具 ---"
  # OpenSSL — macOS 自带，但可能需要更新
  check_cmd openssl || run brew install openssl
  # wget (离线下载时需要)
  check_cmd wget || run brew install wget
}

# ═══════════════════════════════════════════════════════════════
# Ubuntu / Debian
# ═══════════════════════════════════════════════════════════════
setup_linux() {
  log_step "Linux (${DISTRO}) 环境初始化"

  # 基础更新
  if ! $DRY_RUN; then
    sudo apt update -qq
  fi

  if $DOCKER_ONLY; then
    install_linux_docker
    install_linux_kubectl
    return
  fi

  install_linux_docker
  install_linux_kubectl
  install_linux_argo_rollouts
  install_argo_rollouts_controller
  install_linux_minikube
  install_linux_jdk
  install_linux_tools
  [ "$MODE" = "offline" ] && { load_docker_images; install_maven_offline_repo; }

  log_step "Linux 环境初始化完成"
}

install_linux_docker() {
  log_info "--- Docker Engine ---"
  if check_cmd docker; then
    # 确保服务运行
    pgrep dockerd &>/dev/null || run sudo systemctl start docker 2>/dev/null || true
    return
  fi

  if [ "$MODE" = "offline" ]; then
    install_docker_static
  else
    # 使用官方仓库
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs 2>/dev/null || echo "jammy") stable" | \
      sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    sudo apt update -qq
    run sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    run sudo systemctl enable docker
    run sudo systemctl start docker
  fi

  # 将当前用户加入 docker 组
  if ! groups | grep -q docker; then
    run sudo usermod -aG docker "$USER"
    log_warn "已将 $USER 加入 docker 组，重新登录后生效"
  fi
}

install_linux_kubectl() {
  log_info "--- kubectl ---"
  if check_cmd kubectl; then return; fi

  if [ "$MODE" = "offline" ]; then
    install_from_local "kubectl" "kubectl-linux-${PKG_ARCH}" /usr/local/bin
    sudo mv "/usr/local/bin/kubectl-linux-${PKG_ARCH}" /usr/local/bin/kubectl
  else
    curl -fsSL "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/${PKG_ARCH}/kubectl" | \
      run sudo tee /usr/local/bin/kubectl > /dev/null
    run sudo chmod +x /usr/local/bin/kubectl
  fi
}

install_linux_minikube() {
  log_info "--- Minikube ---"
  if check_cmd minikube; then return; fi

  if [ "$MODE" = "offline" ]; then
    install_from_local "minikube" "minikube-linux-${PKG_ARCH}" /usr/local/bin
    sudo mv "/usr/local/bin/minikube-linux-${PKG_ARCH}" /usr/local/bin/minikube
  else
    curl -fsSLo /tmp/minikube "https://github.com/kubernetes/minikube/releases/latest/download/minikube-linux-${PKG_ARCH}"
    run sudo install /tmp/minikube /usr/local/bin/minikube
  fi
}

install_linux_jdk() {
  log_info "--- JDK 21 (Temurin) ---"
  if command -v java &>/dev/null && java -version 2>&1 | grep -q "21\."; then
    log_info "  ✓ JDK 21 已安装"
    return
  fi

  if [ "$MODE" = "offline" ]; then
    install_from_local "jdk" "OpenJDK21U-jdk_${JDK_ARCH}_linux_*.tar.gz" "/usr/lib/jvm" 1
    # 设置默认 Java
    sudo update-alternatives --install /usr/bin/java java /usr/lib/jvm/bin/java 1
    sudo update-alternatives --install /usr/bin/javac javac /usr/lib/jvm/bin/javac 1
  else
    run sudo apt install -y wget apt-transport-https gnupg
    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo gpg --dearmor -o /usr/share/keyrings/adoptium-archive-keyring.gpg
    echo "deb [signed-by=/usr/share/keyrings/adoptium-archive-keyring.gpg] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs 2>/dev/null || echo "jammy") main" | \
      sudo tee /etc/apt/sources.list.d/adoptium.list > /dev/null
    sudo apt update -qq
    run sudo apt install -y temurin-21-jdk
  fi
}

install_linux_tools() {
  log_info "--- 其他工具 ---"
  check_cmd openssl || run sudo apt install -y openssl
  check_cmd curl || run sudo apt install -y curl
  check_cmd jq || run sudo apt install -y jq
  check_cmd envsubst || run sudo apt install -y gettext-base
}

install_linux_argo_rollouts() {
  log_info "--- Argo Rollouts CLI ---"
  if check_cmd kubectl-argo-rollouts; then return; fi
  if [ "$MODE" = "offline" ]; then
    install_from_local "kubectl" "kubectl-argo-rollouts-linux-${PKG_ARCH}" /usr/local/bin
    sudo mv "/usr/local/bin/kubectl-argo-rollouts-linux-${PKG_ARCH}" /usr/local/bin/kubectl-argo-rollouts
  else
    local argo_url
    case "$ARCH" in
      arm64|aarch64) argo_url="https://github.com/argoproj/argo-rollouts/releases/latest/download/kubectl-argo-rollouts-linux-arm64" ;;
      *)            argo_url="https://github.com/argoproj/argo-rollouts/releases/latest/download/kubectl-argo-rollouts-linux-amd64" ;;
    esac
    curl -fsSLo /tmp/kubectl-argo-rollouts "$argo_url"
    run sudo install /tmp/kubectl-argo-rollouts /usr/local/bin/kubectl-argo-rollouts
    rm -f /tmp/kubectl-argo-rollouts
  fi
}

# ═══════════════════════════════════════════════════════════════
# Argo Rollouts Controller（部署到 K8s 集群）
# ═══════════════════════════════════════════════════════════════
install_argo_rollouts_controller() {
  log_info "--- Argo Rollouts Controller ---"

  if ! command -v kubectl &>/dev/null; then
    log_warn "  kubectl 未安装，跳过 Argo Rollouts Controller 部署"
    return 0
  fi

  if ! kubectl cluster-info &>/dev/null 2>&1; then
    log_warn "  kubectl 未连接集群，跳过 Argo Rollouts Controller 部署"
    return 0
  fi

  # 检查是否已部署
  if kubectl get namespace argo-rollouts &>/dev/null; then
    if kubectl get pods -n argo-rollouts -l app.kubernetes.io/name=argo-rollouts 2>/dev/null | grep -q Running; then
      log_info "  ✓ Argo Rollouts Controller 已运行"
      return 0
    fi
  fi

  if [ "$MODE" = "offline" ]; then
    # 离线：使用本地 install manifest
    local manifest="${PACKAGES_DIR}/images/argo-rollouts-install.yaml"
    if [ ! -f "$manifest" ]; then
      log_warn "  Argo Rollouts install manifest 不存在: $manifest"
      log_warn "  请先运行: ./bootstrap/download-packages.sh"
      return 1
    fi
    run kubectl apply -f "$manifest"
  else
    # 在线：直接从 GitHub 安装
    run kubectl create namespace argo-rollouts --dry-run=client -o yaml | kubectl apply -f -
    run kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml
  fi

  log_info "  ✓ Argo Rollouts Controller 已部署"
}

# ═══════════════════════════════════════════════════════════════
# CentOS 7 / RHEL 7
# ═══════════════════════════════════════════════════════════════
setup_centos() {
  log_step "CentOS 7 环境初始化"

  # EPEL 仓库 (jq 等工具需要)
  if ! rpm -q epel-release &>/dev/null; then
    log_info "安装 EPEL..."
    run sudo yum install -y epel-release
  fi

  if ! $DRY_RUN; then
    sudo yum makecache -q
  fi

  if $DOCKER_ONLY; then
    install_centos_docker
    install_linux_kubectl
    return
  fi

  install_centos_docker
  install_linux_kubectl
  install_linux_argo_rollouts
  install_argo_rollouts_controller
  install_linux_minikube
  install_centos_jdk
  install_centos_tools
  [ "$MODE" = "offline" ] && { load_docker_images; install_maven_offline_repo; }

  log_step "CentOS 7 环境初始化完成"
}

install_centos_docker() {
  log_info "--- Docker Engine ---"
  if check_cmd docker; then
    pgrep dockerd &>/dev/null || run sudo systemctl start docker 2>/dev/null || true
    return
  fi

  if [ "$MODE" = "offline" ]; then
    install_docker_static
  else
    run sudo yum install -y yum-utils
    run sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
    run sudo yum install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    run sudo systemctl enable docker
    run sudo systemctl start docker
  fi

  if ! groups | grep -q docker; then
    run sudo usermod -aG docker "$USER"
    log_warn "已将 $USER 加入 docker 组，重新登录后生效"
  fi
}

install_centos_jdk() {
  log_info "--- JDK 21 (Temurin) ---"
  if command -v java &>/dev/null && java -version 2>&1 | grep -q "21\."; then
    log_info "  ✓ JDK 21 已安装"
    return
  fi

  if [ "$MODE" = "offline" ]; then
    install_from_local "jdk" "OpenJDK21U-jdk_${JDK_ARCH}_linux_*.tar.gz" "/usr/lib/jvm" 1
    sudo update-alternatives --install /usr/bin/java java /usr/lib/jvm/bin/java 1
    sudo update-alternatives --install /usr/bin/javac javac /usr/lib/jvm/bin/javac 1
  else
    # Adoptium RPM 仓库
    sudo rpm --import https://packages.adoptium.net/artifactory/api/gpg/key/public
    run sudo tee /etc/yum.repos.d/adoptium.repo > /dev/null <<'REPO'
[adoptium]
name=Adoptium
baseurl=https://packages.adoptium.net/artifactory/rpm/centos/7/$(uname -m)
enabled=1
gpgcheck=1
gpgkey=https://packages.adoptium.net/artifactory/api/gpg/key/public
REPO
    run sudo yum install -y temurin-21-jdk
  fi
}

install_centos_tools() {
  log_info "--- 其他工具 ---"
  check_cmd openssl || run sudo yum install -y openssl
  check_cmd curl || run sudo yum install -y curl
  check_cmd wget || run sudo yum install -y wget
  check_cmd jq || run sudo yum install -y jq
  check_cmd envsubst || run sudo yum install -y gettext
}

# ═══════════════════════════════════════════════════════════════
# Docker Static 二进制离线安装（所有 Linux 发行版通用）
# ═══════════════════════════════════════════════════════════════
install_docker_static() {
  local docker_tar
  docker_tar=$(find "${PACKAGES_DIR}/docker" -maxdepth 1 -name "docker-*.tgz" -type f 2>/dev/null | head -1)

  if [ -z "$docker_tar" ]; then
    log_error "  ✗ Docker 离线包未找到: ${PACKAGES_DIR}/docker/docker-*.tgz"
    log_error "  请先运行: ./bootstrap/download-packages.sh"
    return 1
  fi

  log_info "  从 Docker Static 包安装: $(basename "$docker_tar")"
  local tmpdir
  tmpdir=$(mktemp -d)
  run sudo tar xzf "$docker_tar" -C "$tmpdir"
  run sudo cp "$tmpdir"/docker/* /usr/local/bin/
  rm -rf "$tmpdir"

  # containerd systemd 服务
  if [ ! -f /etc/systemd/system/containerd.service ]; then
    run sudo tee /etc/systemd/system/containerd.service > /dev/null <<'UNIT'
[Unit]
Description=containerd container runtime
Documentation=https://containerd.io
After=network.target local-fs.target

[Service]
ExecStartPre=-/sbin/modprobe overlay
ExecStart=/usr/local/bin/containerd
Type=notify
Delegate=yes
KillMode=process
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
UNIT
  fi

  # Docker systemd 服务
  if [ ! -f /etc/systemd/system/docker.service ]; then
    run sudo tee /etc/systemd/system/docker.service > /dev/null <<'UNIT'
[Unit]
Description=Docker Application Container Engine
Documentation=https://docs.docker.com
After=network-online.target firewalld.service containerd.service
Wants=network-online.target
Requires=containerd.service

[Service]
Type=notify
ExecStart=/usr/local/bin/dockerd -H fd:// --containerd=/run/containerd/containerd.sock
ExecReload=/bin/kill -s HUP $MAINPID
TimeoutStartSec=0
RestartSec=2
Restart=always

[Install]
WantedBy=multi-user.target
UNIT
  fi

  # docker socket
  if [ ! -f /etc/systemd/system/docker.socket ]; then
    run sudo tee /etc/systemd/system/docker.socket > /dev/null <<'UNIT'
[Unit]
Description=Docker Socket for the API

[Socket]
ListenStream=/var/run/docker.sock
SocketMode=0660
SocketUser=root
SocketGroup=docker

[Install]
WantedBy=sockets.target
UNIT
  fi

  # docker 用户组
  getent group docker &>/dev/null || run sudo groupadd docker

  run sudo systemctl daemon-reload
  run sudo systemctl enable containerd docker
  run sudo systemctl start containerd docker
}

# ═══════════════════════════════════════════════════════════════
# Docker 镜像导入（离线模式 — 加载预导出的 tar）
# ═══════════════════════════════════════════════════════════════
load_docker_images() {
  local image_dir="${PACKAGES_DIR}/images"
  if [ ! -d "$image_dir" ]; then
    log_info "  无 Docker 镜像目录 ($image_dir)，跳过导入"
    return 0
  fi

  local count
  count=$(find "$image_dir" -maxdepth 1 -name "*.tar" -type f 2>/dev/null | wc -l | tr -d ' ')
  if [ "$count" -eq 0 ]; then
    return 0
  fi

  log_info "--- Docker 镜像导入 (${count} 个) ---"
  for tar_file in "$image_dir"/*.tar; do
    [ -f "$tar_file" ] || continue
    local image_name
    image_name=$(basename "$tar_file" .tar)
    if docker image inspect "$image_name" &>/dev/null 2>&1; then
      log_info "  ✓ 已存在: $image_name"
      continue
    fi
    log_info "  ↓ 导入: $(basename "$tar_file")"
    run docker load -i "$tar_file"
  done

  log_info "镜像导入完成"
}

# ── 安装 Maven 离线仓库 ───────────────────────────────────────
install_maven_offline_repo() {
  local repo_file="${PACKAGES_DIR}/maven/maven-offline-repo.tar.gz"
  [ -f "$repo_file" ] || return 0

  local m2_dir="${HOME}/.m2/repository"
  if [ -d "$m2_dir" ] && [ "$(ls -A "$m2_dir" 2>/dev/null)" ]; then
    log_info "  ✓ Maven 本地仓库已存在 (~/.m2/repository)"
    return 0
  fi

  log_info "--- Maven 离线仓库 ---"
  log_info "  解压离线仓库到 ~/.m2/repository..."
  mkdir -p "$m2_dir"
  tar xzf "$repo_file" -C "$m2_dir"
  log_info "  ✓ Maven 离线仓库已安装"
}

# ═══════════════════════════════════════════════════════════════
# 安装后验证
# ═══════════════════════════════════════════════════════════════
verify() {
  log_step "安装验证"
  echo ""
  echo "  组件          状态"
  echo "  ────────────  ────"

  check_cmd docker                  && echo "  docker                ✓" || echo "  docker                ✗ 待启动"
  check_cmd kubectl                 && echo "  kubectl               ✓" || echo "  kubectl               ✗"
  check_cmd kubectl-argo-rollouts   && echo "  kubectl-argo-rollouts ✓" || echo "  kubectl-argo-rollouts ✗ (金丝雀发布可选)"
  check_cmd minikube                && echo "  minikube              ✓" || echo "  minikube              ✗"
  check_cmd java                    && echo "  java                  ✓" || echo "  java                  ✗"
  check_cmd openssl                 && echo "  openssl               ✓" || echo "  openssl               ✗"

  echo ""

  if check_cmd java && check_cmd docker && check_cmd kubectl; then
    log_info "核心组件已就绪 ✓"
    echo ""
    echo "下一步:"
    echo "  1. 启动 Docker 并确保 kubectl 连接到集群"
    echo "  2. cd $(dirname "$PROJECT_DIR") && ./deploy/deploy.sh dev all"
    echo ""
    echo "或者先搭建本地 K8s 集群:"
    echo "  minikube start --cpus=2 --memory=4096 --driver=docker"
  else
    log_warn "部分组件缺失，请检查上方输出"
  fi
}

# ═══════════════════════════════════════════════════════════════
# 入口
# ═══════════════════════════════════════════════════════════════
case "$OS" in
  Darwin)
    setup_macos
    ;;
  Linux)
    case "$DISTRO" in
      ubuntu|debian)
        setup_linux
        ;;
      centos|rhel)
        setup_centos
        ;;
      *)
        log_error "不支持的 Linux 发行版: $DISTRO"
        log_info "支持的发行版: Ubuntu 22.04+, Debian 12+, CentOS 7"
        log_info "可手动安装 docker + kubectl + JDK 21 后直接使用 deploy.sh"
        exit 1
        ;;
    esac
    ;;
  *)
    log_error "不支持的操作系统: $OS"
    exit 1
    ;;
esac

verify
