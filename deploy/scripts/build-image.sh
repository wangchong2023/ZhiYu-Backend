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
DOCKER_IMAGE="${DOCKER_IMAGE-zhiyu-backend}"
DOCKER_TAG="${DOCKER_TAG:-${PROJECT_VERSION_FULL:-latest}}"
IMAGE_PULL_POLICY="${IMAGE_PULL_POLICY-IfNotPresent}"
SKIP_BUILD="${SKIP_BUILD-false}"
SKIP_PUSH="${SKIP_PUSH-true}"

# 计算镜像完整引用路径
if [ -n "${DOCKER_REGISTRY}" ]; then
    IMAGE_FULL="${DOCKER_REGISTRY}/${DOCKER_IMAGE}:${DOCKER_TAG}"
else
    IMAGE_FULL="${DOCKER_IMAGE}:${DOCKER_TAG}"
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

    log_step "开始编译/构建 Docker 镜像: ${IMAGE_FULL} ..."
    
    cd "$PROJECT_ROOT"
    local jar_file
    jar_file=$(find backend/zhiyu-server/target -maxdepth 1 -name "zhiyu-server-*.jar" 2>/dev/null | head -n 1 || echo "")
    
    if [ -z "$jar_file" ] || [ ! -f "$jar_file" ]; then
        log_error "未找到后端胖 JAR 编译产物，构建镜像失败！"
        log_error "请先在本地开发端执行打包编译命令: mvn clean package -DskipTests"
        exit 1
    fi
    log_info "发现后端胖 JAR 产物: $jar_file"

    # 资深架构师分层缓存优化说明:
    # 传统的 `ADD jar_file.jar` 会导致每次业务代码变动时，整个庞大的 100MB+ JAR 镜像层完全失效，
    # 造成高昂的带宽与存储浪费。此处引入 Spring Boot 官方推荐的 layertools 依赖包离线提取机制：
    #   - dependencies: 第三方开源框架依赖，极低变更频次
    #   - spring-boot-loader: Spring Boot 自研装载器，几乎不变
    #   - snapshot-dependencies: 快照依赖，中度频次
    #   - application: 核心业务编译类与资源，高频变更（仅几百 KB）
    # 经由此拆分，Docker 构建能在高频部署中完美复用 95% 以上的缓存层，极大缩短构建与传输时间。
    log_info "正在使用 Spring Boot layertools 提取分层依赖目录..."
    rm -rf backend/zhiyu-server/target/extracted
    java -Djarmode=layertools -jar "$jar_file" extract --destination backend/zhiyu-server/target/extracted
    log_info "  ✓ 分层依赖提取成功"

    # 执行 Docker 镜像构建
    local build_args=("-t" "$IMAGE_FULL")
    local arch
    arch=$(uname -m | sed 's/x86_64/amd64/;s/aarch64/arm64/')
    build_args+=("--build-arg" "TARGETARCH=${arch}")

    log_info "Docker 构建后端运行时镜像 (芯片架构: ${arch})..."
    
    if [ "$ENV" = "kubeadm" ]; then
        docker build "${build_args[@]}" -f deploy/docker/Dockerfile.kubeadm "$PROJECT_ROOT"
    else
        docker build "${build_args[@]}" -f Dockerfile "$PROJECT_ROOT"
    fi
    log_info "  ✓ Docker 镜像构建成功: ${IMAGE_FULL}"

    # 镜像推送或本地 Containerd 灌入
    if [ "$SKIP_PUSH" = "true" ]; then
        log_info "检测到 SKIP_PUSH=true，跳过 Docker Registry 远程推送"
        
        if [ "$ENV" = "kubeadm" ]; then
            # 物理压入 containerd 内部存储空间原理解析:
            # 在 kubeadm 自建单机集群环境中，K8s 容器运行时不再依赖宿主机的 Docker 守护进程，
            # 而是直接使用底层的 containerd 引擎。K8s 调度的 Pod 只能在 containerd 的 `k8s.io` 命名空间下寻找镜像。
            # 此处通过 `docker save` 物理导出 tar 包，并利用 sudo 强行执行 `ctr -n k8s.io images import`
            # 将镜像直接灌入 Kubernetes 容器调度的核心存储环，彻底免去了自建 Registry 镜像站的繁重成本。
            log_step "正在将构建完成的镜像物理导入 Containerd (命名空间: k8s.io) ..."
            
            local tmp_tar
            tmp_tar="${TMPDIR:-/tmp}/zhiyu-backend-$$.tar"
            
            log_info "  1/3 正在导出镜像为本地 tar 归档: $tmp_tar ..."
            docker save "$IMAGE_FULL" -o "$tmp_tar"
            
            log_info "  2/3 正在将 tar 归档物理压入 containerd 命名空间..."
            sudo ctr -n k8s.io images import "$tmp_tar"
            
            log_info "  3/3 清理临时 tar 缓存文件..."
            rm -f "$tmp_tar"
            
            log_info "  ✓ 镜像物理压入 Containerd 成功！"
        fi
    else
        log_info "正在将构建成功的镜像推送至指定 Registry 仓库..."
        docker push "$IMAGE_FULL"
        log_info "  ✓ 镜像推送至 Registry 成功"
    fi

    log_info "后端镜像构建与自举处理全部完成 ✓"
}

build_and_import
