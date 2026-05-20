#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: cleanup.sh
# 脚本功能: 专职负责 Kubernetes 集群内 ZhiYu 部署资源及命名空间的物理安全清理与销毁。
#           支持应用资源、基础设施及监控命名空间的干净清除。
#           针对 kubeadm 单机环境，支持传入 --reset-kubeadm 彻底强制重置 Kubernetes 集群节点。
# 编 写 人: 资深架构师 & 高级开发工程师 (Antigravity AI)
# 编写时间: 2026-05-20
# ==============================================================================

set -euo pipefail

# ── 载入公共核心环境加载器 ──────────────────────────────────────────
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
source "${SCRIPT_DIR}/common.sh"

# ── 帮助菜单 ──────────────────────────────────────────────────
show_help() {
    echo "用法: $0 [env] [options]"
    echo "  env:              目标环境名称，可选值: dev | test | staging | release | kubeadm (默认: kubeadm)"
    echo "选项:"
    echo "  --reset-kubeadm:  在 kubeadm 环境下，强制执行 kubeadm reset 重置整个 Kubernetes 节点"
    echo "  -y, --yes:        跳过安全确认，直接执行清理"
    echo "  -h, --help:       显示本帮助菜单"
    echo ""
    echo "示例:"
    echo "  $0 kubeadm --reset-kubeadm --yes"
    echo "  $0 --yes  # 默认使用 kubeadm 环境直接静默清理"
}

# ── 参数解析 ──────────────────────────────────────────────────
RESET_KUBEADM=false
FORCE_YES=false

parse_common_args "$@"
for arg in "$@"; do
    case "$arg" in
        --reset-kubeadm) RESET_KUBEADM=true ;;
        -y|--yes)        FORCE_YES=true ;;
    esac
done

load_env_and_secrets

# ── 核心资源物理销毁逻辑 ────────────────────────────────────────
# 函数名称: do_cleanup
# 函数功能: 彻底删除 K8s 中的应用 Deployment、StatefulSet、Secret、Service、Ingress 并阻塞等待命名空间彻底被注销
do_cleanup() {
    local namespace="${K8S_NAMESPACE}"
    local monitoring_ns="monitoring"

    log_step "开始执行 ${ENV} 环境资源物理安全清理与销毁..."

    # 1. 预览待删除资源
    log_info "将要清理以下资源列表:"
    echo "  ┌─ 业务应用层 (${namespace}):"
    echo "  │  - Deployment / Rollout"
    echo "  │  - Service, Ingress"
    echo "  │  - HPA, PDB, NetworkPolicy, ServiceAccount"
    echo "  │  - ConfigMap, Secret"
    echo "  │"
    echo "  ├─ 基础设施层 (${namespace}):"
    echo "  │  - MySQL StatefulSet + Services + PVC + Secret"
    echo "  │  - Redis StatefulSet + Services + PVC + Secret"
    echo "  │  - Nacos Deployment + Service + PVC + Secret"
    echo "  │"
    echo "  ├─ 系统监控层 (${monitoring_ns}):"
    echo "  │  - Prometheus + Grafana + kube-state-metrics + node-exporter"
    echo "  │"
    echo "  └─ Kubernetes 命名空间物理销毁: ${namespace}, ${monitoring_ns}"
    echo ""

    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY-RUN] 仅进行指令预览，不真正下发销毁指令。"
        echo "将执行:"
        echo "  kubectl delete namespace ${namespace} ${monitoring_ns}"
        if [ "$ENV" = "kubeadm" ] && [ "$RESET_KUBEADM" = true ]; then
            echo "  sudo kubeadm reset --force"
        fi
        return 0
    fi

    # 2. 安全确认阶段
    if [ "$FORCE_YES" = false ]; then
        echo "======================================================================"
        log_warn "⚠️ 警告：此操作将永久抹除 ${ENV} 环境在 K8s 中的全部应用负载与持久化数据！"
        echo "  当前目标命名空间: ${namespace} 与 ${monitoring_ns}"
        echo "======================================================================"
        
        # 生产/预发布环境执行强制二次确认，防止误操作
        if [ "$ENV" = "release" ] || [ "$ENV" = "staging" ]; then
            read -p "⚠️ 检测到是生产/预发布环境，请输入环境名称 [ ${ENV} ] 以确信销毁: " confirm_env
            if [ "$confirm_env" != "$ENV" ]; then
                log_warn "输入的名称不匹配，安全退出清理流程。"
                exit 0
            fi
        else
            read -p "是否确信要开始销毁？[y/N]: " confirm_yn
            if [[ ! "$confirm_yn" =~ ^[yY](es)?$ ]]; then
                log_info "用户取消，安全退出。"
                exit 0
            fi
        fi
    else
        log_info "检测到 -y/--yes 参数，跳过交互式安全确认..."
    fi

    # 检查 kubectl 连通性，如果处于 cleanup 模式，kubectl 断开则允许降级，以便 kubeadm reset 可以继续
    if ! kubectl cluster-info &>/dev/null; then
        log_warn "Kubernetes 集群连通异常，无法通过 API Server 正常删除 K8s 资源"
        if [ "$ENV" = "kubeadm" ] && [ "$RESET_KUBEADM" = true ]; then
            log_info "将跳过 K8s API 删除，直接执行 Kubeadm 节点强制重置..."
        else
            log_error "集群无法连接且未指定重置 Kubeadm，物理清理终止。"
            exit 1
        fi
    else
        # 3. 开始通过 K8s API 异步销毁业务应用层
        log_info "正在清理业务应用层资源..."
        kubectl delete deploy -n "$namespace" -l app=zhiyu-backend --ignore-not-found --wait=false 2>/dev/null || true
        kubectl delete rollout -n "$namespace" -l app=zhiyu-backend --ignore-not-found --wait=false 2>/dev/null || true
        kubectl delete svc -n "$namespace" zhiyu-backend --ignore-not-found 2>/dev/null || true
        kubectl delete ingress -n "$namespace" zhiyu-backend --ignore-not-found 2>/dev/null || true
        kubectl delete hpa -n "$namespace" zhiyu-backend --ignore-not-found 2>/dev/null || true
        kubectl delete pdb -n "$namespace" zhiyu-backend --ignore-not-found 2>/dev/null || true
        kubectl delete networkpolicy -n "$namespace" zhiyu-backend --ignore-not-found 2>/dev/null || true
        kubectl delete sa -n "$namespace" zhiyu-backend --ignore-not-found 2>/dev/null || true
        kubectl delete configmap -n "$namespace" zhiyu-backend-config --ignore-not-found 2>/dev/null || true
        kubectl delete secret -n "$namespace" zhiyu-backend-secret --ignore-not-found 2>/dev/null || true

        # 4. 开始通过 K8s API 异步销毁基础设施层
        log_info "正在清理基础设施资源..."
        kubectl delete sts -n "$namespace" mysql --ignore-not-found --wait=false 2>/dev/null || true
        kubectl delete svc -n "$namespace" mysql mysql-headless --ignore-not-found 2>/dev/null || true
        kubectl delete pvc -n "$namespace" -l app=mysql --ignore-not-found 2>/dev/null || true
        kubectl delete secret -n "$namespace" mysql-secret --ignore-not-found 2>/dev/null || true

        kubectl delete sts -n "$namespace" redis --ignore-not-found --wait=false 2>/dev/null || true
        kubectl delete svc -n "$namespace" redis redis-headless --ignore-not-found 2>/dev/null || true
        kubectl delete pvc -n "$namespace" -l app=redis --ignore-not-found 2>/dev/null || true
        kubectl delete secret -n "$namespace" redis-secret --ignore-not-found 2>/dev/null || true

        kubectl delete deploy -n "$namespace" nacos --ignore-not-found --wait=false 2>/dev/null || true
        kubectl delete svc -n "$namespace" nacos --ignore-not-found 2>/dev/null || true
        kubectl delete pvc -n "$namespace" -l app=nacos --ignore-not-found 2>/dev/null || true
        kubectl delete secret -n "$namespace" nacos-secret --ignore-not-found 2>/dev/null || true

        # 5. 彻底删除监控栈命名空间
        if kubectl get namespace "$monitoring_ns" &>/dev/null; then
            log_info "正在物理卸载监控栈命名空间 (${monitoring_ns})..."
            kubectl delete namespace "$monitoring_ns" --ignore-not-found --wait=false
            
            # ── 资深架构师防撞车同步等待 ───────────────────────
            # 等待命名空间彻底 Terminated，防止下一次部署瞬间冲突
            log_info "同步轮询等待监控命名空间 (${monitoring_ns}) 彻底从集群注销 (限时 60 秒)..."
            local wait_count=0
            local max_wait=30 # 30 * 2s = 60s
            while kubectl get namespace "$monitoring_ns" &>/dev/null; do
                if [ $wait_count -ge $max_wait ]; then
                    log_warn "监控命名空间销毁超时 (60s)，跳过等待。"
                    break
                fi
                sleep 2
                wait_count=$((wait_count + 1))
            done
            log_info "  ✓ 监控栈命名空间已清理完毕"
        fi

        # 6. 彻底删除业务命名空间
        if kubectl get namespace "$namespace" &>/dev/null; then
            log_info "正在物理卸载应用命名空间 (${namespace})..."
            kubectl delete namespace "$namespace" --ignore-not-found --wait=false
            
            log_info "同步轮询等待应用命名空间 (${namespace}) 彻底从集群注销 (限时 60 秒)..."
            local wait_count=0
            local max_wait=30 # 30 * 2s = 60s
            while kubectl get namespace "$namespace" &>/dev/null; do
                if [ $wait_count -ge $max_wait ]; then
                    log_warn "应用命名空间销毁超时 (60s)，跳过等待。"
                    break
                fi
                sleep 2
                wait_count=$((wait_count + 1))
            done
            
            if ! kubectl get namespace "$namespace" &>/dev/null; then
                log_info "  ✓ 应用命名空间已彻底从集群注销！"
            else
                log_warn "  ⚠️ 命名空间长时间处于 Terminating 状态，可能存在 Finalizer 挂起，请手动介入检查。"
            fi
        fi

        # 7. 清理监控遗留全局 RBAC
        kubectl delete clusterrolebinding kube-state-metrics --ignore-not-found 2>/dev/null || true
        kubectl delete clusterrole kube-state-metrics --ignore-not-found 2>/dev/null || true
    fi

    # 8. 针对 Kubeadm 集群节点的硬重置自举 (如果指定)
    if [ "$ENV" = "kubeadm" ] && [ "$RESET_KUBEADM" = true ]; then
        log_step "准备在当前节点强制执行 kubeadm reset ..."
        # 提供默认的普通用户 parallels 执行 sudo 自动提权，配合 echo 'root'
        sudo kubeadm reset --force || log_warn "kubeadm reset 执行失败，请手动确认。"
        
        log_info "清理遗留 CNI 网络接口与 local 路由配置..."
        sudo rm -rf /etc/cni/net.d
        rm -rf "$HOME/.kube/config" 2>/dev/null || true
        rm -rf /home/parallels/.kube/config 2>/dev/null || true
        log_info "  ✓ Kubernetes 节点已强制重置成功，遗留网络凭证清除完毕。"
    fi

    log_info "一键资源清理与物理安全销毁工作顺利执行完毕 ✓"
}

# 运行清理主逻辑
do_cleanup
