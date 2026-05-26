#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: deploy-app.sh
# 脚本功能: K8s 集群内 ZhiYu 微服务应用（Deployment、Service、Ingress、HPA、PDB、
#           NetworkPolicy 等）的变量加载、YAML动态渲染及多服务一键编排发布。
#
# 微服务拆分 (Phase 2):
#   ufp-gateway  — API 网关 + 前端 SPA（Spring Cloud Gateway，替代 Nginx）
#   ufp-auth     — 认证服务（JWT/OAuth/TOTP/WebAuthn）
#   zhiyu-admin  — 业务聚合（管理后台/用户/订阅/通知）
# 编 写 人: 资深架构师 & 高级开发工程师 (Antigravity AI)
# 编写时间: 2026-05-20
# 更新时间: 2026-05-27 (多服务拆分)
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
}

parse_common_args "$@"
load_env_and_secrets

APP_DIR="${PROJECT_ROOT}/deploy/manifests/02-app"

# ── 微服务定义 ──────────────────────────────────────────────────
# 镜像名从 config.env 环境变量读取（支持 fallback）
#   ufp-gateway  — API 网关 + 前端 SPA（Spring Cloud Gateway，替代 Nginx）
#   ufp-auth     — 认证服务（JWT/OAuth/TOTP/WebAuthn）
#   zhiyu-admin  — 业务聚合（管理后台/用户/订阅/通知）
GATEWAY_IMAGE_NAME="${GATEWAY_IMAGE:-zhiyu-gateway}"
AUTH_IMAGE_NAME="${AUTH_IMAGE:-zhiyu-auth}"
ADMIN_SVC_IMAGE_NAME="${ADMIN_SVC_IMAGE:-zhiyu-admin}"

declare -A SERVICE_IMAGE_MAP=(
    ["ufp-gateway"]="$GATEWAY_IMAGE_NAME"
    ["ufp-auth"]="$AUTH_IMAGE_NAME"
    ["zhiyu-admin"]="$ADMIN_SVC_IMAGE_NAME"
)
# Phase 2 拆分完成后，ufp-auth 和 zhiyu-admin 各自使用独立镜像:
#   AUTH_IMAGE="ufp-auth-server"
#   ADMIN_SVC_IMAGE="zhiyu-admin-server"

DOCKER_REGISTRY="${DOCKER_REGISTRY-}"
DOCKER_TAG="${DOCKER_TAG:-${PROJECT_VERSION_FULL:-latest}}"
IMAGE_PULL_POLICY="${IMAGE_PULL_POLICY-IfNotPresent}"
export IMAGE_PULL_POLICY

# ── 计算各服务完整镜像引用 ──────────────────────────────────────
declare -A SERVICE_IMAGES
for svc in "${!SERVICE_IMAGE_MAP[@]}"; do
    img="${SERVICE_IMAGE_MAP[$svc]}"
    if [ -n "${DOCKER_REGISTRY}" ]; then
        SERVICE_IMAGES[$svc]="${DOCKER_REGISTRY}/${img}:${DOCKER_TAG}"
    else
        SERVICE_IMAGES[$svc]="${img}:${DOCKER_TAG}"
    fi
done

# ── 导出各服务镜像变量（manifest 模板中通过 envsubst 引用） ──────
export GATEWAY_IMAGE_FULL="${SERVICE_IMAGES[ufp-gateway]}"
export AUTH_IMAGE_FULL="${SERVICE_IMAGES[ufp-auth]}"
export ADMIN_SVC_IMAGE_FULL="${SERVICE_IMAGES[zhiyu-admin]}"

# ==============================================================================
# 函数名称: deploy_shared_resources
# 函数功能: 部署所有微服务共享的 K8s 资源（Namespace、Secret、Ingress、RBAC、PDB）
# ==============================================================================
deploy_shared_resources() {
    log_step "部署共享资源 (Namespace: ${K8S_NAMESPACE}) ..."

    # 1. Namespace
    apply_template "${APP_DIR}/shared/namespace.yaml" "Namespace"

    # 2. Secret — JWT 非对称密钥对 + 数据库/Redis/Nacos 密码
    local jwt_key_dir="${JWT_KEY_DIR:-${PROJECT_ROOT}/deploy/envs/${ENV}}"
    if [ -f "${jwt_key_dir}/jwt-private.pem" ] && [ -f "${jwt_key_dir}/jwt-public.pem" ]; then
        log_info "正在载入非对称 JWT 私钥与公钥对，并转化为 Base64 二进制流..."

        local jwt_private_b64 jwt_public_b64
        jwt_private_b64=$(base64 < "${jwt_key_dir}/jwt-private.pem" | tr -d '\n')
        jwt_public_b64=$(base64 < "${jwt_key_dir}/jwt-public.pem" | tr -d '\n')

        log_info "正在生成并热加载微服务高安全 Secret: zhiyu-backend-secret ..."
        kubectl create secret generic zhiyu-backend-secret \
            -n "${K8S_NAMESPACE}" \
            --from-literal=JWT_PRIVATE_KEY="$jwt_private_b64" \
            --from-literal=JWT_PUBLIC_KEY="$jwt_public_b64" \
            --from-literal=SPRING_DATASOURCE_USERNAME="${MYSQL_USER:-zhiyu}" \
            --from-literal=SPRING_DATASOURCE_PASSWORD="${MYSQL_PASSWORD:-}" \
            --from-literal=SPRING_DATA_REDIS_PASSWORD="${REDIS_PASSWORD:-}" \
            --from-literal=SPRING_CLOUD_NACOS_USERNAME="${NACOS_USERNAME:-nacos}" \
            --from-literal=SPRING_CLOUD_NACOS_PASSWORD="${NACOS_PASSWORD:-}" \
            --from-literal=ADMIN_PASSWORD_HASH="${ADMIN_PASSWORD_HASH:-}" \
            --dry-run=client -o yaml | kubectl apply -f -
        log_info "  ✓ zhiyu-backend-secret 密钥流部署成功"
    else
        log_error "未在 $jwt_key_dir 目录中找到有效的非对称 jwt-private.pem/jwt-public.pem 证书对！"
        log_error "请先运行 ensure-secrets.sh 级联生成安全密码与非对称 JWT 证书！"
        exit 1
    fi

    # 3. RBAC（zhiyu-admin 需要 Pod 列表权限）
    apply_template "${APP_DIR}/shared/rbac.yaml" "RBAC"

    # 4. PDB（主动中断保护，每服务一个）
    apply_template "${APP_DIR}/shared/pdb.yaml" "PDB"

    # 5. NetworkPolicy（CNI 不支持时降级）
    if [ -f "${APP_DIR}/shared/network-policy.yaml" ]; then
        apply_template "${APP_DIR}/shared/network-policy.yaml" "NetworkPolicy" || log_warn "  ⚠️ CNI 不支持 NetworkPolicy"
    fi

    # 6. Argo Rollout（仅 staging/release 且文件存在时）
    if [ "$ENV" = "release" ] || [ "$ENV" = "staging" ]; then
        if [ -f "${APP_DIR}/shared/rollout.yaml" ]; then
            apply_template "${APP_DIR}/shared/rollout.yaml" "Argo Rollout"
        fi
    fi
}

# ==============================================================================
# 函数名称: deploy_service
# 函数功能: 部署单个微服务的 ConfigMap、Deployment、Service、HPA
# 参    数:
#   $1 - string - 服务名称（如 ufp-gateway）
# ==============================================================================
deploy_service() {
    local svc="$1"
    local svc_dir="${APP_DIR}/${svc}"

    if [ ! -d "$svc_dir" ]; then
        log_error "服务清单目录不存在: $svc_dir"
        return 1
    fi

    log_step "部署微服务: ${svc} ..."

    # 1. ConfigMap
    if [ -f "${svc_dir}/configmap.yaml" ]; then
        apply_template "${svc_dir}/configmap.yaml" "${svc} ConfigMap"
    else
        log_warn "未找到 ${svc}/configmap.yaml，跳过 ConfigMap 部署"
    fi

    # 2. Deployment（staging/release 优先 Argo Rollout）
    if [ "$ENV" = "release" ] || [ "$ENV" = "staging" ]; then
        if [ -f "${svc_dir}/rollout.yaml" ]; then
            apply_template "${svc_dir}/rollout.yaml" "${svc} Argo Rollout"
        else
            log_warn "${svc}/rollout.yaml 不存在，降级使用 Deployment"
            apply_template "${svc_dir}/deployment.yaml" "${svc} Deployment"
        fi
    else
        apply_template "${svc_dir}/deployment.yaml" "${svc} Deployment"
    fi

    # 3. Service
    apply_template "${svc_dir}/service.yaml" "${svc} Service"

    # 4. HPA（可选）
    if [ -f "${svc_dir}/hpa.yaml" ]; then
        apply_template "${svc_dir}/hpa.yaml" "${svc} HPA"
    fi

    # 5. ServiceAccount（可选）
    if [ -f "${svc_dir}/service-account.yaml" ]; then
        apply_template "${svc_dir}/service-account.yaml" "${svc} ServiceAccount"
    fi
}

# ==============================================================================
# 函数名称: wait_for_service
# 函数功能: 阻塞等待指定微服务的所有 Pod 就绪
# 参    数:
#   $1 - string - 服务名称（label app=$1）
# ==============================================================================
wait_for_service() {
    local svc="$1"
    local timeout="${2:-180}"

    if [ "$DRY_RUN" = true ]; then
        log_info "  [DRY-RUN] 跳过 ${svc} Pod 就绪等待"
        return
    fi

    log_info "阻塞等待 ${svc} Pod 完全就绪 (超时 ${timeout}s) ..."
    if ! kubectl wait --for=condition=ready pod -l "app=${svc}" -n "${K8S_NAMESPACE}" --timeout="${timeout}s" 2>/dev/null; then
        log_warn "  ⚠️ ${svc} Pod 未能在 ${timeout}s 内全部 Ready，请手动检查"
    else
        log_info "  ✓ ${svc} 全部 Pod 就绪"
    fi
}

# ==============================================================================
# 函数名称: deploy_all
# 函数功能: 一键编排部署全部微服务及共享资源
# ==============================================================================
deploy_all() {
    log_info "开始编排与发布 ZhiYu 微服务集群 (Namespace: ${K8S_NAMESPACE}) ..."
    log_info "目标服务: ${!SERVICE_IMAGE_MAP[*]}"
    log_info "镜像标签: ${DOCKER_TAG}"

    # 1. 共享资源
    deploy_shared_resources

    # 2. 逐服务部署
    for svc in "${!SERVICE_IMAGE_MAP[@]}"; do
        deploy_service "$svc"
    done

    # 3. Ingress（在所有 Service 创建后部署，确保 backend 可解析）
    apply_template "${APP_DIR}/shared/ingress.yaml" "Ingress"

    # 4. 等待所有 Pod 就绪
    for svc in "${!SERVICE_IMAGE_MAP[@]}"; do
        wait_for_service "$svc"
    done

    log_info "ZhiYu 微服务集群编排发布完成 ✓"
}

# 运行主流程
deploy_all
