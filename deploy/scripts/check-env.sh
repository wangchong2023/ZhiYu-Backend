#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: check-env.sh
# 脚本功能: 专职负责部署前的本地/远程环境预检与 Kubernetes 集群连通性测试。
# 编 写 人: 资深架构师 & 高级开发工程师 (Antigravity AI)
# 编写时间: 2026-05-20
# ==============================================================================

set -euo pipefail

# ── 载入公共核心环境加载器 ──────────────────────────────────────────
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
source "${SCRIPT_DIR}/common.sh"

# ── 帮助菜单 ──────────────────────────────────────────────────
show_help() {
    echo "用法: $0 [env]"
    echo "  env: 目标环境名称，可选值: dev | test | staging | release | kubeadm (默认: kubeadm)"
    echo "  -h, --help: 显示本帮助菜单"
    echo ""
    echo "示例: $0 kubeadm"
}

parse_common_args "$@"
load_env_and_secrets

# ── 核心环境校验函数 ──────────────────────────────────────────
# 函数名称: verify_tools
# 函数功能: 校验必需的系统运维依赖工具链是否就绪，并测试 Kubernetes API Server 连通性
verify_tools() {
    log_step "开始执行部署前置条件与工具链检查..."
    local ok=true

    # 工具命令检查辅助函数
    check_cmd() {
        local cmd="$1"
        if command -v "$cmd" &>/dev/null; then
            log_info "  ✓ [系统工具] $cmd -> 已安装在: $(command -v "$cmd")"
        else
            log_error "  ✗ [系统工具] $cmd -> 未安装，请先进行系统开荒部署"
            ok=false
        fi
    }

    # 依次校验核心依赖
    check_cmd kubectl
    check_cmd docker
    check_cmd openssl

    # 检查 argo-rollouts 插件 (Staging/Release Canary 依赖，非强制)
    if command -v kubectl-argo-rollouts &>/dev/null; then
        log_info "  ✓ [可选工具] kubectl-argo-rollouts -> $(kubectl-argo-rollouts version 2>&1 | head -n 1 | cut -c1-40)"
    else
        log_warn "  ⚠️ [可选工具] kubectl-argo-rollouts -> 未安装 (Staging/Release 将滚动发布)"
    fi

    # 测试 Kubernetes API 连通性
    log_info "测试 Kubernetes 集群连通性..."
    if kubectl cluster-info &>/dev/null; then
        log_info "  ✓ [集群连接] 成功建立与 Kubernetes API Server 的物理连接"
    else
        log_error "  ✗ [集群连接] 无法连接到指定的 Kubernetes 集群，请检查 Kubeconfig 状态"
        ok=false
    fi

    if [ "$ok" = false ]; then
        log_error "前置条件检查未通过，部署流被迫终止！"
        exit 1
    fi

    log_info "所有前置条件检查通过，系统环境就绪 ✓"
}

# 运行主校验函数
verify_tools
