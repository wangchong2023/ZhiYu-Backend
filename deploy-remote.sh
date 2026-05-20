#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智鱼后端)
# 脚本名称: deploy-remote.sh
# 脚本功能: 本地进行代码编译，并通过 sshpass & rsync 同步至远程服务器进行 K8s 部署。
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

# ── 部署配置信息 ──────────────────────────────────────────────
REMOTE_IP="10.211.55.4"
REMOTE_USER="parallels"
REMOTE_PASS="root"
REMOTE_DIR="/home/parallels/Documents/dev/code/ZhiYu"

# 本地 Java/Maven 环境配置 (使用 Homebrew 的 OpenJDK 21)
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
# 强健壮性设计：在某些非交互式或受限的父级进程环境中，原有 PATH 可能丢失或为空，导致 dirname 等系统基础命令丢失。
# 资深架构师在此显式保留原有 PATH（若有），并强行追加系统标准核心路径（/usr/bin 等），彻底根治 'dirname: command not found' 引起的 Maven Classworlds 加载死锁。
export PATH="$JAVA_HOME/bin:${PATH:-}:/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin:/opt/homebrew/bin"

# ── 1. 环境预检 ──────────────────────────────────────────────
# 函数名称: check_local_env
# 函数说明: 检查本地编译部署所需的工具链 (Java, Maven, sshpass, rsync)
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
    
    # 检查 sshpass
    if ! command -v sshpass &>/dev/null; then
        log_error "本地未安装 sshpass 客户端，请运行: brew install hudochenkov/sshpass/sshpass"
        exit 1
    fi
    log_info "发现 sshpass 工具"
    
    # 检查 rsync
    if ! command -v rsync &>/dev/null; then
        log_error "本地未安装 rsync 客户端"
        exit 1
    fi
    log_info "发现 rsync 工具"
}

# ── 2. 本地编译打包 ──────────────────────────────────────────
# 函数名称: build_locally
# 函数说明: 清理旧产物并编译打包生成最新的可部署 Fat JAR 包
build_locally() {
    log_step "正在本地编译打包项目 (跳过单元测试)..."
    mvn -f backend/pom.xml clean package -DskipTests
    log_info "本地打包成功！已生成最新的 JAR 包"
}

# ── 3. 代码与产物同步 ────────────────────────────────────────
# 函数名称: sync_to_remote
# 函数说明: 使用 rsync 增量同步代码骨架并使用 scp 单独传输最新的编译产物
sync_to_remote() {
    log_step "正在同步项目结构与编译产物到远程主机..."
    
    # 使用 rsync 同步除 target、.git 外的项目核心代码与部署脚本 (rsync 会自动创建不存在的目录)
    log_info "使用 rsync 增量同步源码及部署配置文件..."
    sshpass -p "$REMOTE_PASS" rsync -avz --delete \
        --exclude='.git/' \
        --exclude='.idea/' \
        --exclude='.codegraph/' \
        --exclude='.claude/' \
        --exclude='frontend/' \
        --exclude='docs/' \
        --exclude='**/target/' \
        ./ "${REMOTE_USER}@${REMOTE_IP}:${REMOTE_DIR}/" < /dev/null
        
    # 单独传输本地编译出的最新的胖 JAR 包 (rsync 自动创建目标子目录)
    log_info "通过 rsync 拷贝本地编译出的胖 JAR 包到远端..."
    sshpass -p "$REMOTE_PASS" rsync -avz backend/zhiyu-server/target/zhiyu-server-*.jar "${REMOTE_USER}@${REMOTE_IP}:${REMOTE_DIR}/backend/zhiyu-server/target/" < /dev/null
    
    log_info "代码及产物同步完成 ✓"
}

# ── 4. 远程命令执行与部署 ───────────────────────────────────
# 函数名称: run_ssh
# 函数说明: 使用 sshpass 配合安全选项 (强制密码认证、禁用公钥尝试)，在远程主机执行命令
# 参    数: $1 - 待执行的远程命令字符串
run_ssh() {
    local cmd="$1"
    sshpass -p "$REMOTE_PASS" ssh \
        -o StrictHostKeyChecking=no \
        -o PreferredAuthentications=password \
        -o PubkeyAuthentication=no \
        "${REMOTE_USER}@${REMOTE_IP}" "${cmd}" < /dev/null
}

# 函数名称: deploy_remotely
# 函数说明: 通过 SSH 远程调用远程服务器的 deploy.sh 部署应用，支持常规操作与组合动作
# 参    数: $1 - 部署动作 (如 build-clean-deploy, all, cleanup, show-secrets 等)
# ── 本地与远端密码哈希联动 ──────────────────────────────────────
# 函数名称: ensure_and_hash_secrets
# 函数说明: 1. 优先尝试从远端拉取 passwords.env (若远端有)
#           2. 本地执行 ensure-secrets.sh 补充证书和明文密码
#           3. 使用本地指定 ./env/venv/bin/python3 + bcrypt 动态算明文 NACOS_PASSWORD 的 BCrypt 哈希
#           4. 将哈希值追加写入本地 passwords.env
# ──────────────────────────────────────────────────────────────
ensure_and_hash_secrets() {
    log_step "正在执行本地与远端密码哈希联动..."
    
    local secrets_dir="deploy/secrets/kubeadm"
    local password_file="${secrets_dir}/passwords.env"
    
    mkdir -p "$secrets_dir"
    
    # 1. 尝试从远程将 passwords.env 备份回本地以确保密码一致性
    log_info "尝试从远程服务器拉取 passwords.env 备份以确保单源密码一致..."
    if sshpass -p "$REMOTE_PASS" scp -o StrictHostKeyChecking=no \
        -o PreferredAuthentications=password -o PubkeyAuthentication=no \
        "${REMOTE_USER}@${REMOTE_IP}:${REMOTE_DIR}/${password_file}" "$password_file" 2>/dev/null; then
        log_info "  ✓ 成功从远程拉取密码文件 passwords.env"
    else
        log_warn "  远程暂无密码文件，将在本地生成新密码"
    fi
    
    # 2. 运行本地 ensure-secrets.sh，确保明文密码和 JWT 对就绪
    if [ -f "deploy/scripts/ensure-secrets.sh" ]; then
        log_info "执行本地 ensure-secrets.sh 确认密钥/证书就绪..."
        source "deploy/scripts/ensure-secrets.sh" kubeadm
    fi
    
    # 3. 动态计算 NACOS_PASSWORD 对应的 BCrypt Hash (Spring Security 标准强哈希)
    if [ -n "${NACOS_PASSWORD:-}" ]; then
        log_info "使用本地 ./env/venv/bin/python3 加载 bcrypt 模块计算 Nacos 密码哈希..."
        
        local hash_val
        hash_val=$(./env/venv/bin/python3 -c "
import bcrypt
pwd = b'${NACOS_PASSWORD}'
# 生成标准 BCrypt 强哈希（使用 12 轮 Work Factor 以满足 Spring Security 高强度要求）
hashed = bcrypt.hashpw(pwd, bcrypt.gensalt(12)).decode('utf-8')
print(hashed)
" 2>/dev/null || echo "")
        
        if [ -n "$hash_val" ]; then
            log_info "  ✓ BCrypt 哈希计算成功！"
            export NACOS_PASSWORD_HASH="$hash_val"
            
            # 4. 追加/写入到本地的 passwords.env 文件中，防止下次丢失
            if ! grep -q "NACOS_PASSWORD_HASH" "$password_file"; then
                echo "export NACOS_PASSWORD_HASH='${NACOS_PASSWORD_HASH}'" >> "$password_file"
                log_info "  ✓ 已将 NACOS_PASSWORD_HASH 追加写入 passwords.env"
            else
                # 如果已经存在，则用 python 进行替换，确保最新的明文能被最新的哈希更新
                ./env/venv/bin/python3 -c "
import re
with open('${password_file}', 'r') as f:
    content = f.read()
# 正则替换已有的 NACOS_PASSWORD_HASH
new_content = re.sub(r'export NACOS_PASSWORD_HASH=.*', 'export NACOS_PASSWORD_HASH=\'${NACOS_PASSWORD_HASH}\'', content)
if 'export NACOS_PASSWORD_HASH' not in content:
    new_content += '\nexport NACOS_PASSWORD_HASH=\'${NACOS_PASSWORD_HASH}\'\n'
with open('${password_file}', 'w') as f:
    f.write(new_content)
"
                log_info "  ✓ 已在 passwords.env 中更新最新的 NACOS_PASSWORD_HASH"
            fi
        else
            log_error "本地 Python 计算哈希失败！请检查 ./env/venv/bin/python3 与 bcrypt 是否正常。"
            exit 1
        fi
    fi
}

# 函数名称: deploy_remotely
# 函数说明: 通过 SSH 远程调用远程服务器的 deploy.sh 部署应用，支持常规操作与组合动作
# 参    数: $1 - 部署动作 (如 build-clean-deploy, all, cleanup, show-secrets 等)
deploy_remotely() {
    local action="${1:-all}"
    
    if [ "${action}" = "build-clean-deploy" ]; then
        log_step "正在执行组合动作: 本地构建 -> 远程清理 -> 远程部署 (build-clean-deploy)..."
        
        # 1. 远程执行安全清理 (自动回答 'no' 拒绝 kubeadm reset, 自动回答 'n' 拒绝删除本地镜像与密码文件)
        log_step "步骤 1/2: 正在远程安全清理旧部署资源 (自动拒绝危险的 kubeadm reset)..."
        run_ssh "cd ${REMOTE_DIR} && chmod +x deploy/deploy.sh && (echo 'no'; echo 'n') | ./deploy/deploy.sh kubeadm cleanup"
            
        log_info "安全清理指令已下发！休眠 3 秒以防短时间高频 SSH 连接被防火墙或限刷保护拒绝..."
        sleep 3
        
        # 2. 远程执行一键部署 (重新根据最新 Fat JAR 打包并进行 K8s 编排部署)
        log_step "步骤 2/2: 正在远程重新构建镜像并部署全部资源 (infra, init, build, deploy)..."
        run_ssh "cd ${REMOTE_DIR} && export NACOS_PASSWORD_HASH='${NACOS_PASSWORD_HASH:-}' && ./deploy/deploy.sh kubeadm all"
            
        log_info "远程部署与服务拉起执行成功 ✓"
    else
        log_step "正在远程触发 K8s 部署 (参数: env=kubeadm, action=${action})..."
        
        # 针对常规 cleanup 动作，同样注入安全默认答复，防止其卡在等待交互输入界面
        if [ "${action}" = "cleanup" ]; then
            run_ssh "cd ${REMOTE_DIR} && chmod +x deploy/deploy.sh && (echo 'no'; echo 'n') | ./deploy/deploy.sh kubeadm cleanup"
        else
            run_ssh "cd ${REMOTE_DIR} && export NACOS_PASSWORD_HASH='${NACOS_PASSWORD_HASH:-}' && ./deploy/deploy.sh kubeadm ${action}"
        fi
        
        log_info "远程部署操作执行成功 ✓"
    fi
}


# ── 主流程 ──────────────────────────────────────────────────
main() {
    local action="${1:-all}"
    
    check_local_env
    
    # 0. 优先执行本地与远程密码同步及哈希动态高能计算
    ensure_and_hash_secrets
    
    # 针对清理、状态查询及查看密码等运维动作，跳过本地编译
    if [ "${action}" = "cleanup" ] || [ "${action}" = "status" ] || [ "${action}" = "show-secrets" ]; then
        log_info "执行运维动作: ${action}，跳过本地编译步骤..."
        # 仅同步最新的部署配置脚本，确保远程执行的是最新逻辑 (rsync 自动建目录)
        sshpass -p "$REMOTE_PASS" rsync -avz --delete \
            --exclude='.git/' \
            --exclude='.idea/' \
            --exclude='.codegraph/' \
            --exclude='.claude/' \
            --exclude='frontend/' \
            --exclude='docs/' \
            --exclude='**/target/' \
            ./ "${REMOTE_USER}@${REMOTE_IP}:${REMOTE_DIR}/" < /dev/null
    else
        build_locally
        sync_to_remote
    fi
    
    deploy_remotely "${action}"
}

# 运行主流程，并将命令行参数传递给远程部署动作
main "$@"
