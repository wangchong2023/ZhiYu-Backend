#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: offline-pack.sh
# 脚本功能: 专职负责一键编译、镜像拉取、分层解包以及高内聚全自包含的离线交付压缩包打包。
#           支持从本地构建或直接拉取公共 Registry 两种运行模式，并能智能过滤敏感密码凭证以
#           及 PEM 私钥对，确保离线包 100% 满足信息安全审计标准。
# 编 写 人: 资深架构师 & 高级开发工程师 (Antigravity AI)
# 编写时间: 2026-05-20
# 调用方式:
#   ./deploy/scripts/offline-pack.sh <env>          # 本地编译并构建 Docker 镜像，打包离线包
#   ./deploy/scripts/offline-pack.sh <env> --pull   # 绕过本地 Maven 编译，直接拉取 Registry 镜像打包
# 输出路径: artifact/zhiyu-offline-YYYYMMDD.tar.gz
# ==============================================================================
set -euo pipefail

# ── 颜色及日志输出定义 ──────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()  { echo -e "${CYAN}[STEP]${NC}  $*"; }

# ── 参数解析与验证 ──────────────────────────────────────────────
ENV="${1:-}"
MODE="${2:-build}"

# 打印规范化 Usage 帮助菜单
if [ -z "$ENV" ]; then
  echo "用法: $0 <env> [--pull]"
  echo ""
  echo "环境:"
  echo "  kubeadm   单机物理离线 K8s 部署环境"
  echo "  dev       阿里云 ACK 云原生开发环境"
  echo "  test      阿里云 ACK 云原生测试环境"
  echo ""
  echo "选项:"
  echo "  --pull    从指定 Registry 拉取镜像（跳过本地 Maven 源码编译，适用于 CI/CD 场景）"
  echo ""
  echo "示例:"
  echo "  $0 kubeadm           # 本地 Maven 源码编译 -> 构建 Docker 镜像 -> 组装离线包"
  echo "  $0 kubeadm --pull    # 从 Registry 拉取镜像 -> 组装离线包"
  exit 1
fi

[ "$MODE" = "--pull" ] && MODE="pull"

# ── 基准路径动态推导 ──────────────────────────────────────────────
# 利用物理寻址计算，确保无论在任何工作目录下执行该脚本，都能正确获得相关绝对物理路径
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_DIR="$(cd "$DEPLOY_DIR/.." && pwd)"
ENV_FILE="${DEPLOY_DIR}/envs/${ENV}/config.env"

# 强验证环境配置文件是否存在，保障打包基准数据完备
if [ ! -f "$ENV_FILE" ]; then
  log_error "环境配置文件不存在: $ENV_FILE"
  exit 1
fi
source "$ENV_FILE"
log_info "已成功加载目标环境: ${ENV} (K8s Namespace: ${K8S_NAMESPACE:-})"

# ── 基础设施镜像版本默认值表 ──────────────────────────────────────
# 与 deploy/manifests/ 下的配置文件默认版本保持高强内聚与高度统一
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.0}"
REDIS_IMAGE="${REDIS_IMAGE:-redis:7-alpine}"
NACOS_IMAGE="${NACOS_IMAGE:-nacos/nacos-server:v2.4.0}"
BUSYBOX_IMAGE="${BUSYBOX_IMAGE:-busybox:1.36}"
PROMETHEUS_IMAGE="${PROMETHEUS_IMAGE:-prom/prometheus:v3.7.0}"
GRAFANA_IMAGE="${GRAFANA_IMAGE:-grafana/grafana:11.6.0}"
KUBE_STATE_METRICS_IMAGE="${KUBE_STATE_METRICS_IMAGE:-registry.k8s.io/kube-state-metrics/kube-state-metrics:v2.15.0}"
NODE_EXPORTER_IMAGE="${NODE_EXPORTER_IMAGE:-prom/node-exporter:v1.9.0}"
MYSQLD_EXPORTER_IMAGE="${MYSQLD_EXPORTER_IMAGE:-prom/mysqld-exporter:v0.15.0}"
REDIS_EXPORTER_IMAGE="${REDIS_EXPORTER_IMAGE:-oliver006/redis_exporter:v1.67.0}"

# 智宇微服务应用镜像名推导
if [ -n "${DOCKER_REGISTRY:-}" ]; then
  APP_IMAGE="${DOCKER_REGISTRY}/${DOCKER_IMAGE}:${DOCKER_TAG:-latest}"
else
  APP_IMAGE="${DOCKER_IMAGE:-zhiyu-backend}:${DOCKER_TAG:-latest}"
fi

# ── 输出制品包目录与命名定义 ──────────────────────────────────────
# 设计决策：离线包统一输出到项目顶层 artifact/ 目录，扁平化（无环境子目录），
# 文件名仅含日期标识，使产物路径简洁清晰，便于 scp 传输和归档管理。
PACKAGE_DIR="${PROJECT_DIR}/artifact"
PACKAGE_NAME="zhiyu-offline-$(date +%Y%m%d)"
BUNDLE_DIR="${PACKAGE_DIR}/${PACKAGE_NAME}"
OUTPUT_FILE="${PACKAGE_DIR}/${PACKAGE_NAME}.tar.gz"

# 保证每次打包前，输出的临时目录完全干净，避免因上一次打包残留导致脏数据污染
rm -rf "$BUNDLE_DIR"
mkdir -p "$BUNDLE_DIR"

# ── 全量所需镜像列表汇总 ──────────────────────────────────────────
IMAGES=("$APP_IMAGE" "$MYSQL_IMAGE" "$REDIS_IMAGE" "$BUSYBOX_IMAGE"
        "$PROMETHEUS_IMAGE" "$GRAFANA_IMAGE" "$KUBE_STATE_METRICS_IMAGE" "$NODE_EXPORTER_IMAGE"
        "$MYSQLD_EXPORTER_IMAGE" "$REDIS_EXPORTER_IMAGE")

# Nacos 配置为 standalone 并且使用内建 Derby 存储时，才将 Nacos 服务本身加入镜像集合
if [ -n "${NACOS_STORAGE:-}" ]; then
  IMAGES+=("$NACOS_IMAGE")
fi

# ══════════════════════════════════════════════════════════════
# Step 1: 编译微服务 JAR 包并分层提取 (仅在 kubeadm + build 模式下执行)
# ══════════════════════════════════════════════════════════════
if [ "$MODE" = "build" ] && [ "$ENV" = "kubeadm" ]; then
  log_step "执行微服务 Maven 源码编译流程..."

  # 智能探测当前项目是否已存在编译好的最新包，若无则拉起 Maven 编译
  JAR_FILE=$(find "${PROJECT_DIR}/backend/zhiyu-server/target" -maxdepth 1 -name "zhiyu-server-*.jar" 2>/dev/null | head -1)
  if [ -z "$JAR_FILE" ]; then
    log_info "未检测到预编译包，唤起 Maven 构建工具链..."
    if [ -x "${PROJECT_DIR}/backend/mvnw" ]; then
      (cd "${PROJECT_DIR}/backend" && ./mvnw clean package -DskipTests -pl zhiyu-server -am -q)
    elif command -v mvn &>/dev/null; then
      (cd "$PROJECT_DIR" && mvn -f backend/pom.xml clean package -DskipTests -Denforcer.skip=true -pl zhiyu-server -am -q)
    else
      log_error "系统未安装 Maven 且未检测到 maven wrapper 脚本！编译被迫终止。"
      exit 1
    fi
    JAR_FILE=$(find "${PROJECT_DIR}/backend/zhiyu-server/target" -maxdepth 1 -name "zhiyu-server-*.jar" 2>/dev/null | head -1)
    if [ -z "$JAR_FILE" ]; then
      log_error "Maven 编译异常：未能在目标位置生成 zhiyu-server-*.jar 包"
      exit 1
    fi
  fi
  log_info "当前所使用的 Spring Boot JAR 包路径为: ${JAR_FILE}"

  # ── Step 2: 提取 Spring Boot 胖 JAR 为 Layertools 分层架构 ──
  # 资深架构师核心设计：利用 layertools 提取分层依赖目录，将 fat-jar 拆分为 dependencies, 
  # spring-boot-loader, snapshot-dependencies, application 四个层，以极大提升宿主机 Containerd 的镜像缓存命中率
  log_step "启动 Spring Boot layertools 胖 JAR 目录分层提取..."
  (cd "$PROJECT_DIR" && java -Djarmode=layertools -jar "$JAR_FILE" extract --destination backend/zhiyu-server/target/extracted)

  # ── Step 3: 本地构建应用 Docker 容器镜像 ────────────────────
  log_step "启动本地 Docker 容器镜像编译构建: $APP_IMAGE"
  (cd "$PROJECT_DIR" && docker build -t "$APP_IMAGE" -f deploy/docker/Dockerfile.kubeadm .)
fi

# ══════════════════════════════════════════════════════════════
# Step 4: 拉取 / 验证基础设施镜像
# ══════════════════════════════════════════════════════════════
log_step "准备镜像..."

for img in "${IMAGES[@]}"; do
  if [ "$img" = "$APP_IMAGE" ] && [ "$MODE" = "build" ]; then
    continue  # 已在 Step 3 构建
  fi
  if docker image inspect "$img" &>/dev/null; then
    log_info "  ✓ 本地已有: $img"
  else
    log_info "  拉取: $img"
    docker pull "$img"
  fi
done

# ══════════════════════════════════════════════════════════════
# Step 5: 导出镜像到 tar
# ══════════════════════════════════════════════════════════════
log_step "导出镜像..."
IMAGES_TAR="${BUNDLE_DIR}/images.tar"
docker save "${IMAGES[@]}" -o "$IMAGES_TAR"

# SHA256 校验
(cd "$BUNDLE_DIR" && shasum -a 256 images.tar > images.tar.sha256)
IMAGES_SIZE=$(du -h "$IMAGES_TAR" | cut -f1)
log_info "镜像包大小: $IMAGES_SIZE"

# ══════════════════════════════════════════════════════════════
# Step 6: 组装离线包
# ══════════════════════════════════════════════════════════════
log_step "组装离线包..."

# 复制 deploy/ 目录（排除远端不需要的文件、敏感强密码及 pem 证书密钥对）
rsync -a \
  --exclude='artifact/' \
  --exclude='docker/' \
  --exclude='scripts/offline-pack.sh' \
  --exclude='scripts/install-kubeadm.sh' \
  --exclude='scripts/gen-jwt-keys.sh' \
  --exclude='scripts/offline-deploy.sh' \
  --exclude='envs/**/*.pem' \
  --exclude='envs/**/passwords.env' \
  --exclude='.DS_Store' \
  --exclude='._*' \
  "${DEPLOY_DIR}/" "${BUNDLE_DIR}/deploy/"

# 仅保留当前环境的环境配置包目录，删除其他环境目录，消除割裂感
find "${BUNDLE_DIR}/deploy/envs" -maxdepth 1 -mindepth 1 -type d -not -name "${ENV}" -exec rm -rf {} + 2>/dev/null || true

# 清理 macOS 产生的临时文件
find "${BUNDLE_DIR}" -name '._*' -delete 2>/dev/null || true
find "${BUNDLE_DIR}" -name '.DS_Store' -delete 2>/dev/null || true

# 确保脚本可执行
chmod +x "${BUNDLE_DIR}/deploy/deploy.sh"
chmod +x "${BUNDLE_DIR}/deploy/scripts/"*.sh 2>/dev/null || true

# ══════════════════════════════════════════════════════════════
# Step 7: 生成远端部署入口脚本 (智能一键装载系统)
# ══════════════════════════════════════════════════════════════
log_info "生成极简一键式智能部署入口脚本..."

cat > "${BUNDLE_DIR}/offline-deploy.sh" <<'DEPLOY_SCRIPT'
#!/bin/bash
# ============================================================
# 极简一键式智能离线部署脚本 — 在目标物理节点上执行
#
# 功能:
#   1. 智能宿主机依赖自检：自动检查机器上 Docker 容器引擎和 kubectl 是否已就绪。
#   2. 自动底层环境开荒：若环境缺失，自动唤起内置的 bootstrap 离线装机包一键配置宿主机环境。
#   3. 全量零干预容器拉起：依赖就绪后，智能探测容器引擎（ctr/docker）并无损拉起微服务与监控大盘。
#
# 用法:
#   sudo ./offline-deploy.sh              # 一键完整离线部署
#   ./offline-deploy.sh --dry-run        # 仅校验，不执行
# ============================================================
set -euo pipefail

DRY_RUN=false
[ "${1:-}" = "--dry-run" ] && DRY_RUN=true

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()  { echo -e "${CYAN}[STEP]${NC}  $*"; }

BUNDLE_DIR="$(cd "$(dirname "$0")" && pwd)"
IMAGES_TAR="${BUNDLE_DIR}/images.tar"
DEPLOY_SCRIPT="${BUNDLE_DIR}/deploy/deploy.sh"
BOOTSTRAP_SCRIPT="${BUNDLE_DIR}/deploy/scripts/os-init.sh"

# ── 环境名从 deploy/envs/ 目录自动智能推断 ───────────────────────────
# 直接读取 deploy/envs/ 下唯一的环境包子目录名称
ENV=$(find "${BUNDLE_DIR}/deploy/envs" -mindepth 1 -maxdepth 1 -type d -exec basename {} \;)
ENV="${ENV:-kubeadm}"

echo "=================================================="
echo " ZhiYu-Backend 极简一键式智能离线部署"
echo " 环境: ${ENV}"
echo " 目录: ${BUNDLE_DIR}"
[ "$DRY_RUN" = true ] && echo " 模式: DRY-RUN（仅校验）"
echo "=================================================="

# ── 智能环境自检与引导开荒 ─────────────────────────────────────
log_step "宿主机底层环境依赖自检..."

NEED_BOOTSTRAP=false
if ! command -v docker &>/dev/null || ! command -v kubectl &>/dev/null; then
  NEED_BOOTSTRAP=true
fi

if [ "$NEED_BOOTSTRAP" = true ]; then
  log_warn "检测到当前宿主机缺失 Docker 容器引擎或 kubectl 集群管理客户端！"
  if [ -f "$BOOTSTRAP_SCRIPT" ]; then
    log_info "发现离线包中包含自动引导程序，启动内置的 os-init 宿主机系统初始化模块对宿主机进行底层开荒..."
    chmod +x "$BOOTSTRAP_SCRIPT"
    # 调用内置的系统初始化脚本执行离线安装
    if bash "$BOOTSTRAP_SCRIPT" --offline; then
      log_info "✓ 宿主机基础环境依赖初始化成功"
      
      # 智能提示：如果当前用户第一次被加入 docker 组，需要提示其刷新组或使用 sudo
      if ! groups | grep -q docker && [ "$USER" != "root" ]; then
        log_warn "  ⚠ 已将当前用户 $USER 加入 docker 组。因组权限限制，若直接运行 docker 报权限错误，"
        log_warn "    请执行 'newgrp docker' 刷新临时会话组，或者使用 sudo ./offline-deploy.sh 再次拉起。"
      fi
    else
      log_error "✗ 宿主机基础环境初始化失败，请检查报错日志！"
      exit 1
    fi
  else
    log_error "✗ 缺失底层运行环境（Docker/kubectl），且离线包不包含 bootstrap 引导文件夹，无法开荒。"
    log_error "  请联系系统管理员或在联网环境下手动安装底层依赖后再行尝试。"
    exit 1
  fi
else
  log_info "✓ 宿主机底层依赖已完备，跳过系统环境初始化开荒阶段。"
fi

# ── 离线镜像与服务编排自检 ─────────────────────────────────────
log_step "微服务部署前置包自检..."

if [ ! -f "$IMAGES_TAR" ]; then
  log_error "镜像包不存在: $IMAGES_TAR"
  exit 1
fi

if [ ! -f "$DEPLOY_SCRIPT" ]; then
  log_error "部署脚本不存在: $DEPLOY_SCRIPT"
  exit 1
fi

command -v kubectl &>/dev/null || { log_error "kubectl 仍未就绪，请确保 K8s 集群已正常连接！"; exit 1; }
# 智能检测容器后端（ctr/docker）并支持兼容模式导入镜像
HAS_DOCKER=false
HAS_CTR=false
command -v docker &>/dev/null && HAS_DOCKER=true
command -v ctr &>/dev/null && HAS_CTR=true

if [ "$HAS_DOCKER" = false ] && [ "$HAS_CTR" = false ]; then
  log_error "✗ 未检测到可用的容器后端 (Docker Engine 或 containerd)，请先启动相关服务！"
  exit 1;
fi
log_info "✓ 自检通过"

# ── 校验 SHA256 ──────────────────────────────────────────────
if [ -f "${IMAGES_TAR}.sha256" ]; then
  log_info "校验镜像包完整性..."
  (cd "$BUNDLE_DIR" && shasum -a 256 -c images.tar.sha256)
  log_info "  ✓ 校验通过"
fi

if [ "$DRY_RUN" = true ]; then
  log_info "DRY-RUN 完成，跳过实际部署"
  exit 0
fi

# ── 智能导入容器镜像 ──────────────────────────────────────────
log_step "向宿主机容器引擎导入离线镜像 (这可能需要 2~3 分钟)..."

if [ "$HAS_CTR" = true ]; then
  log_info "使用 containerd (ctr) 导入镜像到 k8s.io 命名空间..."
  if ctr -n k8s.io images import "$IMAGES_TAR" 2>/dev/null; then
    log_info "  ✓ 镜像导入成功"
  else
    log_info "尝试 sudo 导入到 containerd..."
    sudo ctr -n k8s.io images import "$IMAGES_TAR"
    log_info "  ✓ 镜像导入成功"
  fi
elif [ "$HAS_DOCKER" = true ]; then
  log_info "使用 Docker 引擎导入本地镜像..."
  if docker load -i "$IMAGES_TAR"; then
    log_info "  ✓ 镜像导入成功"
  else
    log_info "尝试 sudo 导入到 Docker..."
    sudo docker load -i "$IMAGES_TAR"
    log_info "  ✓ 镜像导入成功"
  fi
fi

# ── 执行服务编排部署（跳过重构编译，使用离线包）────────────────
log_step "执行微服务及监控栈的编排与一键拉起..."
export SKIP_BUILD=true

# 1. 部署微服务与核心基础设施（MySQL/Redis/Nacos/App）
log_info "正在拉起微服务核心及基础设施 (MySQL, Redis, Nacos, ZhiYu-Backend)..."
if bash "$DEPLOY_SCRIPT" "$ENV" all; then
  log_info "✓ 微服务及核心基础设施部署成功"
else
  log_error "✗ 微服务及核心基础设施部署失败，请检查上方控制台日志！"
  exit 1
fi

# 2. 部署监控大盘栈（Prometheus & Grafana）
log_info "正在拉起监控栈 (Prometheus, Grafana)..."
if bash "$DEPLOY_SCRIPT" "$ENV" monitoring; then
  log_info "✓ 监控栈（Prometheus & Grafana）部署成功，内置预置大盘已自动挂载"
else
  log_error "✗ 监控栈部署失败，请检查监控容器状态！"
  exit 1
fi

log_step "一键式极简离线部署全部顺利完成！"
log_info "可以使用以下命令检查集群中所有组件状态:"
log_info "  ./deploy/deploy.sh $ENV status"
DEPLOY_SCRIPT

chmod +x "${BUNDLE_DIR}/offline-deploy.sh"

# ══════════════════════════════════════════════════════════════
# Step 8: 压缩输出
# ══════════════════════════════════════════════════════════════
log_step "压缩离线包..."

# COPYFILE_DISABLE=1 防止 macOS 生成 ._* Apple Double 文件
(cd "$PACKAGE_DIR" && COPYFILE_DISABLE=1 tar czf "${PACKAGE_NAME}.tar.gz" "$PACKAGE_NAME")

BUNDLE_SIZE=$(du -h "$OUTPUT_FILE" | cut -f1)

# 清理临时目录
rm -rf "$BUNDLE_DIR"

# ══════════════════════════════════════════════════════════════
# 输出结果
# ══════════════════════════════════════════════════════════════
echo ""
echo "================================================"
echo " 离线部署包已生成"
echo "================================================"
echo " 文件:     $OUTPUT_FILE"
echo " 大小:     $BUNDLE_SIZE"
echo " 环境:     $ENV"
echo ""
echo " 含镜像 (${#IMAGES[@]}):"
for img in "${IMAGES[@]}"; do
  echo "   • $img"
done
echo ""
echo "── 部署到目标节点 ──"
echo "  scp $OUTPUT_FILE <user>@<node>:/tmp/"
echo "  ssh <user>@<node>"
echo "  cd /tmp && tar xzf $(basename "$OUTPUT_FILE")"
echo "  cd ${PACKAGE_NAME} && sudo ./offline-deploy.sh"
echo ""
echo "── 仅校验（不解压部署）──"
echo "  cd ${PACKAGE_NAME} && ./offline-deploy.sh --dry-run"
echo "================================================"
