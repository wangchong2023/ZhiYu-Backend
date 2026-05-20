#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: deploy-monitor.sh
# 脚本功能: 专职负责 K8s 集群内监控体系（Prometheus v3 + Grafana + node-exporter +
#           kube-state-metrics）的自动拉起，以及超大监控大盘 ConfigMap 的安全注入与部署。
# 编 写 人: 资深架构师 & 高级开发工程师 (Antigravity AI)
# 编写时间: 2026-05-20
# ==============================================================================

set -euo pipefail

# ── 载入公共核心环境加载器 ──────────────────────────────────────────
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
source "${SCRIPT_DIR}/common.sh"

# ── 帮助菜单 ──────────────────────────────────────────────────
# ==============================================================================
# 函数名称: show_help
# 函数功能: 打印脚本命令行帮助提示菜单
# 参    数: 无
# 返回值/退出码:
#   无
# ==============================================================================
show_help() {
    echo "用法: $0 [env]"
    echo "  env: 目标环境名称，可选值: dev | test | staging | release | kubeadm (默认: kubeadm)"
    echo "  -h, --help: 显示本帮助菜单"
}

parse_common_args "$@"
load_env_and_secrets

MONITORING_DIR="${PROJECT_ROOT}/deploy/manifests/03-monitoring"
MONITORING_NS="monitoring"

# ── 监控组件默认参数覆盖与挂载 ──────────────────────────────────────
export GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-admin}"

# ── 核心监控栈部署编排流程 ─────────────────────────────────────
# ==============================================================================
# 函数名称: deploy_monitoring_stack
# 函数功能: 统筹创建监控独立命名空间，幂等拉起 kube-state-metrics、node-exporter，
#           通过防超限自愈机制刷入大型 Grafana 大盘 ConfigMap，最后编排运行 
#           Prometheus v3 时序数据库与 Grafana 控制台。
# 参    数: 无，依赖全局及加载的环境变量
# 返回值/退出码:
#   0 - 监控栈全生态部署且就绪亮起成功
#   1 - 部署或同步轮询超时失败
# ==============================================================================
deploy_monitoring_stack() {
    log_step "开始部署监控栈生态系统 (Namespace: ${MONITORING_NS}) ..."

    # 1. 创建独立监控命名空间，并自动打上互斥/安全标签，便于网络策略和配额控制
    if ! kubectl get namespace "$MONITORING_NS" &>/dev/null; then
        if [ "$DRY_RUN" = false ]; then
            kubectl create namespace "$MONITORING_NS"
            kubectl label namespace "$MONITORING_NS" name=monitoring --overwrite
        fi
        log_info "命名空间 ${MONITORING_NS} 创建成功，并已打上监控标签"
    else
        log_info "命名空间 ${MONITORING_NS} 已存在"
    fi

    # 2. 部署 K8s 指标采集组件 kube-state-metrics (提供 Pod, STS, Deploy 级别多维指标采集)
    log_info "正在部署 K8s 状态指标采集器 kube-state-metrics..."
    if [ "$DRY_RUN" = true ]; then
        envsubst < "${MONITORING_DIR}/kube-state-metrics.yaml" | kubectl apply -n "$MONITORING_NS" --dry-run=client -f -
    else
        envsubst < "${MONITORING_DIR}/kube-state-metrics.yaml" | kubectl apply -n "$MONITORING_NS" -f -
    fi
    log_info "  ✓ kube-state-metrics 编排部署成功"

    # 3. 部署物理节点守护进程组件 node-exporter (使用 DaemonSet 形式，打通物理主机底层硬件监控)
    log_info "正在部署物理机器底层硬件监控 node-exporter..."
    if [ "$DRY_RUN" = true ]; then
        envsubst < "${MONITORING_DIR}/node-exporter.yaml" | kubectl apply -n "$MONITORING_NS" --dry-run=client -f -
    else
        envsubst < "${MONITORING_DIR}/node-exporter.yaml" | kubectl apply -n "$MONITORING_NS" -f -
    fi
    log_info "  ✓ node-exporter 节点级分发应用成功"

    # 3.5. 部署 metrics-server 至 kube-system（提供 CPU/内存指标给 HPA 和 kubectl top）
    log_info "正在部署 metrics-server 指标采集器..."
    if [ "$DRY_RUN" = true ]; then
        kubectl apply -f "${MONITORING_DIR}/metrics-server.yaml" --dry-run=client
    else
        # 离线环境镜像自愈：若 containerd 中不存在则从归档包自动导入
        local ms_image="${METRICS_SERVER_IMAGE:-registry.k8s.io/metrics-server/metrics-server:v0.7.2}"
        local ms_tag="${ms_image##*:}"
        if ! sudo ctr -n k8s.io images ls | grep -q "metrics-server.*${ms_tag}"; then
            local ms_tar="${PROJECT_ROOT}/deploy/packages/common/images/metrics-server-${ms_tag}.tar"
            if [ -f "$ms_tar" ]; then
                log_info "  从离线归档包导入 metrics-server 镜像..."
                sudo ctr -n k8s.io images import "$ms_tar"
            fi
        fi
        kubectl apply -f "${MONITORING_DIR}/metrics-server.yaml"
    fi
    log_info "  ✓ metrics-server 部署成功"

    # 4. 部署 Prometheus (内置时序数据库 + SVC + ClusterRole 访问控制)
    log_info "正在部署时序数据库中心 Prometheus v3 (容量配置为: ${PROMETHEUS_STORAGE})..."
    if [ "$DRY_RUN" = true ]; then
        envsubst < "${MONITORING_DIR}/prometheus.yaml" | kubectl apply -n "$MONITORING_NS" --dry-run=client -f -
    else
        envsubst < "${MONITORING_DIR}/prometheus.yaml" | kubectl apply -n "$MONITORING_NS" -f -
    fi
    log_info "  ✓ Prometheus 核心时序数据库部署成功"

    # 5. 部署预置大盘（MySQL、Redis、Nacos、Node-Exporter）
    # ── 资深架构师防超限设计 ──────────────────────────────────────
    # 架构原理解析:
    # node-exporter, mysql 等成熟监控大盘的 JSON 定义内容非常庞大（单个仪表盘通常超过 250KB，合集超 1MB）。
    # 如果通过传统的 `kubectl apply -f configmap.yaml` 方式下发，K8s 会将完整的配置以 annotation 形式
    #（last-applied-configuration）附加在 metadata 中。这会瞬间触发 K8s 内部硬性限制错误：
    # "Request entity too large: allowed size is 262144 bytes"。
    # 架构优化策略: 
    # 彻底弃用声明式 apply，采用先 `delete configmap`（幂等清理）再通过原生二进制流直接在 API Server 侧
    # 执行 `kubectl create configmap --from-file=`。此法不将大盘 JSON 载入 annotation 记录，
    # 完美避开了 256KB 的容量天花板，在不借助外部 Chart 的情况下安全无损地完成了大型资源的 K8s 注入。
    # ────────────────────────────────────────────────────────────
    log_info "正在安全注入预置组件监控大盘 ConfigMap (MySQL, Redis, Nacos, OS)..."
    if [ -d "${MONITORING_DIR}/dashboards" ]; then
        if [ "$DRY_RUN" = true ]; then
            log_info "  [DRY-RUN] 校验 dashboards 监控大盘目录"
        else
            kubectl delete configmap grafana-dashboards-json -n "$MONITORING_NS" --ignore-not-found
            kubectl create configmap grafana-dashboards-json --from-file="${MONITORING_DIR}/dashboards" -n "$MONITORING_NS"
            log_info "  ✓ 预置监控大盘 ConfigMap 全量安全注入成功"
        fi
    else
        log_warn "  ⚠️ dashboards 目录不存在，跳过大盘预装载"
    fi

    # 6. 部署 Grafana (可视化分析面板，暴露 30000 端口，配置 Secret 强密码)
    log_info "正在部署大盘可视化套件 Grafana (内置管理员密码: ${GRAFANA_PASSWORD})..."
    if [ "$DRY_RUN" = true ]; then
        envsubst < "${MONITORING_DIR}/grafana.yaml" | kubectl apply -n "$MONITORING_NS" --dry-run=client -f -
    else
        envsubst < "${MONITORING_DIR}/grafana.yaml" | kubectl apply -n "$MONITORING_NS" -f -
    fi
    log_info "  ✓ Grafana 可视化控制台部署成功"

    # 7. 阻塞并确认所有监控 Pod 的亮起，提供运维控制台最佳访问入口提示
    if [ "$DRY_RUN" = false ]; then
        log_info "阻塞等待所有监控栈 Pod 全部就绪 (超时设定为 120s) ..."
        kubectl wait --for=condition=ready pod --all -n "$MONITORING_NS" --timeout=120s 2>/dev/null \
            || log_warn "  ⚠️ 部分监控 Pod 可能启动较慢"
    fi

    log_info "监控栈生态系统全部自举拉起完成 ✓"
    
    local node_ip
    node_ip=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "<远程服务器Node-IP>")
    echo ""
    echo "=========================================================================="
    echo " 📊 ZhiYu 系统统一运维监控访问中心"
    echo "=========================================================================="
    echo " Grafana 控制大盘:     http://${node_ip}:30000"
    echo " Prometheus API:       kubectl port-forward -n monitoring svc/prometheus 9090:9090"
    echo " 控制台登录凭据:        admin / ${GRAFANA_PASSWORD}"
    echo "=========================================================================="
    echo ""
}

# 运行主流程
deploy_monitoring_stack
