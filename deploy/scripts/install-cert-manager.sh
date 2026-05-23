#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: install-cert-manager.sh
# 脚本功能: 独立安装 cert-manager（Let's Encrypt 自动 TLS 证书管理）到 K8s 集群。
#           支持在线和离线两种安装模式，幂等（检测已安装则跳过）。
# 编写时间: 2026-05-23
# 调用方式:
#   在线安装: ./deploy/scripts/install-cert-manager.sh
#   离线安装: 确保 deploy/packages/common/cert-manager.yaml 已下载，脚本自动优先使用本地清单
# ==============================================================================
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()  { echo -e "${CYAN}[STEP]${NC}  $*"; }

CERT_MANAGER_VERSION="v1.16.2"
ONLINE_URL="https://github.com/cert-manager/cert-manager/releases/download/${CERT_MANAGER_VERSION}/cert-manager.yaml"

# 计算本地离线清单路径
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_MANIFEST="${SCRIPT_DIR}/../packages/common/cert-manager.yaml"

# ── 安装 cert-manager ──────────────────────────────────────────
do_install() {
    log_step "安装 cert-manager v${CERT_MANAGER_VERSION}..."

    # 检查是否已安装（幂等）
    if kubectl get pods -n cert-manager -l app.kubernetes.io/name=cert-manager \
        --field-selector=status.phase=Running 2>/dev/null | grep -q cert-manager; then
        log_info "cert-manager 已运行，跳过安装"
        kubectl get pods -n cert-manager --no-headers 2>/dev/null || true
        return 0
    fi

    # 优先使用本地离线清单，fallback 在线下载
    if [ -f "$LOCAL_MANIFEST" ]; then
        log_info "使用本地离线清单: cert-manager.yaml"
        kubectl apply -f "$LOCAL_MANIFEST" 2>&1 | tail -5
    else
        log_info "在线下载并安装 cert-manager..."
        kubectl apply -f "$ONLINE_URL" 2>&1 | tail -5
    fi

    # 等待 cert-manager 核心组件就绪
    log_info "等待 cert-manager Pod 就绪 (最多 120 秒)..."
    kubectl wait --for=condition=ready pod \
        -l app.kubernetes.io/instance=cert-manager \
        -n cert-manager --timeout=120s 2>/dev/null \
        || log_warn "部分 cert-manager Pod 可能仍在启动中"

    log_info "cert-manager v${CERT_MANAGER_VERSION} 安装完成"
    kubectl get pods -n cert-manager --no-headers 2>/dev/null || true
}

# ── 主流程 ────────────────────────────────────────────────────
echo "================================================"
echo " cert-manager 安装脚本"
echo " 版本: ${CERT_MANAGER_VERSION}"
echo "================================================"
echo ""

do_install
