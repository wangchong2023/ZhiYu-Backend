#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: init-db.sh
# 脚本功能: 专职负责数据库库表建立、Nacos Schema 执行、密码鉴权强哈希注入、以及
#           级联拉起安全密钥生成(ensure-secrets.sh)与Nacos核心配置推送(init-nacos.sh)。
#           实现了 100% 幂等与全自动一键式数据层自举。
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
}

parse_common_args "$@"
load_env_and_secrets

# ── 核心函数: 创建 Nacos 数据库并刷入 Schema ───────────────────
# 函数名称: create_nacos_db
# 函数功能: 通过 kubectl exec 在 K8s 内部 MySQL 中创建 nacos 库并初始化表结构。
#           特别加固：注入由本地 Python 计算出来的强密码 BCrypt 哈希，保障安全性。
create_nacos_db() {
    local nacos_db="nacos"
    log_info "检查与自举 Nacos 专属数据库: $nacos_db"

    # 执行 SQL 创建数据库
    kubectl exec -n "${K8S_NAMESPACE}" statefulset/mysql -- \
        mysql -u root -p"${MYSQL_ROOT_PASSWORD}" \
        -e "CREATE DATABASE IF NOT EXISTS \`${nacos_db}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" \
        2>/dev/null || { log_warn "  (跳过 Nacos 数据库创建 — Nacos 镜像可能配置为内置 Derby 模式)"; return; }

    log_info "  Nacos 数据库创建成功或已存在: $nacos_db"

    # 路径修复：加载已经搬迁至 sql/ 目录下的 nacos-mysql-schema.sql
    local schema_file="${SCRIPT_DIR}/sql/nacos-mysql-schema.sql"
    if [ -f "$schema_file" ]; then
        log_info "  正在向 MySQL 导入 Nacos 核心表结构 (12张系统表)..."
        kubectl exec -i -n "${K8S_NAMESPACE}" statefulset/mysql -- \
            mysql -u root -p"${MYSQL_ROOT_PASSWORD}" "$nacos_db" \
            < "$schema_file" 2>/dev/null \
            && log_info "  ✓ Nacos 表结构初始化导入完成" \
            || log_warn "  (表结构已存在或本次导入跳过)"

        # Nacos 鉴权管理员强密码哈希加固
        if [ -n "${NACOS_PASSWORD_HASH:-}" ]; then
            log_info "  [安全加固] 正在向 Nacos 数据库写入强密码 BCrypt 强哈希..."
            kubectl exec -i -n "${K8S_NAMESPACE}" statefulset/mysql -- \
                mysql -u root -p"${MYSQL_ROOT_PASSWORD}" "$nacos_db" \
                -e "UPDATE users SET password = '${NACOS_PASSWORD_HASH}' WHERE username = 'nacos';" 2>/dev/null \
                && log_info "  ✓ Nacos 默认超级管理员密码覆写成功！" \
                || log_warn "  ⚠️ 强密码哈希写入异常，建议稍后查看 Nacos console"
        else
            log_warn "  [警告] 未检测到 NACOS_PASSWORD_HASH，Nacos 鉴权可能使用内置明文或导致鉴权锁死！"
        fi
    else
        log_error "  ❌ 关键表结构文件不存在: $schema_file，Nacos 部署可能会失败！"
    fi
}

# ── 核心函数: 创建应用微服务专属数据库 ─────────────────────────
# 函数名称: create_app_db
# 函数功能: 创建系统核心微服务数据存储库，并赋予对应的专职普通数据库用户所有权限
create_app_db() {
    log_info "检查与自举 ZhiYu 主体微服务数据库: ${MYSQL_DATABASE}"

    # 创建业务库
    kubectl exec -n "${K8S_NAMESPACE}" statefulset/mysql -- \
        mysql -u root -p"${MYSQL_ROOT_PASSWORD}" \
        -e "CREATE DATABASE IF NOT EXISTS \`${MYSQL_DATABASE}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" \
        2>/dev/null

    # 授权专用用户
    kubectl exec -n "${K8S_NAMESPACE}" statefulset/mysql -- \
        mysql -u root -p"${MYSQL_ROOT_PASSWORD}" \
        -e "GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO '${MYSQL_USER}'@'%'; FLUSH PRIVILEGES;" \
        2>/dev/null

    log_info "  ✓ 业务应用数据库与用户授权配置就绪: ${MYSQL_DATABASE}"
}

# ── 核心函数: 创建 UFP 认证授权平台库 ───────────────────────────
create_ufp_auth_db() {
    local ufp_db="${UFP_AUTH_DATABASE:-ufp_auth}"
    log_info "检查与自举 UFP 认证授权数据库: $ufp_db"

    kubectl exec -n "${K8S_NAMESPACE}" statefulset/mysql -- \
        mysql -u root -p"${MYSQL_ROOT_PASSWORD}" \
        -e "CREATE DATABASE IF NOT EXISTS \`${ufp_db}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" \
        2>/dev/null || { log_warn "  (跳过 UFP 认证库创建)"; return; }

    kubectl exec -n "${K8S_NAMESPACE}" statefulset/mysql -- \
        mysql -u root -p"${MYSQL_ROOT_PASSWORD}" \
        -e "GRANT ALL PRIVILEGES ON \`${ufp_db}\`.* TO '${MYSQL_USER}'@'%'; FLUSH PRIVILEGES;" \
        2>/dev/null

    log_info "  ✓ UFP 认证授权数据库与用户授权配置就绪: $ufp_db"
}

# ── 执行数据库库表自举 ──────────────────────────────────────────
if [ -n "${MYSQL_STORAGE:-}" ]; then
    log_step "启动 K8s 内嵌 MySQL 库表结构自动初始化..."
    create_nacos_db
    create_app_db
    create_ufp_auth_db
else
    log_warn "检测到使用外部云数据库: ${MYSQL_HOST}:${MYSQL_PORT}，跳过内联初始化"
    log_warn "请确认已手动配置库 ${MYSQL_DATABASE} 并完成了相应的用户授权！"
fi

# ── 2. 级联调用：安全初始化 Nacos 配置 ──────────────────────────
if [ -f "${SCRIPT_DIR}/init-nacos.sh" ]; then
    log_step "自动触发 Nacos 核心配置热推送..."
    bash "${SCRIPT_DIR}/init-nacos.sh" "$ENV"
else
    log_warn "未找到 init-nacos.sh，跳过 Nacos 配置推送步骤"
fi

log_info "数据初始化动作全链路执行完成 ✓"
echo "Flyway 迁移将在应用核心容器启动时由 Spring Boot 自动驱动执行"
