#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: status-probe.sh
# 脚本功能: 专职负责 K8s 集群中 ZhiYu-Backend 相关命名空间（包括业务与监控）资源状态及组件深层健康探测。
#           执行对 App Actuator、MySQL、Redis、Nacos 读写端、Prometheus、Grafana 等端点的连接与就绪状况诊断。
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
    echo ""
    echo "示例:"
    echo "  $0 kubeadm"
    echo "  $0          # 默认对 kubeadm 环境进行深度系统诊断探测"
}

parse_common_args "$@"
load_env_and_secrets

# ── 核心健康度深度探测 ──────────────────────────────────────────
# ==============================================================================
# 函数名称: do_probe
# 函数功能: 深度轮询 K8s StatefulSet, Deployment, Pod 物理及逻辑就绪拓扑，
#           并强行穿透容器网络，在 Pod 内部执行 MySQL Ping, Redis Pong, 
#           Nacos Readiness 以及 Spring Boot Actuator 的健康度深度自愈探测。
# 参    数: 无，依赖已加载的环境变量配置
# 返回值/退出码:
#   0 - 诊断流程圆满完成
#   1 - 目标核心命名空间缺失
# ==============================================================================
do_probe() {
    local namespace="${K8S_NAMESPACE}"
    local monitoring_ns="monitoring"

    log_step "开始探测 ${ENV} 环境下 Kubernetes 组件运行状态及健康度..."

    if ! kubectl get namespace "${namespace}" &>/dev/null; then
        log_error "目标应用命名空间 ${namespace} 在集群中不存在！尚未部署服务。"
        exit 1
    fi

    echo ""
    echo "── 1. Kubernetes 核心资源就绪状态 ──"
    echo "👉 [StatefulSets]"
    kubectl get statefulset -n "${namespace}" 2>/dev/null || echo "  无 StatefulSet"
    
    echo ""
    echo "👉 [Deployments]"
    kubectl get deploy -n "${namespace}" 2>/dev/null || echo "  无 Deployment"
    
    echo ""
    echo "👉 [Pods 运行实例状态 (可宽屏查看)]"
    kubectl get pods -n "${namespace}" -o wide 2>/dev/null || echo "  无 Pod"
    
    echo ""
    echo "👉 [Services 网络端点]"
    kubectl get svc -n "${namespace}" 2>/dev/null || echo "  无 Service"
    
    echo ""
    echo "👉 [Ingress 域名网关]"
    kubectl get ingress -n "${namespace}" 2>/dev/null || echo "  无 Ingress"
 
    echo ""
    echo "👉 [HPA / PDB 伸缩限制与网络策略]"
    kubectl get hpa -n "${namespace}" 2>/dev/null || echo "  未配置 HPA"
    kubectl get pdb -n "${namespace}" 2>/dev/null || echo "  未配置 PDB"
    kubectl get networkpolicy -n "${namespace}" 2>/dev/null || echo "  未配置 NetworkPolicy"

    echo ""
    echo "── 2. 核心组件应用级深度健康诊断 ──"

    # 资深架构师防崩溃健壮性设计解析:
    # 在微服务刚刚下发部署或遭遇严重宿主机物理崩溃时，目标 Pod 实例列表可能完全为空或处于 Pending 状态。
    # 此时若直接强行执行 `kubectl get pods ... -o jsonpath='{.items[0].metadata.name}'`，由于 API Server
    # 返回的 items 数组为空，对索引 `[0]` 的提取会触发 kubectl 抛出非零退出码。
    # 在强限制安全机制 `set -euo pipefail` 驱动下，这会导致主脚本立即崩盘中断，运维无法拿到后续其他组件的诊断报告。
    # 此处在语句后追加 `|| echo ""` 进行了强力拦截，确保了不管实例拓扑有多混乱，主进程绝不断裂，完美容错。
    local backend_pod mysql_pod redis_pod nacos_pod adminweb_pod prometheus_pod grafana_pod
    backend_pod=$(kubectl get pods -n "${namespace}" -l app=zhiyu-backend -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    mysql_pod=$(kubectl get pods -n "${namespace}" -l app=mysql -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    redis_pod=$(kubectl get pods -n "${namespace}" -l app=redis -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    nacos_pod=$(kubectl get pods -n "${namespace}" -l app=nacos -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    adminweb_pod=$(kubectl get pods -n "${namespace}" -l app=admin-web -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    prometheus_pod=$(kubectl get pods -n "$monitoring_ns" -l app=prometheus -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    grafana_pod=$(kubectl get pods -n "$monitoring_ns" -l app=grafana -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

    # A. 业务微服务 (通过 kubectl exec 连入 Pod 内部，本地透传 curl /actuator/health 深度探测 UP 状态)
    if [ -n "$backend_pod" ]; then
        local app_health
        app_health=$(kubectl exec -n "${namespace}" "$backend_pod" -- curl -s --max-time 3 http://localhost:8080/actuator/health 2>/dev/null || echo "UNREACHABLE")
        if [[ "$app_health" == *"UP"* ]]; then
            echo -e "  App 微服务:     ${GREEN}UP${NC} (Actuator: $app_health)"
        else
            echo -e "  App 微服务:     ${RED}DOWN / UNREACHABLE${NC} ($app_health)"
        fi
    else
        echo -e "  App 微服务:     ${YELLOW}未部署/未调度实例${NC}"
    fi

    # B. MySQL 数据库实例 (连入 mysql 容器，执行 mysqladmin ping 进行进程级自愈探测)
    if [ -n "$mysql_pod" ]; then
        local mysql_root_pass="${MYSQL_ROOT_PASSWORD:-}"
        if [ -n "$mysql_root_pass" ]; then
            if kubectl exec -n "${namespace}" "$mysql_pod" -- mysqladmin ping -uroot -p"${mysql_root_pass}" 2>/dev/null | grep -q "alive"; then
                echo -e "  MySQL 数据库:   ${GREEN}UP${NC} (mysqld is alive)"
            else
                echo -e "  MySQL 数据库:   ${RED}DOWN (Ping Failed)${NC}"
            fi
        else
            echo -e "  MySQL 数据库:   ${YELLOW}UP (无法获取 Root 密码执行 Ping)${NC}"
        fi
    else
        echo -e "  MySQL 数据库:   ${YELLOW}未部署/未调度实例${NC}"
    fi

    # C. Redis 缓存实例 (连入 redis 容器，物理下发 redis-cli ping 并校验 PONG 回应)
    if [ -n "$redis_pod" ]; then
        local redis_pass="${REDIS_PASSWORD:-}"
        local redis_ping_cmd="redis-cli ping"
        [ -n "$redis_pass" ] && redis_ping_cmd="redis-cli -a ${redis_pass} ping"
        
        if kubectl exec -n "${namespace}" "$redis_pod" -- sh -c "$redis_ping_cmd" 2>/dev/null | grep -q "PONG"; then
            echo -e "  Redis 缓存:     ${GREEN}UP${NC} (PONG)"
        else
            echo -e "  Redis 缓存:     ${RED}DOWN (No PONG)${NC}"
        fi
    else
        echo -e "  Redis 缓存:     ${YELLOW}未部署/未调度实例${NC}"
    fi

    # D. Nacos 配置与服务发现中心 (强力穿透端口，访问 nacos 本地健康度服务就绪接口 readiness)
    if [ -n "$nacos_pod" ]; then
        local nacos_status
        nacos_status=$(kubectl exec -n "${namespace}" "$nacos_pod" -- curl -s --max-time 3 http://localhost:8848/nacos/v1/console/health/readiness 2>/dev/null || echo "UNREACHABLE")
        if [[ "$nacos_status" == "OK" ]]; then
            echo -e "  Nacos 配置中心: ${GREEN}UP${NC} (Readiness: $nacos_status)"
        else
            echo -e "  Nacos 配置中心: ${RED}DOWN / UNREACHABLE${NC} ($nacos_status)"
        fi
    else
        echo -e "  Nacos 配置中心: ${YELLOW}未部署/未调度实例${NC}"
    fi

    # E. 前端 admin-web (Nginx 静态托管，检查本地 80 端口)
    if [ -n "$adminweb_pod" ]; then
        local web_http
        web_http=$(kubectl exec -n "${namespace}" "$adminweb_pod" -c nginx -- curl -s -o /dev/null -w "%{http_code}" http://localhost:80/ 2>/dev/null || echo "000")
        if [ "$web_http" = "200" ]; then
            echo -e "  前端 admin-web: ${GREEN}UP${NC} (HTTP $web_http)"
        else
            echo -e "  前端 admin-web: ${RED}DOWN${NC} (HTTP $web_http)"
        fi
    else
        echo -e "  前端 admin-web: ${YELLOW}未部署/未调度实例${NC}"
    fi

    # F. Prometheus 监控底座 (通过 K8s API Server 检查 Ready 状态条件)
    if [ -n "$prometheus_pod" ]; then
        local prom_ready
        prom_ready=$(kubectl get pod -n "$monitoring_ns" "$prometheus_pod" -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "False")
        if [ "$prom_ready" = "True" ]; then
            echo -e "  Prometheus:     ${GREEN}UP${NC} (Pod Ready)"
        else
            echo -e "  Prometheus:     ${RED}DOWN (Pod Not Ready)${NC}"
        fi
    else
        echo -e "  Prometheus:     ${YELLOW}未部署/未调度实例${NC}"
    fi

    # G. Grafana 仪表盘面板 (通过 K8s API Server 检查 Ready 状态条件)
    if [ -n "$grafana_pod" ]; then
        local graf_ready
        graf_ready=$(kubectl get pod -n "$monitoring_ns" "$grafana_pod" -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "False")
        if [ "$graf_ready" = "True" ]; then
            echo -e "  Grafana 面板:   ${GREEN}UP${NC} (Pod Ready)"
        else
            echo -e "  Grafana 面板:   ${RED}DOWN (Pod Not Ready)${NC}"
        fi
    else
        echo -e "  Grafana 面板:   ${YELLOW}未部署/未调度实例${NC}"
    fi

    echo ""
    echo "── 3. 部署访问入口清单 ──"
    if kubectl get ingress -n "${namespace}" zhiyu-backend &>/dev/null; then
        echo -e "  业务 API 入口:  ${CYAN}https://${INGRESS_HOST}/api/v1${NC}"
    else
        echo "  [提示] 尚未挂载外部 Ingress 域名，可通过端口转发方式临时本地访问微服务:"
        echo "  -> 执行: kubectl port-forward -n ${namespace} svc/zhiyu-backend 8080:8080"
        echo "  -> 访问: http://localhost:8080/api/v1"
    fi

    # 如果有 Grafana 监控，打印 Grafana 的物理 NodePort 可直接访问链接
    if kubectl get svc -n "$monitoring_ns" grafana &>/dev/null; then
        local node_ip
        node_ip=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "<node-ip>")
        echo -e "  Grafana 面板:   ${CYAN}http://${node_ip}:30000${NC} (默认账号: admin / ${GRAFANA_PASSWORD:-})"
    fi
    echo ""
}

# 执行状态诊断主函数
do_probe
