#!/bin/bash
# ============================================================
# 离线镜像打包脚本
# 用法:
#   ./deploy/scripts/offline-pack.sh <env>
#   ./deploy/scripts/offline-pack.sh kubeadm           # 打包 kubeadm 环境所需全部镜像
#   ./deploy/scripts/offline-pack.sh kubeadm --pull    # 从 registry 拉取（不本地构建）
# ============================================================
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()  { echo -e "${CYAN}[STEP]${NC}  $*"; }

# ── 参数解析 ──────────────────────────────────────────────────
ENV="${1:-}"
MODE="${2:-build}"  # build | pull

if [ -z "$ENV" ]; then
  echo "用法: $0 <env> [--pull]"
  echo "  env:  dev | test | staging | release | kubeadm"
  echo ""
  echo "示例:"
  echo "  $0 kubeadm           # 本地构建 + 打包"
  echo "  $0 kubeadm --pull    # 从 registry 拉取 + 打包"
  exit 1
fi

[ "$MODE" = "--pull" ] && MODE="pull"

# ── 路径 ──────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_DIR="$(cd "$DEPLOY_DIR/.." && pwd)"
ENV_FILE="${DEPLOY_DIR}/envs/${ENV}.env"

if [ -f "$ENV_FILE" ]; then
  source "$ENV_FILE"
  log_info "已加载环境: ${ENV} (namespace: ${K8S_NAMESPACE})"
else
  log_error "环境文件不存在: $ENV_FILE"
  exit 1
fi

# ── 镜像版本默认值 ────────────────────────────────────────────
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.0}"
REDIS_IMAGE="${REDIS_IMAGE:-redis:7-alpine}"
NACOS_IMAGE="${NACOS_IMAGE:-nacos/nacos-server:v2.4.0}"
BUSYBOX_IMAGE="${BUSYBOX_IMAGE:-busybox:1.36}"
PROMETHEUS_IMAGE="${PROMETHEUS_IMAGE:-prom/prometheus:v3.7.0}"
GRAFANA_IMAGE="${GRAFANA_IMAGE:-grafana/grafana:11.6.0}"
KUBE_STATE_METRICS_IMAGE="${KUBE_STATE_METRICS_IMAGE:-registry.k8s.io/kube-state-metrics/kube-state-metrics:v2.15.0}"
NODE_EXPORTER_IMAGE="${NODE_EXPORTER_IMAGE:-prom/node-exporter:v1.9.0}"

# 应用镜像
if [ -n "${DOCKER_REGISTRY:-}" ]; then
  APP_IMAGE="${DOCKER_REGISTRY}/${DOCKER_IMAGE}:${DOCKER_TAG}"
else
  APP_IMAGE="${DOCKER_IMAGE}:${DOCKER_TAG:-latest}"
fi

# 输出文件
OUTPUT_DIR="${PROJECT_DIR}/offline-images"
OUTPUT_FILE="${OUTPUT_DIR}/zhiyu-offline-${ENV}-$(date +%Y%m%d).tar"
mkdir -p "$OUTPUT_DIR"

# ── 构建镜像列表 ──────────────────────────────────────────────
IMAGES=("$APP_IMAGE" "$MYSQL_IMAGE" "$REDIS_IMAGE" "$BUSYBOX_IMAGE"
        "$PROMETHEUS_IMAGE" "$GRAFANA_IMAGE" "$KUBE_STATE_METRICS_IMAGE" "$NODE_EXPORTER_IMAGE")

# Nacos 仅在有存储配置时加入（kubeadm 默认禁用）
if [ -n "${NACOS_STORAGE:-}" ]; then
  IMAGES+=("$NACOS_IMAGE")
fi

# ── 构建/拉取镜像 ─────────────────────────────────────────────
log_step "准备镜像 (模式: ${MODE})..."

if [ "$MODE" = "build" ] && [ "$ENV" = "kubeadm" ]; then
  log_info "构建应用镜像: $APP_IMAGE"
  cd "$PROJECT_DIR"
  JAR_FILE=$(ls zhiyu-server/target/zhiyu-server-*.jar 2>/dev/null | head -1)
  if [ -z "$JAR_FILE" ]; then
    log_error "未找到编译产物，请先执行: ./mvnw clean package -DskipTests -pl zhiyu-server -am"
    cd - > /dev/null
    exit 1
  fi
  java -Djarmode=layertools -jar "$JAR_FILE" extract --destination target/extracted
  docker build -t "$APP_IMAGE" -f deploy/docker/Dockerfile.kubeadm .
  cd - > /dev/null
fi

for img in "${IMAGES[@]}"; do
  if [ "$img" = "$APP_IMAGE" ] && [ "$MODE" = "build" ]; then
    continue  # 已在上面构建
  fi
  if docker image inspect "$img" &>/dev/null; then
    log_info "  ✓ 本地已有: $img"
  else
    log_info "  拉取: $img"
    docker pull "$img"
  fi
done

# ── 导出镜像 ──────────────────────────────────────────────────
log_step "导出镜像到 tarball..."
docker save "${IMAGES[@]}" -o "$OUTPUT_FILE"

# ── 生成校验和 ────────────────────────────────────────────────
log_info "生成 SHA256..."
(cd "$OUTPUT_DIR" && shasum -a 256 "$(basename "$OUTPUT_FILE")" > "$(basename "$OUTPUT_FILE").sha256")

# ── 输出结果 ──────────────────────────────────────────────────
FILE_SIZE=$(du -h "$OUTPUT_FILE" | cut -f1)
echo ""
echo "========================================"
echo " 离线包已生成"
echo "========================================"
echo " 文件:     $OUTPUT_FILE"
echo " 大小:     $FILE_SIZE"
echo " 校验:     ${OUTPUT_FILE}.sha256"
echo ""
echo " 含镜像 (${#IMAGES[@]}):"
for img in "${IMAGES[@]}"; do
  echo "   • $img"
done
echo ""
echo "── 导入到目标节点 ──"
echo "  scp $OUTPUT_FILE root@<node>:/tmp/"
echo "  ssh root@<node> 'ctr -n k8s.io images import /tmp/$(basename "$OUTPUT_FILE")'"
echo ""
echo "── 或直接管道导入 ──"
echo "  docker save ${IMAGES[0]} ${IMAGES[1]} ... | ssh root@<node> ctr -n k8s.io images import -"
echo "========================================"
