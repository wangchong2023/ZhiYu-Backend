#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: deploy.sh
# 脚本功能: 统一分发主入口网关。
#           负责解析全局环境参数（默认 kubeadm 降维自适应）、部署动作（默认 all），
#           并作为轻量级网关将具体执行路由分发给各个高内聚的原子运维子脚本。
# 编 写 人: 资深架构师 & 高级开发工程师 (Antigravity AI)
# 编写时间: 2026-05-20
# 用    法:
#           ./deploy/deploy.sh [env] [action] [--dry-run]
#           支持参数乱序，如果未输入 env 默认使用 "kubeadm"，未输入 action 默认使用 "all"
# ==============================================================================

set -euo pipefail

# ── 引入公共核心加载器 ──────────────────────────────────────────
# 自动定位 common.sh 并挂载，获得色彩输出、基准路径计算及环境自适应
DEPLOY_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SCRIPTS_DIR="${DEPLOY_DIR}/scripts"

if [ -f "${SCRIPTS_DIR}/common.sh" ]; then
    source "${SCRIPTS_DIR}/common.sh"
else
    echo -e "\033[0;31m[ERROR]\033[0m 无法加载公共核心脚本 common.sh"
    exit 1
fi

# ── 智能参数解析与兜底 ────────────────────────────────────────────
# 深度架构设计原理解析:
# 1. 混淆乱序智能容错: 传统的 Shell 脚本参数解析往往依赖严格的顺序定义（$1 必须是 env，$2 必须是 action）。
#    此处的循环参数智能检测策略，通过模式匹配，自适应提取出 env 参数和 action 参数，无论用户以何种顺序传入
#    （如 `./deploy.sh deploy dev --dry-run` 亦能完美识别），大幅度提升了 CI/CD 及手工维护的交互友好度。
# 2. 状态占位符及兜底逻辑: 若未识别到目标环境变量与特定动作，系统会安全且鲁棒地触发用法提示，以 1 状态码中断错误配置的下发。
ENV=""
ACTION=""
DRY_RUN_FLAG=""

for arg in "$@"; do
    case "$arg" in
        --dry-run)
            DRY_RUN=true
            DRY_RUN_FLAG="--dry-run"
            ;;
        dev|test|staging|release|kubeadm)
            ENV="$arg"
            ;;
        check|infra|init|build|deploy|frontend-build|frontend-deploy|monitoring|cleanup|all|status|show-secrets|cert-manager)
            ACTION="$arg"
            ;;
        *)
            log_error "无效参数: $arg"
            echo "用法: $0 [env] [action] [--dry-run]"
            echo "  env:     dev | test | staging | release | kubeadm (默认: kubeadm)"
            echo "  action:  check | infra | init | build | deploy | frontend-build | frontend-deploy | monitoring | cleanup | cert-manager | all | status | show-secrets (默认: all)"
            echo "  --dry-run: 仅验证（kubectl --dry-run=client），不真正部署"
            echo ""
            echo "示例: $0 kubeadm all            # 一键部署前后端全部"
            echo "      $0 kubeadm frontend-build # 仅构建前端 Docker 镜像"
            echo "      $0 kubeadm frontend-deploy# 仅部署前端 K8s 资源"
            echo "      $0 dev deploy --dry-run   # 模拟部署"
            exit 1
            ;;
    esac
done

# 智能降维自举默认值:
# 当运维人员未输入目标环境与具体行为时，系统自愈推导为 kubeadm 物理单节点环境和一键式 all 全链路部署动作。
ENV="${ENV:-kubeadm}"
ACTION="${ACTION:-all}"

# 组装分发给子脚本的参数数组
SUB_ARGS=("$ENV")
if [ -n "$DRY_RUN_FLAG" ]; then
    SUB_ARGS+=("$DRY_RUN_FLAG")
fi

# ── 打印欢迎标语 ────────────────────────────────────────────────
echo -e "${CYAN}================================================${NC}"
echo -e "       智宇后端系统 (ZhiYu-Backend) 统一分发网关"
echo -e "       目标环境: ${YELLOW}${ENV}${NC}  |  调度动作: ${YELLOW}${ACTION}${NC}"
echo -e "${CYAN}================================================${NC}\n"

# ── 统一分发网关路由 ────────────────────────────────────────────
# ==============================================================================
# 函数名称: run_sub_script
# 函数功能: 动态路由并执行指定的原子运维脚本，并同步透传目标环境及 `--dry-run` 标志
# 参    数: 
#   $1 - string - 原子脚本文件名 (例如: 'check-env.sh', 'deploy-infra.sh')
#   $2 - string - 该步骤的中文可读性任务描述 (例如: '前置环境与 K8s 连通预检')
# 返回值/退出码:
#   0 - 执行成功
#   非 0 - 原子脚本异常退出，主脚本级联捕获，强行报错打断当前持续集成部署流
# ==============================================================================
run_sub_script() {
    local script_name="$1"
    local desc="$2"
    local script_path="${SCRIPTS_DIR}/${script_name}"

    if [ -f "$script_path" ]; then
        log_step "==> [开始执行] ${desc} (${script_name}) ..."
        # 赋予执行权限以防万一，确保其具备宿主机直接解释执行的能力
        chmod +x "$script_path"
        # 核心级联错误拦截机制: 保持退出码，级联向上抛出。由于设置了 set -euo pipefail，
        # 任何子脚本抛出非0退出码，主进程将立即被安全中断，确保了故障的现场保留。
        "$script_path" "${SUB_ARGS[@]}"
        log_info "==> [成功结束] ${desc} ✓\n"
    else
        log_error "找不到原子子脚本: ${script_path}"
        exit 1
    fi
}

# ── 动作路由核心状态机 ──────────────────────────────────────────
case "$ACTION" in
    check)
        run_sub_script "check-env.sh" "前置环境与 K8s 连通预检"
        ;;
    infra)
        run_sub_script "check-env.sh" "前置环境与 K8s 连通预检"
        run_sub_script "deploy-infra.sh" "部署 MySQL/Redis/Nacos 基础设施"
        ;;
    init)
        run_sub_script "check-env.sh" "前置环境与 K8s 连通预检"
        run_sub_script "init-db.sh" "自举建表与 Nacos 配置初始化推送"
        ;;
    build)
        run_sub_script "check-env.sh" "前置环境与 K8s 连通预检"
        run_sub_script "build-image.sh" "代码 Maven 编译与 Containerd 镜像灌入"
        ;;
    frontend-build)
        run_sub_script "check-env.sh" "前置环境与 K8s 连通预检"
        run_sub_script "build-frontend.sh" "前端 Nginx 镜像构建与 Containerd 灌入"
        ;;
    deploy)
        run_sub_script "check-env.sh" "前置环境与 K8s 连通预检"
        run_sub_script "deploy-app.sh" "部署微服务应用到 Kubernetes"
        ;;
    frontend-deploy)
        run_sub_script "check-env.sh" "前置环境与 K8s 连通预检"
        run_sub_script "deploy-frontend.sh" "部署前端 admin-web 到 Kubernetes"
        ;;
    monitoring)
        run_sub_script "check-env.sh" "前置环境与 K8s 连通预检"
        run_sub_script "deploy-monitor.sh" "部署 Prometheus/Grafana 监控栈"
        ;;
    cleanup)
        run_sub_script "check-env.sh" "前置环境与 K8s 连通预检"
        run_sub_script "cleanup.sh" "资源一键物理清理与命名空间销毁"
        ;;
    status)
        run_sub_script "status-probe.sh" "系统运行状态与深度健康诊断探测"
        ;;
    show-secrets)
        run_sub_script "show-secrets.sh" "显示环境敏感密码凭证"
        ;;
    cert-manager)
        run_sub_script "check-env.sh" "前置环境与 K8s 连通预检"
        run_sub_script "install-cert-manager.sh" "安装 cert-manager 到集群"
        ;;
    all)
        # 一键集成部署全链路（基础设施 → 数据库 → 3 微服务镜像 → K8s → 监控）
        # 前端 SPA 已集成在 ufp-gateway 镜像中，无需单独构建/部署
        run_sub_script "check-env.sh" "1. 前置环境与 K8s 连通预检"
        run_sub_script "deploy-infra.sh" "2. 部署 MySQL/Redis/Nacos 基础设施"
        run_sub_script "init-db.sh" "3. 自举建表与 Nacos 配置初始化推送"
        run_sub_script "build-image.sh" "4. 3 个微服务 Maven 编译与 Containerd 镜像灌入"
        run_sub_script "deploy-app.sh" "5. 部署 3 个微服务应用到 Kubernetes"
        run_sub_script "deploy-monitor.sh" "6. 部署 Prometheus/Grafana 监控栈"

        echo -e "${GREEN}================================================${NC}"
        echo -e " 🎉 恭喜，智宇平台 3 微服务全链路一键集成部署圆满完成！"
        echo -e "    ufp-gateway:  http://<INGRESS_HOST>/        (API 网关 + SPA)"
        echo -e "    ufp-auth:     http://<INGRESS_HOST>/api/v1/auth/"
        echo -e "    zhiyu-admin:  http://<INGRESS_HOST>/api/v1/admin/"
        echo -e "${GREEN}================================================${NC}\n"

        # 自动触发状态诊断，给运维人员最直观的就绪报告
        run_sub_script "status-probe.sh" "自动触发系统状态回测诊断"
        ;;
esac
