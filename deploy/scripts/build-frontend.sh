#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: build-frontend.sh
# 脚本功能: 专职负责前端 admin-web 的 Docker 运行时镜像构建，以及本地 kubeadm
#           单节点 Containerd 命名空间(k8s.io)的物理灌入导入。
#           前置条件: frontend/dist/ 目录已存在（由本地 npm run build 或 CI 产生）。
# 编 写 人: 资深架构师 & 高级开发工程师 (Antigravity AI)
# 编写时间: 2026-05-21
# ==============================================================================

set -euo pipefail

# ── 载入公共核心环境加载器 ──────────────────────────────────────────
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
source "${SCRIPT_DIR}/common.sh"

show_help() {
    echo "用法: $0 [env]"
    echo "  env: 目标环境名称，可选值: dev | test | staging | release | kubeadm (默认: kubeadm)"
    echo "  -h, --help: 显示本帮助菜单"
    echo ""
    echo "前置条件: frontend/dist/ 目录必须存在（本地执行 npm run build 或 CI 构建产物）"
}

parse_common_args "$@"
load_env_and_secrets

# ── 默认变量 ──────────────────────────────────────────────────
ADMIN_WEB_IMAGE="${ADMIN_WEB_IMAGE:-zhiyu-admin-web}"
ADMIN_WEB_TAG="${ADMIN_WEB_TAG:-${PROJECT_VERSION:-latest}}"
SKIP_BUILD="${SKIP_BUILD:-false}"
SKIP_PUSH="${SKIP_PUSH:-true}"

if [ -n "${DOCKER_REGISTRY:-}" ]; then
    ADMIN_WEB_IMAGE_FULL="${DOCKER_REGISTRY}/${ADMIN_WEB_IMAGE}:${ADMIN_WEB_TAG}"
else
    ADMIN_WEB_IMAGE_FULL="${ADMIN_WEB_IMAGE}:${ADMIN_WEB_TAG}"
fi

# ── 核心构建与灌入逻辑 ──────────────────────────────────────────
build_frontend_image() {
    if [ "$SKIP_BUILD" = "true" ]; then
        log_warn "检测到 SKIP_BUILD=true，跳过前端 Docker 镜像构建"
        return 0
    fi

    log_step "开始构建前端 Docker 镜像: ${ADMIN_WEB_IMAGE_FULL} ..."

    cd "$PROJECT_ROOT"

    if [ ! -d "frontend/dist" ]; then
        log_error "未找到 frontend/dist/ 构建产物！"
        log_error "请先在本地开发端执行: cd frontend && npm ci && npm run build"
        log_error "或通过 CI 流水线构建产物后 rsync 到本机"
        exit 1
    fi

    if [ ! -f "frontend/Dockerfile" ]; then
        log_error "未找到 frontend/Dockerfile"
        exit 1
    fi

    log_info "发现前端构建产物: frontend/dist/ ($(du -sh frontend/dist/ | cut -f1))"

    # Docker 构建
    local arch
    arch=$(uname -m | sed 's/x86_64/amd64/;s/aarch64/arm64/')
    log_info "Docker 构建前端 Nginx 运行时镜像 (芯片架构: ${arch})..."
    docker build -t "$ADMIN_WEB_IMAGE_FULL" -f frontend/Dockerfile frontend/
    log_info "  ✓ Docker 镜像构建成功: ${ADMIN_WEB_IMAGE_FULL}"

    # 镜像推送或本地 Containerd 灌入
    if [ "$SKIP_PUSH" = "true" ]; then
        log_info "检测到 SKIP_PUSH=true，跳过 Docker Registry 远程推送"

        if [ "$ENV" = "kubeadm" ]; then
            log_step "正在将前端镜像物理导入 Containerd (命名空间: k8s.io) ..."

            local tmp_tar="${TMPDIR:-/tmp}/zhiyu-admin-web-$$.tar"
            log_info "  1/3 正在导出镜像为本地 tar 归档..."
            docker save "$ADMIN_WEB_IMAGE_FULL" -o "$tmp_tar"

            log_info "  2/3 正在将 tar 归档物理压入 containerd 命名空间..."
            sudo ctr -n k8s.io images import "$tmp_tar"

            log_info "  3/3 清理临时 tar 缓存文件..."
            rm -f "$tmp_tar"

            log_info "  ✓ 前端镜像物理压入 Containerd 成功！"
        fi
    else
        log_info "正在推送前端镜像至 Registry: ${ADMIN_WEB_IMAGE_FULL}..."
        docker push "$ADMIN_WEB_IMAGE_FULL"
        log_info "  ✓ 前端镜像推送成功"
    fi

    log_info "前端镜像构建与自举处理全部完成 ✓"
}

build_frontend_image
