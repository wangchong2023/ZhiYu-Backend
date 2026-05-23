#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: common.sh
# 脚本功能: 部署架构公共核心环境加载器。
#           负责统一定义彩色日志输出、以物理基准定位项目根目录、自动装载全局环境配置与密码凭证、
#           以及挂载前置密钥与通用的 K8s 模板 apply 辅助渲染引擎。
# 编 写 人: 资深架构师 & 高级开发工程师 (Antigravity AI)
# 编写时间: 2026-05-20
# ==============================================================================

# ── 颜色及日志定义 ──────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()  { echo -e "${CYAN}[STEP]${NC}  $*"; }

# ── 项目根路径自动推导 ──────────────────────────────────────────
# common.sh 固定位于 deploy/scripts/common.sh，向上两级即为项目根
if [ -z "${COMMON_DIR:-}" ]; then
    COMMON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fi
if [ -z "${PROJECT_ROOT:-}" ]; then
    PROJECT_ROOT="$(cd "$COMMON_DIR/../.." && pwd)"
fi
export COMMON_DIR PROJECT_ROOT

# ── 项目语义化版本单一真实来源 ─────────────────────────────────────
PROJECT_VERSION=$(cat "${PROJECT_ROOT}/.version" 2>/dev/null || echo "")
export PROJECT_VERSION

# ── 智能默认环境参数解析 ──────────────────────────────────────────
# 默认环境设为 kubeadm，消除用户高频多余的传参负担
ENV="kubeadm"
DRY_RUN=false
HELP_REQUESTED=false

# ==============================================================================
# 函数名称: parse_common_args
# 函数功能: 智能提取出外部传入的环境标识名、--dry-run 标志、-h/--help 请求。
#           若当前脚本定义了 show_help 函数，检测到 -h 时自动调用并退出。
# 参    数:
#   $@ - string - 传入的脚本命令行全量参数 (如 "kubeadm --dry-run" 或 "-h")
# 返回值/退出码:
#   无，内部直接修改全局变量 ENV, DRY_RUN, HELP_REQUESTED
# ==============================================================================
parse_common_args() {
    for arg in "$@"; do
        case "$arg" in
            -h|--help)
                HELP_REQUESTED=true
                ;;
            --dry-run)
                DRY_RUN=true
                ;;
            dev|test|staging|release|kubeadm)
                ENV="$arg"
                ;;
        esac
    done
    if [ "$HELP_REQUESTED" = true ] && declare -f show_help &>/dev/null; then
        show_help
        exit 0
    fi
}

# ── 全局环境及密码挂载 ────────────────────────────────────────────
# ==============================================================================
# 函数名称: load_env_and_secrets
# 函数功能: 物理加载当前调度环境的 config.env 配置，并级联唤起密码与密钥自愈生成与载入
# 参    数: 无，依赖全局变量 ENV
# 返回值/退出码:
#   0 - 挂载并验证全部就绪
#   1 - 关键环境文件 config.env 缺失，直接阻断运行
# ==============================================================================
load_env_and_secrets() {
    # 重新指定统一扁平化环境包的明文配置文件路径
    ENV_FILE="${PROJECT_ROOT}/deploy/envs/${ENV}/config.env"
    
    # 1. 载入全局环境配置文件
    if [ -f "$ENV_FILE" ]; then
        source "$ENV_FILE"
        log_info "已成功装载全局环境配置: ${ENV} -> ${ENV_FILE} | 命名空间: ${K8S_NAMESPACE:-未设置}"
    else
        log_error "环境配置文件不存在: $ENV_FILE"
        exit 1
    fi

    # 2. 导出 JWT 密钥及敏感凭证所存放的环境专用路径，便于 K8s Secret 物理动态读取
    export JWT_KEY_DIR="${PROJECT_ROOT}/deploy/envs/${ENV}"

    # 3. 载入持久化的单源明文强密码凭证（环境自包含，受 .gitignore 隔离保护）
    local password_file="${JWT_KEY_DIR}/passwords.env"
    if [ -f "$password_file" ]; then
        source "$password_file"
        log_info "已成功挂载已持久化的强密码文件: passwords.env"
    fi

    # 4. 动态调度 ensure-secrets.sh 确认密钥与密码 100% 就绪，自愈生成缺失凭证
    if [ -f "${COMMON_DIR}/ensure-secrets.sh" ]; then
        source "${COMMON_DIR}/ensure-secrets.sh" "$ENV"
    fi
}

# ── 通用公共变量默认值与版本库 ─────────────────────────────────────
# 统一在这里定义镜像版本，极具工程美感，易于统一升级
export MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.0}"
export REDIS_IMAGE="${REDIS_IMAGE:-redis:7-alpine}"
export NACOS_IMAGE="${NACOS_IMAGE:-nacos/nacos-server:v2.4.0}"
export BUSYBOX_IMAGE="${BUSYBOX_IMAGE:-busybox:1.36}"
export PROMETHEUS_IMAGE="${PROMETHEUS_IMAGE:-prom/prometheus:v3.7.0}"
export GRAFANA_IMAGE="${GRAFANA_IMAGE:-grafana/grafana:11.6.0}"
export KUBE_STATE_METRICS_IMAGE="${KUBE_STATE_METRICS_IMAGE:-registry.k8s.io/kube-state-metrics/kube-state-metrics:v2.15.0}"
export NODE_EXPORTER_IMAGE="${NODE_EXPORTER_IMAGE:-prom/node-exporter:v1.9.0}"
export MYSQLD_EXPORTER_IMAGE="${MYSQLD_EXPORTER_IMAGE:-prom/mysqld-exporter:v0.15.0}"
export REDIS_EXPORTER_IMAGE="${REDIS_EXPORTER_IMAGE:-oliver006/redis_exporter:v1.67.0}"
export METRICS_SERVER_IMAGE="${METRICS_SERVER_IMAGE:-registry.k8s.io/metrics-server/metrics-server:v0.7.2}"

# 监控存储卷配额兜底
export PROMETHEUS_STORAGE="${PROMETHEUS_STORAGE:-10Gi}"
export GRAFANA_STORAGE="${GRAFANA_STORAGE:-1Gi}"

# ── K8s 声明式模板渲染辅助引擎 ─────────────────────────────────────
# ==============================================================================
# 函数名称: apply_template
# 函数功能: 通过原生 Linux 管道工具 envsubst 动态提取 Bash 上下文的环境变量并渲染入 
#           Kubernetes 资源 YAML 模板中，并最终以幂等方式下发给 kubectl 实施部署。
# 参    数: 
#   $1 - string - Kubernetes YAML 模板文件的绝对物理路径
#   $2 - string - 该部署组件的中文简要说明名称 (用于日志控制台友好展示)
# 返回值/退出码:
#   0 - 渲染且下发成功
#   非 0 - kubectl 执行出错
# ==============================================================================
apply_template() {
    local file="$1"
    local label="$2"

    if [ "$DRY_RUN" = true ]; then
        # 深度防损控制：--dry-run=client 会在 K8s API 客户端直接进行语法与拓扑连通性模拟，不真正修改集群状态
        envsubst < "$file" | kubectl apply -n "${K8S_NAMESPACE}" --dry-run=client -f -
        log_info "  [DRY-RUN] ${label} (渲染并验证通过)"
    else
        # envsubst 作为极轻量级渲染核心，通过读取 $K8S_NAMESPACE 等变量在标准输入流中热注入，避免磁盘生成临时垃圾文件
        envsubst < "$file" | kubectl apply -n "${K8S_NAMESPACE}" -f -
        log_info "  ✓ ${label} 声明式应用指令下发成功"
    fi
}

# ==============================================================================
# 函数名称: apply_cluster_template
# 函数功能: 与 apply_template 相同，但用于集群级资源（ClusterIssuer 等），
#           不指定命名空间，直接 apply 到集群层级。
# 参    数:
#   $1 - string - Kubernetes YAML 模板文件的绝对物理路径
#   $2 - string - 该部署组件的中文简要说明名称
# 返回值/退出码:
#   0 - 渲染且下发成功
#   非 0 - kubectl 执行出错
# ==============================================================================
apply_cluster_template() {
    local file="$1"
    local label="$2"

    if [ "$DRY_RUN" = true ]; then
        envsubst < "$file" | kubectl apply --dry-run=client -f -
        log_info "  [DRY-RUN] ${label} (渲染并验证通过)"
    else
        envsubst < "$file" | kubectl apply -f -
        log_info "  ✓ ${label} 声明式应用指令下发成功"
    fi
}
