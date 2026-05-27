#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: build-image.sh
# 脚本功能: 专职负责 Spring Boot 胖 JAR 的分层依赖提取、Docker 运行时镜像极速构建、
#           以及本地 kubeadm 单节点 Containerd 命名空间(k8s.io)的物理灌入导入。
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

# ── 默认变量计算与填充 ──────────────────────────────────────────
DOCKER_REGISTRY="${DOCKER_REGISTRY-}"
DOCKER_TAG="${DOCKER_TAG:-${PROJECT_VERSION_FULL:-latest}}"
IMAGE_PULL_POLICY="${IMAGE_PULL_POLICY-IfNotPresent}"
SKIP_BUILD="${SKIP_BUILD-false}"
SKIP_PUSH="${SKIP_PUSH-true}"

# Phase 2 微服务镜像名（从 config.env 读取，支持 fallback）
GATEWAY_IMAGE_NAME="${GATEWAY_IMAGE:-zhiyu-gateway}"
AUTH_IMAGE_NAME="${AUTH_IMAGE:-zhiyu-auth}"
ADMIN_SVC_IMAGE_NAME="${ADMIN_SVC_IMAGE:-zhiyu-admin}"

# 计算镜像完整引用路径
if [ -n "${DOCKER_REGISTRY}" ]; then
    GATEWAY_IMAGE_FULL="${DOCKER_REGISTRY}/${GATEWAY_IMAGE_NAME}:${DOCKER_TAG}"
    AUTH_IMAGE_FULL="${DOCKER_REGISTRY}/${AUTH_IMAGE_NAME}:${DOCKER_TAG}"
    ADMIN_SVC_IMAGE_FULL="${DOCKER_REGISTRY}/${ADMIN_SVC_IMAGE_NAME}:${DOCKER_TAG}"
else
    GATEWAY_IMAGE_FULL="${GATEWAY_IMAGE_NAME}:${DOCKER_TAG}"
    AUTH_IMAGE_FULL="${AUTH_IMAGE_NAME}:${DOCKER_TAG}"
    ADMIN_SVC_IMAGE_FULL="${ADMIN_SVC_IMAGE_NAME}:${DOCKER_TAG}"
fi

# ── 核心构建与灌入逻辑 ──────────────────────────────────────────
# ==============================================================================
# 函数名称: build_and_import
# 函数功能: 驱动 Spring Boot 胖 JAR 依赖分层提取，构建高可用容器运行时镜像，并在 
#           kubeadm 环境下一键将镜像物理灌入 containerd 命名空间，实现本地零外网延迟拉起
# 参    数: 无，依赖全局及加载的环境变量
# 返回值/退出码:
#   0 - 编译、构建及导入全链路成功
#   1 - 胖 JAR 未找到或构建/导入过程失败
# ==============================================================================
build_and_import() {
    if [ "$SKIP_BUILD" = "true" ]; then
        log_warn "检测到 SKIP_BUILD=true，跳过 Docker 镜像构建，使用现有本地/预加载镜像"
        return 0
    fi

    cd "$PROJECT_ROOT"

    # ── 1. 查找 admin (zhiyu-server) 胖 JAR ────────────────────
    local admin_jar
    admin_jar=$(find backend/zhiyu-server/target -maxdepth 1 -name "zhiyu-server-*.jar" 2>/dev/null | head -n 1 || echo "")
    if [ -z "$admin_jar" ] || [ ! -f "$admin_jar" ]; then
        log_error "未找到 admin 胖 JAR 编译产物，构建镜像失败！"
        log_error "请先在本地开发端执行打包编译命令: mvn clean package -DskipTests"
        exit 1
    fi
    log_info "发现 admin 胖 JAR 产物: $admin_jar"

    # ── 2. 查找 auth (ufp-auth-service) 胖 JAR ────────────────
    local auth_jar
    auth_jar=$(find backend/ufp/ufp-auth-service/target -maxdepth 1 -name "ufp-auth-service-*.jar" 2>/dev/null | head -n 1 || echo "")
    if [ -z "$auth_jar" ] || [ ! -f "$auth_jar" ]; then
        log_error "未找到 auth 胖 JAR 编译产物，构建镜像失败！"
        log_error "请先在本地开发端执行打包编译命令: mvn clean package -DskipTests"
        exit 1
    fi
    log_info "发现 auth 胖 JAR 产物: $auth_jar"

    # ── 3. 查找 gateway 胖 JAR ──────────────────────────────────
    local gateway_jar
    gateway_jar=$(find backend/ufp/ufp-gateway-service/target -maxdepth 1 -name "ufp-gateway-service-*.jar" 2>/dev/null | head -n 1 || echo "")
    if [ -z "$gateway_jar" ] || [ ! -f "$gateway_jar" ]; then
        log_error "未找到网关胖 JAR 编译产物，构建镜像失败！"
        log_error "请先在本地开发端执行打包编译命令: mvn clean package -DskipTests"
        exit 1
    fi
    log_info "发现网关胖 JAR 产物: $gateway_jar"

    # ── 4. 提取分层依赖 ────────────────────────────────────────
    log_info "正在使用 Spring Boot layertools 提取分层依赖目录..."

    rm -rf backend/zhiyu-server/target/extracted
    java -Djarmode=layertools -jar "$admin_jar" extract --destination backend/zhiyu-server/target/extracted
    log_info "  ✓ zhiyu-server (admin) 分层依赖提取成功"

    rm -rf backend/ufp/ufp-auth-service/target/extracted
    java -Djarmode=layertools -jar "$auth_jar" extract --destination backend/ufp/ufp-auth-service/target/extracted
    log_info "  ✓ ufp-auth-service 分层依赖提取成功"

    rm -rf backend/ufp/ufp-gateway-service/target/extracted
    java -Djarmode=layertools -jar "$gateway_jar" extract --destination backend/ufp/ufp-gateway-service/target/extracted
    log_info "  ✓ ufp-gateway-service 分层依赖提取成功"

    # ── 5. 构建镜像 ────────────────────────────────────────────
    local arch
    arch=$(uname -m | sed 's/x86_64/amd64/;s/aarch64/arm64/')

    # 5a. 网关镜像
    log_info "Docker 构建网关镜像 (芯片架构: ${arch})..."
    local git_hash
    git_hash=$(git -C "${PROJECT_ROOT}" rev-parse --short HEAD 2>/dev/null || echo "unknown")
    docker build -t "$GATEWAY_IMAGE_FULL" \
        --build-arg "TARGETARCH=${arch}" \
        --build-arg "VITE_GIT_HASH=${git_hash}" \
        -f deploy/docker/Dockerfile.gateway "$PROJECT_ROOT"
    log_info "  ✓ 网关镜像构建成功: ${GATEWAY_IMAGE_FULL}"

    # 5b. 认证服务镜像
    log_info "Docker 构建认证服务镜像 (芯片架构: ${arch})..."
    docker build -t "$AUTH_IMAGE_FULL" \
        --build-arg "TARGETARCH=${arch}" \
        -f deploy/docker/Dockerfile.auth "$PROJECT_ROOT"
    log_info "  ✓ 认证服务镜像构建成功: ${AUTH_IMAGE_FULL}"

    # 5c. 业务聚合服务镜像
    log_info "Docker 构建业务聚合服务镜像 (芯片架构: ${arch})..."
    if [ "$ENV" = "kubeadm" ]; then
        docker build -t "$ADMIN_SVC_IMAGE_FULL" \
            --build-arg "TARGETARCH=${arch}" \
            -f deploy/docker/Dockerfile.kubeadm "$PROJECT_ROOT"
    else
        docker build -t "$ADMIN_SVC_IMAGE_FULL" \
            --build-arg "TARGETARCH=${arch}" \
            -f Dockerfile "$PROJECT_ROOT"
    fi
    log_info "  ✓ 业务聚合服务镜像构建成功: ${ADMIN_SVC_IMAGE_FULL}"

    # ── 6. 推送或导入 ──────────────────────────────────────────
    if [ "$SKIP_PUSH" = "true" ]; then
        log_info "检测到 SKIP_PUSH=true，跳过 Docker Registry 远程推送"

        if [ "$ENV" = "kubeadm" ]; then
            log_step "正在将构建完成的镜像物理导入 Containerd (命名空间: k8s.io) ..."
            local tmp_tar
            tmp_tar="${TMPDIR:-/tmp}/zhiyu-images-$$.tar"

            log_info "  1/3 正在导出 3 个微服务镜像为本地 tar 归档: $tmp_tar ..."
            docker save "$GATEWAY_IMAGE_FULL" "$AUTH_IMAGE_FULL" "$ADMIN_SVC_IMAGE_FULL" -o "$tmp_tar"

            log_info "  2/3 正在将 tar 归档物理压入 containerd 命名空间..."
            sudo ctr -n k8s.io images import "$tmp_tar"

            log_info "  3/3 清理临时 tar 缓存文件..."
            rm -f "$tmp_tar"

            log_info "  ✓ 3 个微服务镜像物理压入 Containerd 成功！"
        fi
    else
        log_info "正在将构建成功的镜像推送至指定 Registry 仓库..."
        docker push "$GATEWAY_IMAGE_FULL"
        docker push "$AUTH_IMAGE_FULL"
        docker push "$ADMIN_SVC_IMAGE_FULL"
        log_info "  ✓ 3 个镜像推送至 Registry 成功"
    fi

    log_info "微服务镜像构建与自举处理全部完成 ✓"
}

build_and_import
