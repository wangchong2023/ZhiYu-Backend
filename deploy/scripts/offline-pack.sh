#!/bin/bash
# ============================================================
# 离线部署包制作脚本
#
# 用法:
#   ./deploy/scripts/offline-pack.sh <env>          # 本地编译 + 打包
#   ./deploy/scripts/offline-pack.sh <env> --pull   # 从 registry 拉取（不本地编译）
#
# 输出: deploy/offline-package/<env>/zhiyu-offline-<env>-YYYYMMDD.tar.gz
#
# 离线包内容（自包含，不含源码）:
#   ├── offline-deploy.sh       # 远端入口
#   ├── images.tar              # Docker 镜像（app + infra）
#   ├── images.tar.sha256
#   └── deploy/                 # K8s manifests + 部署脚本
#
# 安全: 不含 passwords.env 和 JWT 密钥文件（远端首次部署时自动生成）
# ============================================================
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()  { echo -e "${CYAN}[STEP]${NC}  $*"; }

# ── 参数解析 ──────────────────────────────────────────────────
ENV="${1:-}"
MODE="${2:-build}"

if [ -z "$ENV" ]; then
  echo "用法: $0 <env> [--pull]"
  echo ""
  echo "环境:"
  echo "  kubeadm   离线 K8s 集群"
  echo "  dev        阿里云 ACK 开发环境"
  echo "  test       阿里云 ACK 测试环境"
  echo ""
  echo "选项:"
  echo "  --pull    从 registry 拉取镜像（不本地编译）"
  echo ""
  echo "示例:"
  echo "  $0 kubeadm           # 本地编译 + 构建镜像 + 打包"
  echo "  $0 kubeadm --pull    # 从 registry 拉取 + 打包"
  exit 1
fi

[ "$MODE" = "--pull" ] && MODE="pull"

# ── 路径 ──────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_DIR="$(cd "$DEPLOY_DIR/.." && pwd)"
ENV_FILE="${DEPLOY_DIR}/envs/${ENV}.env"

if [ ! -f "$ENV_FILE" ]; then
  log_error "环境文件不存在: $ENV_FILE"
  exit 1
fi
source "$ENV_FILE"
log_info "已加载环境: ${ENV} (namespace: ${K8S_NAMESPACE:-})"

# ── 镜像版本默认值 ────────────────────────────────────────────
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.0}"
REDIS_IMAGE="${REDIS_IMAGE:-redis:7-alpine}"
NACOS_IMAGE="${NACOS_IMAGE:-nacos/nacos-server:v2.4.0}"
BUSYBOX_IMAGE="${BUSYBOX_IMAGE:-busybox:1.36}"
PROMETHEUS_IMAGE="${PROMETHEUS_IMAGE:-prom/prometheus:v3.7.0}"
GRAFANA_IMAGE="${GRAFANA_IMAGE:-grafana/grafana:11.6.0}"
KUBE_STATE_METRICS_IMAGE="${KUBE_STATE_METRICS_IMAGE:-registry.k8s.io/kube-state-metrics/kube-state-metrics:v2.15.0}"
NODE_EXPORTER_IMAGE="${NODE_EXPORTER_IMAGE:-prom/node-exporter:v1.9.0}"

# 应用镜像名
if [ -n "${DOCKER_REGISTRY:-}" ]; then
  APP_IMAGE="${DOCKER_REGISTRY}/${DOCKER_IMAGE}:${DOCKER_TAG:-latest}"
else
  APP_IMAGE="${DOCKER_IMAGE:-zhiyu-backend}:${DOCKER_TAG:-latest}"
fi

# ── 输出目录 ──────────────────────────────────────────────────
PACKAGE_DIR="${DEPLOY_DIR}/offline-package/${ENV}"
PACKAGE_NAME="zhiyu-offline-${ENV}-$(date +%Y%m%d)"
BUNDLE_DIR="${PACKAGE_DIR}/${PACKAGE_NAME}"
OUTPUT_FILE="${PACKAGE_DIR}/${PACKAGE_NAME}.tar.gz"

rm -rf "$BUNDLE_DIR"
mkdir -p "$BUNDLE_DIR"

# ── 镜像列表 ──────────────────────────────────────────────────
IMAGES=("$APP_IMAGE" "$MYSQL_IMAGE" "$REDIS_IMAGE" "$BUSYBOX_IMAGE"
        "$PROMETHEUS_IMAGE" "$GRAFANA_IMAGE" "$KUBE_STATE_METRICS_IMAGE" "$NODE_EXPORTER_IMAGE")

# Nacos 仅在有存储配置时加入（kubeadm 默认不部署）
if [ -n "${NACOS_STORAGE:-}" ]; then
  IMAGES+=("$NACOS_IMAGE")
fi

# ══════════════════════════════════════════════════════════════
# Step 1: 编译 JAR（仅 kubeadm + build 模式）
# ══════════════════════════════════════════════════════════════
if [ "$MODE" = "build" ] && [ "$ENV" = "kubeadm" ]; then
  log_step "编译 JAR..."

  JAR_FILE=$(ls "${PROJECT_DIR}/backend/zhiyu-server/target/zhiyu-server-*.jar" 2>/dev/null | head -1)
  if [ -z "$JAR_FILE" ]; then
    log_info "未找到编译产物，执行 Maven 编译..."
    if [ -x "${PROJECT_DIR}/backend/mvnw" ]; then
      (cd "${PROJECT_DIR}/backend" && ./mvnw clean package -DskipTests -pl zhiyu-server -am -q)
    elif command -v mvn &>/dev/null; then
      (cd "$PROJECT_DIR" && mvn -f backend/pom.xml clean package -DskipTests -Denforcer.skip=true -pl zhiyu-server -am -q)
    else
      log_error "未找到 Maven，请安装 Maven 3.9+ 或设置 JAVA_HOME"
      exit 1
    fi
    JAR_FILE=$(ls "${PROJECT_DIR}/backend/zhiyu-server/target/zhiyu-server-*.jar" 2>/dev/null | head -1)
    if [ -z "$JAR_FILE" ]; then
      log_error "编译失败，未生成 JAR"
      exit 1
    fi
  fi
  log_info "使用 JAR: ${JAR_FILE}"

  # ── Step 2: 提取分层 JAR ────────────────────────────────────
  log_step "提取 Spring Boot 分层 JAR..."
  (cd "$PROJECT_DIR" && java -Djarmode=layertools -jar "$JAR_FILE" extract --destination target/extracted)

  # ── Step 3: 构建应用 Docker 镜像 ─────────────────────────────
  log_step "构建应用 Docker 镜像: $APP_IMAGE"
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

# 复制 deploy/ 目录（不含 secrets/ 和 offline-package/）
rsync -a --exclude='secrets/' --exclude='offline-package/' \
  "${DEPLOY_DIR}/" "${BUNDLE_DIR}/deploy/"

# 确保脚本可执行
chmod +x "${BUNDLE_DIR}/deploy/deploy.sh"
chmod +x "${BUNDLE_DIR}/deploy/scripts/"*.sh 2>/dev/null || true

# ══════════════════════════════════════════════════════════════
# Step 7: 生成远端部署入口脚本
# ══════════════════════════════════════════════════════════════
log_info "生成离线部署入口脚本..."

cat > "${BUNDLE_DIR}/offline-deploy.sh" <<'DEPLOY_SCRIPT'
#!/bin/bash
# ============================================================
# 离线部署脚本 — 在目标 K8s 节点上执行
#
# 用法:
#   ./offline-deploy.sh              # 完整部署
#   ./offline-deploy.sh --dry-run    # 仅校验，不执行
#
# 前提:
#   - kubectl 已配置并可访问集群
#   - ctr 可用（containerd）
#   - 当前目录下存在 images.tar 和 deploy/
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

# ── 环境名从 deploy/envs/ 推断 ────────────────────────────────
# 取第一个非 dev/test/staging/release 的 .env 文件，优先取 kubeadm
ENV=""
for f in "${BUNDLE_DIR}/deploy/envs/"*.env; do
  name=$(basename "$f" .env)
  if [ "$name" = "kubeadm" ]; then ENV="kubeadm"; break; fi
  if [ -z "$ENV" ] && [ "$name" != "dev" ] && [ "$name" != "test" ] && [ "$name" != "staging" ] && [ "$name" != "release" ]; then
    ENV="$name"
  fi
done
ENV="${ENV:-kubeadm}"

echo "================================================"
echo " ZhiYu 离线部署"
echo " 环境: ${ENV}"
echo " 目录: ${BUNDLE_DIR}"
[ "$DRY_RUN" = true ] && echo " 模式: DRY-RUN（仅校验）"
echo "================================================"

# ── 自检 ──────────────────────────────────────────────────────
log_step "自检..."

if [ ! -f "$IMAGES_TAR" ]; then
  log_error "镜像包不存在: $IMAGES_TAR"
  exit 1
fi

if [ ! -f "$DEPLOY_SCRIPT" ]; then
  log_error "部署脚本不存在: $DEPLOY_SCRIPT"
  exit 1
fi

command -v kubectl &>/dev/null || { log_error "kubectl 未安装"; exit 1; }
command -v ctr &>/dev/null || { log_error "ctr 未安装（需要 containerd）"; exit 1; }
log_info "自检通过"

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

# ── 加载镜像到 containerd ─────────────────────────────────────
log_step "加载镜像到 containerd..."
ctr -n k8s.io images import "$IMAGES_TAR"
log_info "镜像导入完成"

# ── 执行部署（跳过构建，镜像已预加载）────────────────────────
log_step "执行部署..."
export SKIP_BUILD=true
exec bash "$DEPLOY_SCRIPT" "$ENV" all
DEPLOY_SCRIPT

chmod +x "${BUNDLE_DIR}/offline-deploy.sh"

# ══════════════════════════════════════════════════════════════
# Step 8: 压缩输出
# ══════════════════════════════════════════════════════════════
log_step "压缩离线包..."

(cd "$PACKAGE_DIR" && tar czf "${PACKAGE_NAME}.tar.gz" "$PACKAGE_NAME")

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
