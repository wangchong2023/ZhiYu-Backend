#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: install-kubeadm.sh
# 脚本功能: 单机生产级 Kubernetes 集群安装与初始化脚本（适用于 Ubuntu 24.04 ARM64/x64 裸机市场）。
#           支持在线安装和离线安装两种模式，并自动完成以下所有核心层组件的配置开荒：
#             1. containerd 容器运行时安装与 SystemdCgroup 配置（居中节点 Kubernetes 要求）。
#             2. kubeadm / kubelet / kubectl 安装与版本锁定（防止意外升级导致集群不兼容）。
#             3. kubeadm init 执行集群初始化，自动配置 Pod CIDR 网络段。
#             4. 单节点污点移除（允许工作负载调度到 control-plane 节点）。
#             5. Flannel CNI 网络插件安装（支持 Pod 跨宿主机通信）。
#             6. local-path-provisioner 动态 PV 分配器安装（为 StatefulSet PVC 提供节点本地持久化存储）。
#             7. nginx-ingress-controller 安装（NodePort 模式，为外部请求提供入口）。
# 编 写 人: 资深架构师 & 高级开发工程师 (Antigravity AI)
# 编写时间: 2026-05-20
# 调用方式:
#   在线安装并初始化: ./deploy/scripts/install-kubeadm.sh            # install + init
#   仅安装软件包:         ./deploy/scripts/install-kubeadm.sh install  # 仅安装，不初始化
#   仅初始化集群:         ./deploy/scripts/install-kubeadm.sh init     # 仅初始化
# 依赖环境: Ubuntu 24.04 LTS (ARM64 or x64)、需 root 权限或 sudo 权限
# 环境变量:
#   K8S_VERSION - Kubernetes 版本号（默认: 1.32.0）
#   POD_CIDR    - Pod 网络 CIDR（默认: 10.244.0.0/16，与 Flannel 一致）
# 注意事项: 此脚本需要在联网环境下运行。离线安装请使用 os-init.sh + packages/ 离线包方案
# ==============================================================================
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

ACTION="${1:-all}"
K8S_VERSION="${K8S_VERSION:-1.32.0}"
POD_CIDR="${POD_CIDR:-10.244.0.0/16}"

# ── 安装依赖 ──────────────────────────────────────────────────
do_install() {
  log_info "安装 kubeadm / kubelet / kubectl / containerd..."

  # 禁用 swap（K8s 要求）
  swapoff -a
  if grep -q 'swap' /etc/fstab; then
    sed -i '/swap/ s/^/#/' /etc/fstab
    log_info "  swap 已禁用并注释 /etc/fstab"
  fi

  # 加载内核模块
  cat > /etc/modules-load.d/k8s.conf <<'EOF'
br_netfilter
overlay
EOF
  modprobe br_netfilter
  modprobe overlay

  # 内核参数
  cat > /etc/sysctl.d/k8s.conf <<'EOF'
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF
  sysctl --system >/dev/null

  # 安装 containerd
  if ! command -v containerd &>/dev/null; then
    log_info "  安装 containerd..."
    apt-get update -qq
    apt-get install -y -qq containerd 2>&1 | tail -1
    mkdir -p /etc/containerd
    containerd config default > /etc/containerd/config.toml
    CONFIG_VER=$(grep -oP 'version\s*=\s*\K\d+' /etc/containerd/config.toml 2>/dev/null || echo "2")
    if [ "$CONFIG_VER" = "3" ]; then
      # containerd v2: 从 disabled_plugins 中移除 cri，启用 SystemdCgroup
      sed -i 's/disabled_plugins = \[.*"cri".*\]/disabled_plugins = []/' /etc/containerd/config.toml
      sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
    else
      # containerd v1: 启用 SystemdCgroup
      sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
    fi
    sed -i 's|sandbox_image = "registry.k8s.io/pause:.*"|sandbox_image = "registry.k8s.io/pause:3.10"|' /etc/containerd/config.toml
    systemctl restart containerd
    systemctl enable containerd
    log_info "  containerd 已安装并启动 (config v${CONFIG_VER})"
  else
    log_info "  containerd 已存在: $(containerd --version)"
  fi

  # 安装 kubeadm / kubelet / kubectl
  if ! command -v kubeadm &>/dev/null; then
    log_info "  安装 kubeadm / kubelet / kubectl v${K8S_VERSION}..."
    apt-get install -y -qq apt-transport-https ca-certificates curl gpg 2>&1 | tail -1

    # Kubernetes apt 源 (pkgs.k8s.io)
    mkdir -p /etc/apt/keyrings
    curl -fsSL "https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION%.*}/deb/Release.key" \
      | gpg --dearmor --batch --yes -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg 2>/dev/null

    echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION%.*}/deb/ /" \
      > /etc/apt/sources.list.d/kubernetes.list

    apt-get update -qq
    apt-get install -y -qq kubelet="${K8S_VERSION}-*" kubeadm="${K8S_VERSION}-*" kubectl="${K8S_VERSION}-*" 2>&1 | tail -1
    apt-mark hold kubelet kubeadm kubectl

    # 配置 kubelet
    mkdir -p /var/lib/kubelet
    cat > /var/lib/kubelet/config.yaml <<YAML
apiVersion: kubelet.config.k8s.io/v1beta1
kind: KubeletConfiguration
cgroupDriver: systemd
failSwapOn: true
YAML

    systemctl enable kubelet
    log_info "  kubeadm / kubelet / kubectl 已安装"
  else
    log_info "  kubeadm 已存在: $(kubeadm version -o short)"
  fi

  log_info "安装完成 ✓"
}

# ── 初始化集群 ──────────────────────────────────────────────────
do_init() {
  local SCRIPT_DIR
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

  log_info "初始化 Kubernetes 集群..."

  # 检查是否已初始化
  if kubectl cluster-info &>/dev/null 2>&1; then
    log_warn "集群已存在，跳过初始化"
    kubectl get nodes
    return 0
  fi

  # ── 资深架构师强健壮性设计 ──────────────────────────────────────
  # 每次集群初始化前，强制进行 systemd daemon-reload 并重启 containerd 运行时。
  # 这样可以彻底避免因为之前的 kubeadm reset 导致 CNI 状态失联，或者 containerd 出现 connection refused 的顽疾。
  # ──────────────────────────────────────────────────────────
  log_info "  重新加载 systemd 守护进程并重启 containerd 容器运行时..."
  systemctl daemon-reload
  systemctl restart containerd
  sleep 3

  # kubeadm init
  log_info "  kubeadm init --pod-network-cidr=${POD_CIDR}..."
  kubeadm init --pod-network-cidr="${POD_CIDR}" --kubernetes-version="${K8S_VERSION}" 2>&1 | tail -5

  # 配置 kubectl
  mkdir -p "$HOME/.kube"
  cp /etc/kubernetes/admin.conf "$HOME/.kube/config"
  chown "$(id -u):$(id -g)" "$HOME/.kube/config"

  # ── 资深架构师防错设计 ─────────────────────────────────────
  # 如果是通过 sudo 执行，自动也为原普通用户配置 kubectl 凭证，避免非 root 用户运行 deploy.sh 时报找不到集群的错误。
  if [ -n "${SUDO_USER:-}" ] && [ "$SUDO_USER" != "root" ]; then
    local user_home
    user_home=$(eval echo "~$SUDO_USER")
    log_info "  检测到 sudo 调用，自动为原用户 ${SUDO_USER} (${user_home}) 配置 kubeconfig 凭证..."
    mkdir -p "${user_home}/.kube"
    cp /etc/kubernetes/admin.conf "${user_home}/.kube/config"
    chown -R "${SUDO_USER}:${SUDO_USER}" "${user_home}/.kube"
  fi
  # ──────────────────────────────────────────────────────────

  # 单节点：去除 control-plane 污点，允许调度工作负载
  log_info "  去除 control-plane 污点..."
  kubectl taint nodes --all node-role.kubernetes.io/control-plane- 2>/dev/null || true

  # 安装 Flannel CNI
  log_info "  安装 Flannel CNI..."
  kubectl apply -f https://github.com/flannel-io/flannel/releases/latest/download/kube-flannel.yml 2>&1 | tail -3

  # 安装 local-path-provisioner（动态 PV）
  log_info "  安装 local-path-provisioner..."
  kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml 2>&1 | tail -3
  # 设为默认 StorageClass
  kubectl patch storageclass local-path -p '{"metadata": {"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}' 2>/dev/null || true

  # 安装 nginx-ingress (NodePort)
  log_info "  安装 nginx-ingress-controller..."
  kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/baremetal/deploy.yaml 2>&1 | tail -3

  # 安装 cert-manager（Let's Encrypt 自动证书管理）
  log_info "  安装 cert-manager..."
  if [ -f "${SCRIPT_DIR}/install-cert-manager.sh" ]; then
    bash "${SCRIPT_DIR}/install-cert-manager.sh"
  else
    kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.16.2/cert-manager.yaml 2>&1 | tail -3
    kubectl wait --for=condition=ready pod --all -n cert-manager --timeout=120s 2>/dev/null || log_warn "cert-manager 可能仍在启动中"
  fi

  # 等待系统 Pod 就绪
  log_info "等待系统 Pod 就绪（最多 180 秒）..."
  kubectl wait --for=condition=ready pod --all -n kube-system --timeout=180s 2>/dev/null || log_warn "部分系统 Pod 可能仍在启动中"
  kubectl wait --for=condition=ready pod --all -n ingress-nginx --timeout=120s 2>/dev/null || log_warn "Ingress 可能仍在启动中"
  kubectl wait --for=condition=ready pod --all -n cert-manager --timeout=120s 2>/dev/null || log_warn "cert-manager 可能仍在启动中"

  log_info "集群初始化完成 ✓"
  kubectl get nodes
}

# ── 主流程 ────────────────────────────────────────────────────
echo "================================================"
echo " Kubeadm 安装脚本"
echo " K8s: v${K8S_VERSION}  |  Pod CIDR: ${POD_CIDR}"
echo "================================================"
echo ""

case "$ACTION" in
  install)
    do_install
    ;;
  init)
    do_init
    ;;
  all)
    do_install
    do_init
    ;;
  *)
    echo "用法: $0 [install|init|all]"
    exit 1
    ;;
esac
