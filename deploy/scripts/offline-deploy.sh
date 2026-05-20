#!/bin/bash
# ============================================================
# 离线部署脚本 — 在目标 K8s 节点上执行
#
# 用法:
#   ./deploy/scripts/offline-deploy.sh /path/to/bundle        # 从离线包部署
#   ./deploy/scripts/offline-deploy.sh --dry-run              # 仅校验
#
# 前提: kubectl + ctr 可用，离线包已解压到指定路径
# ============================================================
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()  { echo -e "${CYAN}[STEP]${NC}  $*"; }

DRY_RUN=false
BUNDLE_DIR=""

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    *) BUNDLE_DIR="$arg" ;;
  esac
done

# 默认：脚本所在目录为 deploy/scripts/，离线包在项目根下
if [ -z "$BUNDLE_DIR" ]; then
  SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
  DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
  BUNDLE_DIR="$(cd "$DEPLOY_DIR/.." && pwd)"
fi

BUNDLE_DIR="$(cd "$BUNDLE_DIR" 2>/dev/null && pwd || echo "$BUNDLE_DIR")"
IMAGES_TAR="${BUNDLE_DIR}/images.tar"
DEPLOY_SH="${BUNDLE_DIR}/deploy/deploy.sh"

# ── 从 deploy/envs/ 目录自动智能推断环境名 ────────────────────
# 直接读取 deploy/envs/ 下唯一的环境包子目录名称作为 ENV 变量
ENV=$(find "${BUNDLE_DIR}/deploy/envs" -mindepth 1 -maxdepth 1 -type d -exec basename {} \;)
ENV="${ENV:-kubeadm}"

echo "================================================"
echo " ZhiYu 离线部署"
echo " 环境: ${ENV}"
echo " 目录: ${BUNDLE_DIR}"
[ "$DRY_RUN" = true ] && echo " 模式: DRY-RUN（仅校验，不执行）"
echo "================================================"

# ── 自检 ──────────────────────────────────────────────────────
log_step "自检..."

if [ ! -f "$IMAGES_TAR" ]; then
  log_error "镜像包不存在: $IMAGES_TAR"
  exit 1
fi

if [ ! -f "$DEPLOY_SH" ]; then
  log_error "部署脚本不存在: $DEPLOY_SH"
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
else
  log_warn "未找到 SHA256 校验文件，跳过完整性校验"
fi

if [ "$DRY_RUN" = true ]; then
  echo ""
  echo "── 将要加载的镜像 ──"
  tar tf "$IMAGES_TAR" 2>/dev/null | grep -E '\.json$|manifest.json' | head -20 || true
  log_info "DRY-RUN 完成，跳过实际部署"
  exit 0
fi

# ── 加载镜像到 containerd ─────────────────────────────────────
log_step "加载镜像到 containerd..."
# ctr import 需要 sudo 权限访问 containerd socket
if ctr -n k8s.io images import "$IMAGES_TAR" 2>/dev/null; then
  log_info "镜像导入完成"
else
  log_info "尝试 sudo 导入..."
  sudo ctr -n k8s.io images import "$IMAGES_TAR"
  log_info "镜像导入完成"
fi

# ── 执行部署（跳过构建，镜像已预加载）────────────────────────
log_step "执行部署..."
export SKIP_BUILD=true
exec bash "$DEPLOY_SH" "$ENV" all
