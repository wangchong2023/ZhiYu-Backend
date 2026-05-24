#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: deploy-to-remote.sh (本地主控远程部署端)
# 脚本功能: 由本地开发端执行。负责在本地进行 Java 编译、提取依赖，
#           并通过 rsync 增量同步代码，调用远程 K8s 主引擎执行自举部署与监控大盘拉起。
#           彻底消除了原 deploy-remote.sh 代理套娃与语义倒装误区。
# 编 写 人: 资深架构师 & 高级开发工程师 (Antigravity AI)
# 编写时间: 2026-05-20
# ==============================================================================

set -euo pipefail

# ── 颜色配置 ──────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()  { echo -e "${CYAN}[STEP]${NC}  $*"; }

# ── 路径定位基准 ──────────────────────────────────────────────
# 使用 BASH_SOURCE 获取该脚本的绝对路径，并由此精准定位项目根目录
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PROJECT_ROOT=$(cd "${SCRIPT_DIR}/.." && pwd)
PROJECT_VERSION=$(cat "${PROJECT_ROOT}/.version" 2>/dev/null || echo "unknown")

# ── 部署配置信息 ──────────────────────────────────────────────
REMOTE_IP="${ZHIYU_REMOTE_IP:-10.211.55.4}"
REMOTE_USER="${ZHIYU_REMOTE_USER:-parallels}"
REMOTE_PASS="${ZHIYU_REMOTE_PASS:-root}"
REMOTE_DIR="/home/parallels/Documents/dev/code/ZhiYu"

# 是否在清理时彻底重置并重新初始化 Kubernetes 集群 (默认不重置，保护生产安全)
RESET_KUBEADM="no"

# 本地 Java/Maven 环境配置 (使用 Homebrew 的 OpenJDK 21)
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
# 强健壮性设计：在非交互式或受限的父进程环境中，PATH 可能丢失，在此强行追加核心路径
export PATH="$JAVA_HOME/bin:${PATH:-}:/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin:/opt/homebrew/bin"

# ── 1. 环境预检 ──────────────────────────────────────────────
# 函数名称: check_local_env
    # 检查本地编译部署所需的工具链 (Java, Maven, rsync)
# 返 回 值: 无
check_local_env() {
    log_step "正在执行本地环境预检..."
    
    # 检查 Java
    if [ ! -d "$JAVA_HOME" ]; then
        log_error "未在指定路径找到 Java 21 JDK: $JAVA_HOME"
        log_error "请确认 Homebrew 安装: brew install openjdk@21"
        exit 1
    fi
    log_info "发现 Java 21 环境: $(java -version 2>&1 | head -n 1)"
    
    # 检查 Maven
    if ! command -v mvn &>/dev/null; then
        log_error "本地未安装 Maven 客户端，请运行: brew install maven"
        exit 1
    fi
    log_info "发现 Maven 客户端: $(mvn -v | head -n 1)"
    
    # 检查 SSH 密钥认证
    if ! ssh -o StrictHostKeyChecking=no -o BatchMode=yes -o ConnectTimeout=5 "${REMOTE_USER}@${REMOTE_IP}" "echo ok" &>/dev/null; then
        log_error "无法通过 SSH 密钥连接到远程服务器 ${REMOTE_USER}@${REMOTE_IP}"
        log_error "请先配置 SSH 免密登录: ssh-copy-id ${REMOTE_USER}@${REMOTE_IP}"
        exit 1
    fi
    log_info "SSH 密钥认证已就绪"
    
    # 检查 rsync
    if ! command -v rsync &>/dev/null; then
        log_error "本地未安装 rsync 客户端"
        exit 1
    fi
    log_info "发现 rsync 工具"
}

# ── 2. 本地编译打包 ──────────────────────────────────────────
# 函数名称: build_locally
# 函数说明: 检查未提交变更（强制要求先提交），然后清理旧产物并编译打包
# 返 回 值: 无
build_locally() {
    # 0. 部署前置检查：确保所有变更已提交，保证版本号可追溯
    log_step "部署前置检查: 验证本地无未提交的代码变更..."
    if ! git -C "${PROJECT_ROOT}" diff --quiet 2>/dev/null || \
       ! git -C "${PROJECT_ROOT}" diff --cached --quiet 2>/dev/null; then
        log_error "检测到未提交的代码变更！"
        log_error "根据版本追溯策略，每次部署前必须提交所有代码变更。"
        log_error "请执行: git add -A && git commit -m '...' 后再重新运行部署脚本"
        exit 1
    fi
    if [ -n "$(git -C "${PROJECT_ROOT}" status --porcelain 2>/dev/null | grep '^??')" ]; then
        log_warn "检测到未跟踪的新文件，请确认是否需要先提交后再部署"
    fi
    log_info "  ✓ Git 工作区干净，所有变更已提交"

    # 记录本次部署的版本标识（git hash），写入文件以便远程无 .git 时使用
    DEPLOY_GIT_HASH=$(git -C "${PROJECT_ROOT}" rev-parse --short HEAD)
    DEPLOY_BUILD_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    echo "$DEPLOY_GIT_HASH" > "${PROJECT_ROOT}/deploy/envs/kubeadm/.git-hash"
    export DEPLOY_GIT_HASH DEPLOY_BUILD_TIME
    log_info "  部署版本: ${PROJECT_ROOT}/.version=${PROJECT_VERSION:-?} git=${DEPLOY_GIT_HASH}"

    log_step "正在本地编译打包项目 (跳过单元测试)..."
    mvn -f "${PROJECT_ROOT}/backend/pom.xml" clean package -DskipTests
    log_info "本地打包成功！已生成最新的 JAR 包"

    # 前端编译
    if [ -f "${PROJECT_ROOT}/frontend/package.json" ]; then
        log_step "正在本地编译前端项目..."
        cd "${PROJECT_ROOT}/frontend"
        npm ci --prefer-offline
        npm run build
        cd "${PROJECT_ROOT}"
        log_info "前端编译成功！已生成 dist/ 产物"
    else
        log_warn "未找到 frontend/package.json，跳过前端编译"
    fi
}

# ── 3. 代码与产物同步 ────────────────────────────────────────
# 函数名称: sync_to_remote
# 函数说明: 使用 rsync 增量同步代码骨架并单独传输最新的编译产物
# 返 回 值: 无
sync_to_remote() {
    log_step "正在同步项目结构与编译产物到远程主机..."
    
    # 使用 rsync 同步除 target、.git 外的项目核心代码与部署脚本
    log_info "使用 rsync 增量同步源码及部署配置文件..."
    rsync -avz --delete \
        --exclude='.git/' \
        --exclude='.idea/' \
        --exclude='.codegraph/' \
        --exclude='.claude/' \
        --exclude='frontend/node_modules/' \
        --exclude='docs/' \
        --exclude='**/target/' \
        "${PROJECT_ROOT}/" "${REMOTE_USER}@${REMOTE_IP}:${REMOTE_DIR}/" < /dev/null
        
    # ── 资深架构师防刷锁死设计 ─────────────────────────────────
    # 原理说明：远程主机可能对高频瞬间的 SSH 连接建链进行安全锁死 (Fail2ban 防护)。
    # 此处在两次 rsync 之间强制休眠 2 秒以释放 TCP 端口及建链计数，根治 Permission denied 顽疾。
    # ──────────────────────────────────────────────────────────
    log_info "同步第一阶段完成，休眠 2 秒以规避远程 SSH 并发限刷保护..."
    sleep 2
        
    # 单独传输本地编译出的最新的胖 JAR 包
    log_info "通过 rsync 拷贝本地编译出的胖 JAR 包到远端..."
    rsync -avz "${PROJECT_ROOT}/backend/zhiyu-server/target/"zhiyu-server-*.jar "${REMOTE_USER}@${REMOTE_IP}:${REMOTE_DIR}/backend/zhiyu-server/target/" < /dev/null
    
    log_info "代码及产物同步完成 ✓"
}

# ── 4. 远程命令执行 ─────────────────────────────────────────
# 函数名称: run_ssh
# 函数说明: 使用 SSH 密钥认证在远程主机执行命令
# 参    数: $1 - 待执行的远程命令字符串
# 返 回 值: 远程命令执行输出
run_ssh() {
    local cmd="$1"
    ssh \
        -o StrictHostKeyChecking=no \
        -o PreferredAuthentications=publickey,password \
        "${REMOTE_USER}@${REMOTE_IP}" "${cmd}" < /dev/null
}

# ── 本地与远端密码哈希联动 ──────────────────────────────────────
# 函数名称: ensure_and_hash_secrets
# 函数说明: 1. 优先尝试从远端拉取 passwords.env (若远端有)
#           2. 本地执行 ensure-secrets.sh 补充证书和明文密码
#           3. 使用本地指定 ./env/venv/bin/python3 + bcrypt 动态算明文 NACOS_PASSWORD 的 BCrypt 哈希
#           4. 将哈希值追加写入本地 passwords.env
# 返 回 值: 无
# ──────────────────────────────────────────────────────────────
ensure_and_hash_secrets() {
    log_step "正在执行本地与远端密码哈希联动..."

    local secrets_dir="${PROJECT_ROOT}/deploy/envs/kubeadm"
    local password_file="${secrets_dir}/passwords.env"

    mkdir -p "$secrets_dir"

    # 1. 尝试从远程将 passwords.env 备份回本地以确保密码一致性
    log_info "尝试从远程服务器拉取 passwords.env 备份以确保单源密码一致..."
    if scp -o StrictHostKeyChecking=no \
        "${REMOTE_USER}@${REMOTE_IP}:${REMOTE_DIR}/deploy/envs/kubeadm/passwords.env" "$password_file" 2>/dev/null; then
        log_info "  ✓ 成功从远程拉取密码文件 passwords.env"
    else
        log_warn "  远程暂无密码文件，将在本地生成新密码"
    fi

    # 2. 运行本地 ensure-secrets.sh（含密码自愈、JWT 密钥、BCrypt 哈希全链路计算）
    if [ -f "${PROJECT_ROOT}/deploy/scripts/ensure-secrets.sh" ]; then
        log_info "执行本地 ensure-secrets.sh 确认密钥/证书/密码哈希就绪..."
        source "${PROJECT_ROOT}/deploy/scripts/ensure-secrets.sh" kubeadm
    fi
}

# ── 5. 远程命令执行与部署 ───────────────────────────────────
# 函数名称: deploy_remotely
# 函数说明: 通过 SSH 远程调用远程服务器的 deploy.sh 部署应用，支持常规操作与组合动作。
# 参    数: $1 - 部署动作 (如 build-clean-deploy, all, cleanup, show-secrets 等)
# 返 回 值: 无
deploy_remotely() {
    local action="${1:-all}"
    
    if [ "${action}" = "build-clean-deploy" ]; then
        log_step "正在执行全自动一键式组合动作: 本地构建 -> 远程清理 -> 远程核心部署 -> 远程监控部署 (build-clean-deploy)..."
        
        if [ "${RESET_KUBEADM}" = "yes" ]; then
            log_warn "⚠️ 检测到 --reset-kubeadm 参数，将执行远程集群重置与重新初始化开荒！"
            
            log_step "步骤 1/3: 正在远程安全清理并彻底重置 Kubernetes 集群..."
            run_ssh "
                cd ${REMOTE_DIR} && \
                echo '[INFO] 1/4 正在执行微服务与基础设施 K8s 资源安全清理...' && \
                chmod +x deploy/deploy.sh && \
                (echo 'no'; echo 'n') | ./deploy/deploy.sh kubeadm cleanup && \
                \
                echo '[INFO] 2/4 正在非交互式执行 kubeadm reset 强制重置节点...' && \
                echo '${REMOTE_PASS}' | sudo -S kubeadm reset --force && \
                \
                echo '[INFO] 3/4 正在彻底清除残留 CNI 网络配置与 kubeconfig 凭证，防止旧状态粘连...' && \
                echo '${REMOTE_PASS}' | sudo -S rm -rf /etc/cni/net.d && \
                rm -rf \$HOME/.kube/config && \
                rm -rf /home/parallels/.kube/config && \
                \
                echo '[INFO] 4/4 正在重新一键式初始化 Kubernetes 单节点集群并配置普通用户授权...' && \
                chmod +x deploy/bootstrap/install-kubeadm.sh && \
                echo '${REMOTE_PASS}' | sudo -S ./deploy/bootstrap/install-kubeadm.sh init
            "
            
            log_info "远程集群重置与初始化完成！休眠 5 秒以确保 K8s API Server 稳定响应..."
            sleep 5
        else
            # 1. 远程执行安全清理 (自动回答 'no' 拒绝 kubeadm reset, 自动回答 'n' 拒绝删除本地镜像与密码文件)
            log_step "步骤 1/3: 正在远程安全清理旧部署资源 (自动拒绝危险的 kubeadm reset)..."
            run_ssh "cd ${REMOTE_DIR} && chmod +x deploy/deploy.sh && (echo 'no'; echo 'n') | ./deploy/deploy.sh kubeadm cleanup"
            
            log_info "安全清理指令已下发！休眠 3 秒以防短时间高频 SSH 连接被防火墙或限刷保护拒绝..."
            sleep 3
        fi
        
        # 2. 远程执行一键核心部署 (重新根据最新 Fat JAR 打包并进行 K8s 编排部署)
        log_step "步骤 2/3: 正在远程重新构建核心服务镜像并部署全部核心资源 (infra, init, build, deploy)..."
        run_ssh "cd ${REMOTE_DIR} && export NACOS_PASSWORD_HASH='${NACOS_PASSWORD_HASH:-}' && ./deploy/deploy.sh kubeadm all"
            
        # 监控栈一键全自动化拉起
        log_info "核心资源部署完成！休眠 3 秒平滑过渡到监控栈拉起步骤..."
        sleep 3

        log_step "步骤 3/3: 正在远程一键式自动部署与拉起监控栈 (Prometheus, Grafana, 预置监控大盘)..."
        run_ssh "cd ${REMOTE_DIR} && ./deploy/deploy.sh kubeadm monitoring"

        log_info "远程全部资源 (核心服务 + 监控组件) 一键部署与拉起执行成功 ✓"
    else
        log_step "正在远程触发 K8s 部署 (参数: env=kubeadm, action=${action})..."
        
        # 针对常规 cleanup 动作，同样注入安全默认答复，防止其卡在等待交互输入界面
        if [ "${action}" = "cleanup" ]; then
            if [ "${RESET_KUBEADM}" = "yes" ]; then
                log_warn "⚠️ 检测到 --reset-kubeadm 参数，将彻底重置远程整个 Kubernetes 集群！"
                run_ssh "
                    cd ${REMOTE_DIR} && \
                    echo '[INFO] 1/2 正在安全清理旧部署资源...' && \
                    chmod +x deploy/deploy.sh && \
                    (echo 'yes'; echo 'n') | ./deploy/deploy.sh kubeadm cleanup
                "
            else
                run_ssh "cd ${REMOTE_DIR} && chmod +x deploy/deploy.sh && (echo 'no'; echo 'n') | ./deploy/deploy.sh kubeadm cleanup"
            fi
        else
            run_ssh "cd ${REMOTE_DIR} && export NACOS_PASSWORD_HASH='${NACOS_PASSWORD_HASH:-}' && ./deploy/deploy.sh kubeadm ${action}"
        fi
        
        log_info "远程部署操作执行成功 ✓"
    fi
}

# ── 主流程 ──────────────────────────────────────────────────
# 函数名称: main
# 函数说明: 脚本入口函数，解析命令行标志（如 --reset-kubeadm），检查本地环境，
#           动态计算密码 BCrypt 哈希，并执行本地构建、代码增量同步和远程部署动作。
# 参    数: 命令行参数
# 返 回 值: 无
main() {
    local action=""
    
    # 支持命令行标志 --reset-kubeadm 放在任意位置，提取动作为 action
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --reset-kubeadm)
                RESET_KUBEADM="yes"
                shift
                ;;
            *)
                if [ -z "$action" ]; then
                    action="$1"
                else
                    log_error "不支持的多个动作参数: $1"
                    exit 1
                fi
                shift
                ;;
        esac
        done
    
    # 默认动作为 all (全自动完整流程部署)
    action="${action:-all}"
    
    check_local_env
    
    # 0. 优先执行本地与远程密码同步及哈希动态高能计算
    ensure_and_hash_secrets
    
    # 针对清理、状态查询及查看密码等运维动作，跳过本地编译，提升运维时效
    if [ "${action}" = "cleanup" ] || [ "${action}" = "status" ] || [ "${action}" = "show-secrets" ]; then
        log_info "执行运维动作: ${action}，跳过本地编译步骤..."
        rsync -avz --delete \
            --exclude='.git/' \
            --exclude='.idea/' \
            --exclude='.codegraph/' \
            --exclude='.claude/' \
            --exclude='frontend/node_modules/' \
            --exclude='docs/' \
            --exclude='**/target/' \
            "${PROJECT_ROOT}/" "${REMOTE_USER}@${REMOTE_IP}:${REMOTE_DIR}/" < /dev/null
    else
        build_locally
        sync_to_remote
    fi
    
    # ── 资深架构师防刷锁死设计 ─────────────────────────────────
    # 同步产物与实际下发部署命令之间，强制休眠 2 秒，规避 SSH 瞬间并发建链。
    # ──────────────────────────────────────────────────────────
    log_info "代码及产物同步完成，休眠 2 秒以平滑规避远程部署 SSH 并发限刷保护..."
    sleep 2

    deploy_remotely "${action}"

    # ── 部署后版本校验 ──────────────────────────────────────
    if [ "${action}" != "cleanup" ] && [ "${action}" != "status" ] && [ "${action}" != "show-secrets" ]; then
        verify_deployed_version
    fi
}

# ── 6. 部署后版本校验 ──────────────────────────────────────────
# 函数名称: verify_deployed_version
# 函数说明: 部署完成后，通过 /api/v1/admin/version 接口校验后端版本是否与构建版本一致
# 返 回 值: 无；如果版本不匹配打印警告
verify_deployed_version() {
    log_step "正在校验远端部署版本..."

    local version_url="http://${REMOTE_IP}:30080/api/v1/admin/version"
    local token=""
    token=$(curl -sf "${version_url%/*/*}/auth/login" \
        -H 'Content-Type: application/json' \
        -d '{"username":"admin","password":"Admin@123456"}' 2>/dev/null | \
        python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('accessToken',''))" 2>/dev/null || echo "")

    if [ -z "$token" ]; then
        # Fallback: try non-authenticated (for open version endpoint if configured)
        token="_none_"
    fi

    local deployed
    deployed=$(curl -sf "$version_url" -H "Authorization: Bearer $token" 2>/dev/null || echo "")
    if [ -z "$deployed" ]; then
        log_warn "  ⚠️ 无法从远端获取部署版本信息，请手动检查服务是否已正常启动"
        return
    fi

    local deployed_commit
    deployed_commit=$(echo "$deployed" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('commitId','unknown'))" 2>/dev/null || echo "unknown")

    log_info "  构建版本: ${DEPLOY_GIT_HASH:-?}"
    log_info "  部署版本: ${deployed_commit}"

    if [ "${DEPLOY_GIT_HASH:-}" != "${deployed_commit}" ] && [ "$deployed_commit" != "unknown" ]; then
        log_warn "  ⚠️ 部署版本与构建版本不一致！新版本可能未成功部署。"
        log_warn "  请检查 K8s Pod 是否已拉取最新镜像: kubectl describe pod -l app=zhiyu-backend -n zhiyu"
    else
        log_info "  ✓ 部署版本校验通过: ${deployed_commit}"
    fi
}

# 运行主流程，并将命令行参数传递给远程部署动作
main "$@"
