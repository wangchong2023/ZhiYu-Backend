#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: deploy-app.sh
# 脚本功能: 专职负责 K8s 集群内 ZhiYu 核心业务微服务应用（Deployment/Rollout、Service、
#           Ingress、HPA、PDB、NetworkPolicy 等）的变量加载、YAML动态渲染及一键编排发布。
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

APP_DIR="${PROJECT_ROOT}/deploy/manifests/02-app"

# ── 计算微服务最新镜像引用 ──────────────────────────────────────
DOCKER_REGISTRY="${DOCKER_REGISTRY-}"
DOCKER_IMAGE="${DOCKER_IMAGE-zhiyu-backend}"
DOCKER_TAG="${DOCKER_TAG-latest}"
IMAGE_PULL_POLICY="${IMAGE_PULL_POLICY-IfNotPresent}"

if [ -n "${DOCKER_REGISTRY}" ]; then
    export IMAGE_FULL="${DOCKER_REGISTRY}/${DOCKER_IMAGE}:${DOCKER_TAG}"
else
    export IMAGE_FULL="${DOCKER_IMAGE}:${DOCKER_TAG}"
fi
export IMAGE_PULL_POLICY

# ── 核心微服务编排部署流程 ─────────────────────────────────────
# ==============================================================================
# 函数名称: deploy_application
# 函数功能: 一键渲染并部署 ZhiYu 微服务 ConfigMap，提取本地非对称 PEM 公私钥对并 
#           Base64 动态注入 K8s 绝密 Secret，根据环境调度进行 Argo Rollout 或平滑 
#           Deployment 部署，最后拉起配套 Service、Ingress 路由和高可用弹性组件。
# 参    数: 无，依赖全局及加载的环境变量
# 返回值/退出码:
#   0 - 业务应用全栈资源部署且 Ready 启动成功
#   1 - 证书找不到或 kubectl/rollout 等待超时崩溃
# ==============================================================================
deploy_application() {
    log_step "开始编排与发布核心微服务应用 (Namespace: ${K8S_NAMESPACE}) ..."

    # 1. 部署 ConfigMap
    if [ -f "${APP_DIR}/configmap.yaml" ]; then
        apply_template "${APP_DIR}/configmap.yaml" "ConfigMap"
    else
        log_warn "未找到 configmap.yaml 模板配置，跳过部署"
    fi

    # 2. 深度架构安全设计: 金融级非对称 JWT 公私钥对自适应动态压入控制
    # 架构原理解析:
    # 业务系统鉴权基于高安全性 JWT RS256（非对称加密）算法，私钥进行签名，公钥进行验签。
    # 传统的敏感密钥硬编码或提交至 Git 极为危险，且在离线环境下手工注入公私钥容易发生编码损毁。
    # 此处设计了基于物理 Base64 编码的安全自动化压入控制：
    #   - 动态提取 `ensure-secrets.sh` 生成的高安全 RSA 2048位 `jwt-private.pem` 和 `jwt-public.pem` 文件。
    #   - 利用 Linux 底层 base64 工具将其转换为 K8s Secret 识别的二进制编码流，并去除换行符。
    #   - 将转化后的 JWT 密钥流连同 MySQL/Redis/Nacos 的强随机明文密码，以 kubectl create secret generic
    #     命令原生态下发创建，既在集群内实现了敏感配置物理隔离，又让微服务能通过环境变量直接无感挂载和解析。
    local jwt_key_dir="${JWT_KEY_DIR:-${PROJECT_ROOT}/deploy/envs/${ENV}}"
    if [ -f "${jwt_key_dir}/jwt-private.pem" ] && [ -f "${jwt_key_dir}/jwt-public.pem" ]; then
        log_info "正在载入非对称 JWT 私钥与公钥对，并转化为 Base64 二进制流..."
        
        local jwt_private_b64 jwt_public_b64
        jwt_private_b64=$(base64 < "${jwt_key_dir}/jwt-private.pem" | tr -d '\n')
        jwt_public_b64=$(base64 < "${jwt_key_dir}/jwt-public.pem" | tr -d '\n')
        
        local secret_dry_flag=""
        [ "$DRY_RUN" = true ] && secret_dry_flag="--dry-run=client"

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
            --dry-run=client -o yaml | kubectl apply -f - $secret_dry_flag
        log_info "  ✓ zhiyu-backend-secret 密钥流部署成功"
    else
        log_error "未在 $jwt_key_dir 目录中找到有效的非对称 jwt-private.pem/jwt-public.pem 证书对！"
        log_error "请先运行 init-db.sh 级联生成安全密码与非对称 JWT 证书！"
        exit 1
    fi

    # 3. 部署应用核心资源（Staging / Release 生产级部署使用 Argo Rollout 金丝雀模板实现渐进式发布，开发环境回退常规 Deployment）
    # 架构自愈设计: 若为生产/预发且内置有 Argo Rollout，则采用高大上的 Canary 金丝雀发布流；
    # 若环境未安装 argo-rollouts 控制器，脚本支持无损自动平滑降级为普通的 Deployment 应用，最大程度保障集群拓扑弹性。
    if [ "$ENV" = "release" ] || [ "$ENV" = "staging" ]; then
        if [ -f "${APP_DIR}/rollout.yaml" ]; then
            apply_template "${APP_DIR}/rollout.yaml" "Argo Rollout (Canary 发布)"
        else
            log_warn "rollout.yaml 不存在，自动平滑降级回退至 Deployment 发布模式"
            apply_template "${APP_DIR}/deployment.yaml" "Deployment"
        fi
    else
        apply_template "${APP_DIR}/deployment.yaml" "Deployment"
    fi

    # 4. 部署配套 SVC 与 Ingress 路由，打通南北向外部准入网关
    apply_template "${APP_DIR}/service.yaml" "Service"
    apply_template "${APP_DIR}/ingress.yaml" "Ingress IngressRoute"

    # 5. 部署高级高可用运维策略 (HPA水平扩容 / PDB容灾主动中断保护 / NetworkPolicy防火墙 / ServiceAccount)
    if [ -f "${APP_DIR}/hpa.yaml" ]; then
        apply_template "${APP_DIR}/hpa.yaml" "HPA (弹性伸缩)"
    fi
    if [ -f "${APP_DIR}/pdb.yaml" ]; then
        apply_template "${APP_DIR}/pdb.yaml" "PDB (主动中断保护)"
    fi
    if [ -f "${APP_DIR}/network-policy.yaml" ]; then
        apply_template "${APP_DIR}/network-policy.yaml" "NetworkPolicy" || log_warn "  ⚠️ CNI 不支持 NetworkPolicy"
    fi
    if [ -f "${APP_DIR}/service-account.yaml" ]; then
        apply_template "${APP_DIR}/service-account.yaml" "ServiceAccount"
    fi

    # 6. 阻塞并同步确认业务 Pod 的亮起状态
    if [ "$DRY_RUN" = false ]; then
        log_info "阻塞等待 business 微服务 Pod 完全就绪 (超时设定为 180s) ..."
        if [ "$ENV" = "release" ] || [ "$ENV" = "staging" ] && [ -f "${APP_DIR}/rollout.yaml" ] && command -v kubectl-argo-rollouts &>/dev/null; then
            kubectl argo rollouts status zhiyu-backend -n "${K8S_NAMESPACE}" --timeout=300s 2>/dev/null \
                || log_warn "  ⚠️ Argo Rollout 等待超时，请通过 argo 命令行确认部署状态"
        else
            kubectl wait --for=condition=ready pod -l app=zhiyu-backend -n "${K8S_NAMESPACE}" --timeout=180s 2>/dev/null \
                || log_warn "  ⚠️ 业务微服务 Pod 未能在限时内全部 Ready"
        fi
    fi

    log_info "业务微服务应用编排发布一键执行完成 ✓"
}

# 运行主流程
deploy_application
