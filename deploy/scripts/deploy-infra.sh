#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: deploy-infra.sh
# 脚本功能: 专职负责 K8s 集群内基础设施（MySQL StatefulSet + Redis + Nacos）的一键自举部署。
#           等待 MySQL 就绪并调用 init-db.sh 提前初始化 Nacos 数据库结构，解决启动循环崩溃顽疾。
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

INFRA_DIR="${PROJECT_ROOT}/deploy/manifests/01-infra"

# ── 核心自举部署函数 ──────────────────────────────────────────
# ==============================================================================
# 函数名称: deploy_infrastructure
# 函数功能: 依次创建命名空间，幂等拉起 MySQL、Redis StatefulSet，并在 MySQL 就绪后，
#           通过自愈机制触发 init-db.sh 自动刷新库表，最终安全拉起 Nacos 注册中心。
# 参    数: 无，依赖全局及加载的环境变量
# 返回值/退出码:
#   0 - 基础设施全生态自建部署并 Ready 成功
#   1 - 部署或阻塞等待超时失败
# ==============================================================================
deploy_infrastructure() {
    log_step "开始部署 ${ENV} 基础设施生态栈..."

    # 1. 幂等创建命名空间，防止命名空间重复创建报错打断部署流程
    if ! kubectl get namespace "${K8S_NAMESPACE}" &>/dev/null; then
        if [ "$DRY_RUN" = false ]; then
            kubectl create namespace "${K8S_NAMESPACE}"
        fi
        log_info "Namespace ${K8S_NAMESPACE} 创建成功"
    else
        log_info "Namespace ${K8S_NAMESPACE} 已存在，继续执行部署"
    fi

    # 2. 部署 MySQL (StatefulSet + Headless Service 提供稳定的 DNS 解析)
    if [ -n "${MYSQL_STORAGE:-}" ]; then
        log_info "正在部署 MySQL (StatefulSet + Headless SVC)..."
        apply_template "${INFRA_DIR}/mysql-statefulset.yaml" "MySQL"
    else
        log_info "跳过内嵌 MySQL，使用外部云数据库: ${MYSQL_HOST}"
    fi

    # 3. 部署 Redis (单节点，AOF与RDB双写持久化安全策略)
    if [ -n "${REDIS_STORAGE:-}" ]; then
        log_info "正在部署 Redis (单节点)..."
        apply_template "${INFRA_DIR}/redis.yaml" "Redis"
    else
        log_info "跳过内嵌 Redis，使用外部缓存实例: ${REDIS_HOST}"
    fi

    # 4. 深度架构自愈设计: 根除 Nacos 与 MySQL 之间的拓扑启动死锁
    # 架构原理解析: 
    # Nacos 使用外部数据库存储（如 MySQL）时，在首次拉起期间，Nacos Pod 内部会同步建立连接，
    # 并校验 `config_info`、`users` 等 12 张核心配置表的存在性。如果此时 MySQL 容器还在进行磁盘初始化或
    # 尚未完成 init-db 建表，Nacos 容器启动脚本会因为找不到表而报错奔溃，导致 Pod 进入 CrashLoopBackOff。
    # 为此，我们设计了强依赖阻塞机制：
    #   - 第一步：使用 `kubectl wait --for=condition=ready` 阻塞等待 MySQL Pod 完全 Ready 亮起。
    #   - 第二步：异步或同步执行 `init-db.sh` 脚本，连通 K8s 宿主和 MySQL，无损导入 `nacos-mysql-schema.sql`，
    #            并将 Nacos 超级管理员密码哈希直接注入数据库。
    #   - 第三步：表结构完成初始化建库闭环后，再行渲染发布 Nacos YAML，确保 Nacos 一次性平滑启动。
    if [ -n "${MYSQL_STORAGE:-}" ] && [ "$DRY_RUN" = false ]; then
        log_info "阻塞等待 MySQL StatefulSet 完全就绪 (最多等待 90 秒)..."
        kubectl wait --for=condition=ready pod -l app=mysql -n "${K8S_NAMESPACE}" --timeout=90s 2>/dev/null \
            || log_warn "MySQL Pod 未能在 90s 内就绪，将尝试继续执行..."

        if [ -f "${SCRIPT_DIR}/init-db.sh" ]; then
            log_info "MySQL 已经就绪，正在调用数据库初始化模块，提前建好 Nacos 库表结构..."
            if bash "${SCRIPT_DIR}/init-db.sh" "$ENV"; then
                log_info "  ✓ Nacos 依赖数据库及表结构提前初始化成功！"
            else
                log_warn "  ⚠️ 数据库初始化脚本返回异常，将尝试直接拉起 Nacos..."
            fi
        else
            log_warn "  ⚠️ 数据库初始化模块 init-db.sh 不存在，跳过提前初始化 Nacos 库表"
        fi
    fi

    # 5. 部署 Nacos (此时 MySQL 已经完成建库建表自举，彻底根除 404/崩溃)
    if [ -n "${NACOS_STORAGE:-}" ]; then
        log_info "正在部署 Nacos 服务注册与配置中心..."
        apply_template "${INFRA_DIR}/nacos.yaml" "Nacos"
        
        # 阻塞等待所有组件亮起，确保后面的 app 微服务拉起时能成功向 Nacos 服务注册和拉取配置
        if [ "$DRY_RUN" = false ]; then
            log_info "阻塞等待基础设施 Pod 全部完全就绪 (最多等待 90 秒)..."
            kubectl wait --for=condition=ready pod --all -n "${K8S_NAMESPACE}" --timeout=90s 2>/dev/null \
                || log_warn "部分基础设施 Pod 可能仍在最终启动初始化中，不影响主部署进程"
        fi
    else
        log_info "跳过内嵌 Nacos，使用外部配置中心: ${NACOS_HOST}"
    fi

    log_info "基础设施生态栈一键自举部署完成 ✓"
}

# 运行主函数
deploy_infrastructure
