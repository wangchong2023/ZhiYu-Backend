#!/bin/bash
# ============================================================
# Kubeadm 安装脚本 — Ubuntu 24.04 ARM64
# 用法:
#   在线安装:  ./deploy/scripts/install-kubeadm.sh
#   仅安装:    ./deploy/scripts/install-kubeadm.sh install
#   仅初始化:  ./deploy/scripts/install-kubeadm.sh init
# ============================================================
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
  log_info "初始化 Kubernetes 集群..."

  # 检查是否已初始化
  if kubectl cluster-info &>/dev/null 2>&1; then
    log_warn "集群已存在，跳过初始化"
    kubectl get nodes
    return 0
  fi

  # kubeadm init
  log_info "  kubeadm init --pod-network-cidr=${POD_CIDR}..."
  kubeadm init --pod-network-cidr="${POD_CIDR}" --kubernetes-version="${K8S_VERSION}" 2>&1 | tail -5

  # 配置 kubectl
  mkdir -p "$HOME/.kube"
  cp /etc/kubernetes/admin.conf "$HOME/.kube/config"
  chown "$(id -u):$(id -g)" "$HOME/.kube/config"

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

  # 等待系统 Pod 就绪
  log_info "等待系统 Pod 就绪（最多 180 秒）..."
  kubectl wait --for=condition=ready pod --all -n kube-system --timeout=180s 2>/dev/null || log_warn "部分系统 Pod 可能仍在启动中"
  kubectl wait --for=condition=ready pod --all -n ingress-nginx --timeout=120s 2>/dev/null || log_warn "Ingress 可能仍在启动中"

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
