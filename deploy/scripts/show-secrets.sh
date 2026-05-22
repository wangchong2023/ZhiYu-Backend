#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: show-secrets.sh
# 脚本功能: 密钥与敏感凭证显示工具。
#           负责从本地密码文件以及 K8s Secrets 中安全地读取并以整齐的格式显示
#           所有基础设施组件（MySQL, Redis, Nacos）和监控大盘的运维管理密码。
# 编 写 人: 资深架构师 & 高级开发工程师 (Antigravity AI)
# 编写时间: 2026-05-20
# ==============================================================================

set -euo pipefail

# ── 引入公共核心加载器 ──────────────────────────────────────────
# 自动定位 common.sh 并挂载，获得色彩输出、基准路径计算及环境密码自适应
SCRIPTS_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
if [ -f "${SCRIPTS_DIR}/common.sh" ]; then
    source "${SCRIPTS_DIR}/common.sh"
else
    echo -e "\033[0;31m[ERROR]\033[0m 无法加载公共核心脚本 common.sh"
    exit 1
fi

# ── 初始化参数与环境加载 ────────────────────────────────────────
parse_common_args "$@"
load_env_and_secrets

# ==============================================================================
# 函数名称: show_passwords
# 函数功能: 核心展示逻辑，分别展示本地密码文件和 K8s 实时的 Secret 密码
# 参    数: 无
# 返 回 值: 无
# ==============================================================================
show_passwords() {
    log_step "正在查询环境 [${ENV}] 下的各组件敏感密码凭证..."

    local namespace="${K8S_NAMESPACE:-zhiyu-dev}"

    # 1. 优先读取并展示本地密码文件
    local password_file="${PROJECT_ROOT}/deploy/envs/${ENV}/passwords.env"
    if [ -f "$password_file" ]; then
        echo -e "\n${CYAN}── [本地单源明文密码文件] ──────────────────────────────────────────${NC}"
        echo " 路径: ${password_file}"
        echo ""
        
        printf "  %-24s %-20s %s\n" "组件" "用户名" "密码"
        printf "  %-24s %-20s %s\n" "────" "────" "────"
        printf "  %-24s %-20s %s\n" "MySQL (root)" "root" "${MYSQL_ROOT_PASSWORD:-<未设置>}"
        printf "  %-24s %-20s %s\n" "MySQL (应用)" "${MYSQL_USER:-zhiyu}" "${MYSQL_PASSWORD:-<未设置>}"
        printf "  %-24s %-20s %s\n" "Redis" "<无用户名>" "${REDIS_PASSWORD:-<未设置>}"
        printf "  %-24s %-20s %s\n" "Nacos" "nacos" "${NACOS_PASSWORD:-<未设置>}"
        printf "  %-24s %-20s %s\n" "Grafana" "admin" "${GRAFANA_PASSWORD:-<未设置>}"
        printf "  %-24s %-20s %s\n" "后台管理员 (admin)" "admin" "${ADMIN_PASSWORD:-<未设置>}"
        echo ""
        echo "  Nacos 身份校验 Key: ${NACOS_IDENTITY_KEY:-serverIdentity}"
        echo "  Nacos 身份校验 Value: ${NACOS_IDENTITY_VALUE:-<未设置>}"
        echo ""
    else
        log_warn "未在本地检测到密码文件: ${password_file}"
    fi

    # 2. 从 K8s 集群的 Secrets 中实时解密读取
    echo -e "${CYAN}── [Kubernetes 实时 Secrets (${namespace})] ──────────────────────────${NC}"
    echo ""

    if kubectl get namespace "$namespace" &>/dev/null; then
        # MySQL 密码提取
        local mysql_pw
        mysql_pw=$(kubectl get secret mysql-secret -n "$namespace" -o jsonpath='{.data.password}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
        if [ -n "$mysql_pw" ]; then
            echo "  MySQL (zhiyu 用户):  ${mysql_pw}"
        else
            echo "  MySQL (zhiyu 用户):  <Secret 不存在>"
        fi
        
        local mysql_root_pw
        mysql_root_pw=$(kubectl get secret mysql-secret -n "$namespace" -o jsonpath='{.data.root-password}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
        [ -n "$mysql_root_pw" ] && echo "  MySQL (root 用户):   ${mysql_root_pw}"

        # Redis 密码提取
        local redis_pw
        redis_pw=$(kubectl get secret redis-secret -n "$namespace" -o jsonpath='{.data.password}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
        if [ -n "$redis_pw" ]; then
            echo "  Redis:              ${redis_pw}"
        else
            echo "  Redis:              <Secret 不存在>"
        fi

        # Nacos 密码提取
        local nacos_pw
        nacos_pw=$(kubectl get secret nacos-secret -n "$namespace" -o jsonpath='{.data.password}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
        if [ -n "$nacos_pw" ]; then
            echo "  Nacos (admin 用户):  ${nacos_pw}"
        else
            echo "  Nacos (admin 用户):  <Secret 不存在>"
        fi

        # 应用级微服务连接 Secrets 提取
        local app_db_pw
        app_db_pw=$(kubectl get secret zhiyu-backend-secret -n "$namespace" -o jsonpath='{.data.SPRING_DATASOURCE_PASSWORD}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
        local app_redis_pw
        app_redis_pw=$(kubectl get secret zhiyu-backend-secret -n "$namespace" -o jsonpath='{.data.SPRING_DATA_REDIS_PASSWORD}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
        
        echo ""
        echo "  微服务应用解密连接凭证:"
        if [ -n "$app_db_pw" ]; then
            echo "    Spring DB 密码:    ${app_db_pw}"
        else
            echo "    Spring DB 密码:    <未压入>"
        fi
        if [ -n "$app_redis_pw" ]; then
            echo "    Spring Redis 密码: ${app_redis_pw}"
        else
            echo "    Spring Redis 密码: <未压入>"
        fi
    else
        log_warn "命名空间 [${namespace}] 不存在，无法读取 K8s Secrets 状态"
    fi

    # 3. 从监控命名空间读取 Grafana 密码
    echo -e "\n${CYAN}── [监控系统凭证 (monitoring)] ──────────────────────────────────────${NC}"
    local grafana_pw
    grafana_pw=$(kubectl get secret grafana-secret -n monitoring -o jsonpath='{.data.admin-password}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
    if [ -n "$grafana_pw" ]; then
        echo "  Grafana (admin 用户): ${grafana_pw}"
    else
        echo "  Grafana (admin 用户): <Secret 不存在或 monitoring 空间未创建>"
    fi

    echo ""
    log_warn "======================================================================"
    log_warn "⚠️  特别提示: 以上密码为敏感信息，请勿在公共或非安全媒介中传播！"
    log_warn "======================================================================"
    echo ""
}

# ── 执行展示 ────────────────────────────────────────────────────
show_passwords
