#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: deploy-frontend.sh
# 脚本功能: 专职负责 K8s 集群内 admin-web 前端容器（Nginx 静态托管）的
#           Deployment、Service 以及 Ingress 路由的一键编排发布。
# 编 写 人: 资深架构师 & 高级开发工程师 (Antigravity AI)
# 编写时间: 2026-05-21
# ==============================================================================

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
source "${SCRIPT_DIR}/common.sh"

show_help() {
    echo "用法: $0 [env]"
    echo "  env: 目标环境名称，可选值: dev | test | staging | release | kubeadm (默认: kubeadm)"
    echo "  -h, --help: 显示本帮助菜单"
}

parse_common_args "$@"
load_env_and_secrets

APP_DIR="${PROJECT_ROOT}/deploy/manifests/02-app"

# ── 计算前端镜像引用 ──────────────────────────────────────────
ADMIN_WEB_IMAGE="${ADMIN_WEB_IMAGE:-zhiyu-admin-web}"
ADMIN_WEB_TAG="${ADMIN_WEB_TAG:-${PROJECT_VERSION:-latest}}"

if [ -n "${DOCKER_REGISTRY:-}" ]; then
    export ADMIN_WEB_IMAGE_FULL="${DOCKER_REGISTRY}/${ADMIN_WEB_IMAGE}:${ADMIN_WEB_TAG}"
else
    export ADMIN_WEB_IMAGE_FULL="${ADMIN_WEB_IMAGE}:${ADMIN_WEB_TAG}"
fi
export IMAGE_PULL_POLICY="${IMAGE_PULL_POLICY:-IfNotPresent}"

# ── 默认资源配额 ──────────────────────────────────────────────
export ADMIN_WEB_REPLICAS="${ADMIN_WEB_REPLICAS:-1}"
export ADMIN_WEB_CPU_REQUEST="${ADMIN_WEB_CPU_REQUEST:-50m}"
export ADMIN_WEB_CPU_LIMIT="${ADMIN_WEB_CPU_LIMIT:-200m}"
export ADMIN_WEB_MEM_REQUEST="${ADMIN_WEB_MEM_REQUEST:-64Mi}"
export ADMIN_WEB_MEM_LIMIT="${ADMIN_WEB_MEM_LIMIT:-128Mi}"

deploy_frontend() {
    log_step "开始编排与发布前端 admin-web (Namespace: ${K8S_NAMESPACE}) ..."

    # 1. 部署前端 Deployment
    if [ -f "${APP_DIR}/deployment-frontend.yaml" ]; then
        apply_template "${APP_DIR}/deployment-frontend.yaml" "admin-web Deployment"
    else
        log_error "未找到 deployment-frontend.yaml"
        exit 1
    fi

    # 2. 部署前端 Service
    if [ -f "${APP_DIR}/service-frontend.yaml" ]; then
        apply_template "${APP_DIR}/service-frontend.yaml" "admin-web Service"
    else
        log_error "未找到 service-frontend.yaml"
        exit 1
    fi

    # 3. 更新 Ingress 路由 — 将前端流量 (/ 路径) 指向 admin-web Service
    #    架构说明: 在 kubeadm 单节点部署中，Ingress 负责按路径分流：
    #      /api/v1       → zhiyu-backend:8080  (后端 API)
    #      /actuator/*   → zhiyu-backend:8080  (健康检查)
    #      /             → admin-web:80        (管理后台 SPA)
    #
    #    这里采用 patch 方式给已有 Ingress 追加前端路由规则，避免覆盖后端规则。
    if [ -f "${APP_DIR}/ingress.yaml" ]; then
        log_info "正在更新 Ingress 路由，添加前端 / 路径规则..."

        # 检查 Ingress 是否已有 / 路径规则（避免重复添加）
        if kubectl get ingress zhiyu-backend -n "${K8S_NAMESPACE}" -o yaml 2>/dev/null | \
            grep -q "path: /$"; then
            log_info "  Ingress / 路径规则已存在，跳过添加"
        else
            # 动态追加 / → admin-web 的路由规则
            kubectl patch ingress zhiyu-backend -n "${K8S_NAMESPACE}" --type=json \
                -p "[{\"op\": \"add\", \"path\": \"/spec/rules/0/http/paths/-\", \"value\": {
                    \"path\": \"/\",
                    \"pathType\": \"Prefix\",
                    \"backend\": {
                        \"service\": {\"name\": \"admin-web\", \"port\": {\"name\": \"http\"}}
                    }
                }}]" 2>/dev/null || {
                    log_warn "  ⚠️ 无法动态 patch Ingress，请手动更新 ingress.yaml 并重新 apply"
                }
            log_info "  ✓ Ingress / 路由已添加 → admin-web:80"
        fi
    else
        log_warn "未找到 ingress.yaml，跳过 Ingress 路由更新"
    fi

    # 4. 等待前端 Pod 就绪
    if [ "$DRY_RUN" = false ]; then
        log_info "等待 admin-web Pod 完全就绪 (超时 60s) ..."
        kubectl wait --for=condition=ready pod -l app=admin-web \
            -n "${K8S_NAMESPACE}" --timeout=60s 2>/dev/null \
            || log_warn "  ⚠️ admin-web Pod 未在限时内 Ready，请手动检查"
    fi

    log_info "前端 admin-web 编排发布完成 ✓"
}

deploy_frontend
