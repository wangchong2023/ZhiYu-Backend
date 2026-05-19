#!/bin/bash
# ============================================================
# ZhiYu-Backend 一键部署脚本
# 用法:
#   ./deploy/deploy.sh <env> <action>
#
# Actions:
#   check   — 验证前置条件
#   infra   — 部署基础设施（MySQL StatefulSet + Redis + Nacos）
#   init    — 初始化数据（DB 库 + Nacos 配置 + JWT 密钥）
#   build   — Maven 编译 + Docker 构建 + 推送镜像
#   deploy  — 部署应用到 K8s（ConfigMap/Secret → Deployment → Service → Ingress → HPA/PDB/NetworkPolicy）
#   all     — 按序执行以上全部 (infra → build → init → deploy)
#   monitor — 部署监控栈（Prometheus + Grafana + kube-state-metrics + node-exporter）
#   status  — 显示各组件状态
#
# 示例:
#   ./deploy/deploy.sh dev all        # 开发环境一键部署 (ACK)
#   ./deploy/deploy.sh test all       # 测试环境一键部署
#   ./deploy/deploy.sh kubeadm all    # 本地 kubeadm 一键部署
#   ./deploy/deploy.sh staging infra  # 仅部署预发布环境基础设施
#   ./deploy/deploy.sh release deploy # 仅部署应用到生产环境
#   ./deploy/deploy.sh kubeadm monitor # 部署 Prometheus + Grafana 监控栈
# ============================================================
set -euo pipefail

# ── 颜色 ──────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()  { echo -e "${CYAN}[STEP]${NC}  $*"; }

# ── 参数解析 ──────────────────────────────────────────────────
DRY_RUN=false
ENV=""
ACTION="all"

for arg in "$@"; do
  case "$arg" in
    --dry-run)
      DRY_RUN=true
      ;;
    dev|test|staging|release|kubeadm)
      ENV="$arg"
      ;;
    check|infra|init|build|deploy|monitoring|all|status)
      ACTION="$arg"
      ;;
    *)
      log_error "无效参数: $arg"
      echo "用法: $0 <env> [action] [--dry-run]"
      echo "  env:     dev | test | staging | release | kubeadm"
      echo "  action:  check | infra | init | build | deploy | monitoring | all | status"
      echo "  --dry-run: 仅验证（kubectl --dry-run=client），不真正部署"
      echo ""
      echo "示例: $0 dev all --dry-run"
      exit 1
      ;;
  esac
done

if [ -z "$ENV" ]; then
  echo "用法: $0 <env> [action] [--dry-run]"
  echo "  env:     dev | test | staging | release | kubeadm"
  echo "  action:  check | infra | init | build | deploy | monitoring | all | status"
  echo "  --dry-run: 仅验证（kubectl --dry-run=client），不真正部署"
  echo ""
  echo "示例: $0 dev all --dry-run"
  exit 1
fi

if [ "$DRY_RUN" = true ]; then
  log_warn "Dry-run 模式：所有 kubectl apply 仅做客户端验证，不会实际部署"
fi

# ── 路径 ──────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="${SCRIPT_DIR}/envs/${ENV}.env"
APP_DIR="${SCRIPT_DIR}/app"
INFRA_DIR="${SCRIPT_DIR}/infra"
SCRIPTS_DIR="${SCRIPT_DIR}/scripts"

# 加载环境变量
if [ -f "$ENV_FILE" ]; then
  source "$ENV_FILE"
  log_info "已加载环境: ${ENV} (namespace: ${K8S_NAMESPACE})"
else
  log_error "环境文件不存在: $ENV_FILE"
  exit 1
fi

# ── 默认值 ────────────────────────────────────────────────────
DOCKER_REGISTRY="${DOCKER_REGISTRY:-registry.cn-hangzhou.aliyuncs.com}"
DOCKER_IMAGE="${DOCKER_IMAGE:-zhiyu/zhiyu-backend}"
DOCKER_TAG="${DOCKER_TAG:-dev-$(date +%Y%m%d-%H%M%S)}"
K8S_NAMESPACE="${K8S_NAMESPACE:-zhiyu-dev}"

# 计算完整镜像引用（处理无 registry 场景，如 kubeadm）
if [ -n "${DOCKER_REGISTRY:-}" ]; then
  IMAGE_FULL="${DOCKER_REGISTRY}/${DOCKER_IMAGE}:${DOCKER_TAG}"
else
  IMAGE_FULL="${DOCKER_IMAGE}:${DOCKER_TAG}"
fi
export IMAGE_FULL
IMAGE_PULL_POLICY="${IMAGE_PULL_POLICY:-Always}"
export IMAGE_PULL_POLICY

# 镜像版本（env 文件可覆盖）
export MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.0}"
export REDIS_IMAGE="${REDIS_IMAGE:-redis:7-alpine}"
export NACOS_IMAGE="${NACOS_IMAGE:-nacos/nacos-server:v2.4.0}"
export BUSYBOX_IMAGE="${BUSYBOX_IMAGE:-busybox:1.36}"
export PROMETHEUS_IMAGE="${PROMETHEUS_IMAGE:-prom/prometheus:v3.7.0}"
export GRAFANA_IMAGE="${GRAFANA_IMAGE:-grafana/grafana:11.6.0}"
export KUBE_STATE_METRICS_IMAGE="${KUBE_STATE_METRICS_IMAGE:-registry.k8s.io/kube-state-metrics/kube-state-metrics:v2.15.0}"
export NODE_EXPORTER_IMAGE="${NODE_EXPORTER_IMAGE:-prom/node-exporter:v1.9.0}"

# ── 前置条件检查 ──────────────────────────────────────────────
do_check() {
  log_step "检查前置条件..."

  local ok=true

  check_cmd() {
    if command -v "$1" &>/dev/null; then
      log_info "  ✓ $1 ($(command -v "$1"))"
    else
      log_error "  ✗ $1 未安装"
      ok=false
    fi
  }

  check_cmd kubectl
  check_cmd docker
  check_cmd openssl

  # kubectl-argo-rollouts 插件（可选 — staging/release 金丝雀发布使用）
  if command -v kubectl-argo-rollouts &>/dev/null; then
    log_info "  (optional) kubectl-argo-rollouts ($(kubectl-argo-rollouts version 2>&1 | head -1 | cut -c1-40))"
  else
    log_warn "  (optional) kubectl-argo-rollouts 未安装 — staging/release 发布将回退到 kubectl wait"
  fi

  if kubectl cluster-info &>/dev/null; then
    log_info "  ✓ kubectl 已连接到集群"
  else
    log_error "  ✗ kubectl 无法连接到集群"
    ok=false
  fi

  if [ "$ok" = false ]; then
    log_error "前置条件不满足，退出"
    exit 1
  fi

  log_info "前置条件检查通过 ✓"
}

# ── 模板 apply 辅助函数 ───────────────────────────────────────
apply_template() {
  local file="$1"
  local label="$2"
  shift 2
  local extra_flags=("$@")

  if [ "$DRY_RUN" = true ]; then
    envsubst < "$file" | kubectl apply -n "${K8S_NAMESPACE}" --dry-run=client -f -
    log_info "  [DRY-RUN] ${label} (验证通过)"
  else
    envsubst < "$file" | kubectl apply -n "${K8S_NAMESPACE}" ${extra_flags:+"${extra_flags[@]}"} -f -
    log_info "  ${label} 已部署"
  fi
}

# ── 部署基础设施 ──────────────────────────────────────────────
do_infra() {
  log_step "部署基础设施 (${ENV})..."

  # 创建 namespace
  if ! kubectl get namespace "${K8S_NAMESPACE}" &>/dev/null; then
    kubectl create namespace "${K8S_NAMESPACE}"
    log_info "  Namespace 已创建: ${K8S_NAMESPACE}"
  else
    log_info "  Namespace 已存在: ${K8S_NAMESPACE}"
  fi

  # 部署 MySQL（StatefulSet — 仅当 MYSQL_STORAGE 非空）
  if [ -n "${MYSQL_STORAGE:-}" ]; then
    log_info "部署 MySQL（StatefulSet）..."
    apply_template "${INFRA_DIR}/mysql-statefulset.yaml" "MySQL (StatefulSet + Headless SVC)"
  else
    log_info "跳过 MySQL（使用外部 RDS: ${MYSQL_HOST}:${MYSQL_PORT}）"
  fi

  # 部署 Redis（仅当 REDIS_STORAGE 非空）
  if [ -n "${REDIS_STORAGE:-}" ]; then
    log_info "部署 Redis..."
    apply_template "${INFRA_DIR}/redis.yaml" "Redis"
  else
    log_info "跳过 Redis（使用外部实例: ${REDIS_HOST}:${REDIS_PORT}）"
  fi

  # 部署 Nacos（仅当 NACOS_STORAGE 非空）
  if [ -n "${NACOS_STORAGE:-}" ]; then
    log_info "部署 Nacos..."
    apply_template "${INFRA_DIR}/nacos.yaml" "Nacos"
  else
    log_info "跳过 Nacos（使用外部集群: ${NACOS_HOST}:${NACOS_PORT}）"
  fi

  # 等待就绪
  log_info "等待 Pod 就绪（最多 180 秒）..."
  kubectl wait --for=condition=ready pod --all -n "${K8S_NAMESPACE}" --timeout=180s 2>/dev/null || log_warn "部分 Pod 可能仍在启动中"

  log_info "基础设施部署完成 ✓"
}

# ── 初始化数据 ────────────────────────────────────────────────
do_init() {
  log_step "初始化数据 (${ENV})..."

  # 1. 创建数据库
  if [ -f "${SCRIPTS_DIR}/init-db.sh" ]; then
    bash "${SCRIPTS_DIR}/init-db.sh" "$ENV"
  else
    log_warn "init-db.sh 不存在，跳过数据库初始化"
  fi

  # 2. 初始化 Nacos 配置
  if [ -f "${SCRIPTS_DIR}/init-nacos.sh" ]; then
    log_info "初始化 Nacos 配置..."
    bash "${SCRIPTS_DIR}/init-nacos.sh" "$ENV"
  else
    log_warn "init-nacos.sh 不存在，跳过 Nacos 初始化"
  fi

  # 3. 生成 JWT 密钥
  if [ -n "${JWT_KEY_DIR:-}" ] && [ ! -f "${JWT_KEY_DIR}/jwt-private.pem" ]; then
    log_info "生成 JWT RS256 密钥对..."
    mkdir -p "$JWT_KEY_DIR"
    bash "${SCRIPTS_DIR}/gen-jwt-keys.sh" "$JWT_KEY_DIR"
  elif [ -n "${JWT_KEY_DIR:-}" ]; then
    log_info "JWT 密钥已存在: ${JWT_KEY_DIR}"
  fi

  log_info "数据初始化完成 ✓"
}

# ── 构建镜像 ──────────────────────────────────────────────────
do_build() {
  log_step "构建 Docker 镜像 (${ENV})..."

  cd "$PROJECT_DIR"

  # 1. 检查预编译 JAR 是否存在
  local jar_file
  jar_file=$(ls backend/zhiyu-server/target/zhiyu-server-*.jar 2>/dev/null | head -1)
  if [ -z "$jar_file" ]; then
    log_error "未找到编译产物，请先执行 Maven 编译: ./mvnw -f backend/pom.xml clean package -DskipTests -pl zhiyu-server -am"
    exit 1
  fi
  log_info "使用 JAR: $jar_file"

  # 2. 提取分层 JAR（所有 JRE 镜像共用）
  log_info "提取 Spring Boot 分层 JAR..."
  java -Djarmode=layertools -jar "$jar_file" extract --destination target/extracted

  # 3. 构建后端运行时镜像
  local build_args=("-t" "$IMAGE_FULL")
  # TARGETARCH 自动检测当前架构
  [ -z "${TARGETARCH:-}" ] && TARGETARCH=$(uname -m | sed 's/x86_64/amd64/;s/aarch64/arm64/')
  build_args+=("--build-arg" "TARGETARCH=${TARGETARCH}")

  log_info "Docker 构建后端: $IMAGE_FULL (arch: ${TARGETARCH})"
  if [ "$ENV" = "kubeadm" ]; then
    docker build "${build_args[@]}" -f deploy/docker/Dockerfile.kubeadm "$PROJECT_DIR"
  else
    docker build "${build_args[@]}" -f Dockerfile "$PROJECT_DIR"
  fi

  # 4. 推送/导入镜像（kubeadm 环境跳过推送）
  if [ "${SKIP_PUSH:-false}" = "true" ]; then
    log_info "跳过镜像推送（SKIP_PUSH=true）"
    if [ "$ENV" = "kubeadm" ]; then
      log_info "导入镜像到 containerd..."
      docker save "$IMAGE_FULL" \
        | ctr -n k8s.io images import - \
        || log_warn "ctr import 失败，手动执行: docker save $IMAGE_FULL | ctr -n k8s.io images import -"
    fi
  else
    log_info "推送镜像..."
    docker push "$IMAGE_FULL"
  fi

  log_info "镜像构建完成 ✓"
  log_info "  Backend:   $IMAGE_FULL"
}

# ── 部署应用 ──────────────────────────────────────────────────
do_deploy() {
  log_step "部署应用到 K8s (${ENV})..."

  # 1. 部署 ConfigMap
  if [ -f "${APP_DIR}/configmap.yaml" ]; then
    apply_template "${APP_DIR}/configmap.yaml" "ConfigMap"
  fi

  # 2. 部署 Secret（含 JWT 密钥）
  if [ -n "${JWT_KEY_DIR:-}" ] && [ -f "${JWT_KEY_DIR}/jwt-private.pem" ]; then
    local jwt_private_b64 jwt_public_b64
    jwt_private_b64=$(base64 < "${JWT_KEY_DIR}/jwt-private.pem" | tr -d '\n')
    jwt_public_b64=$(base64 < "${JWT_KEY_DIR}/jwt-public.pem" | tr -d '\n')
    local secret_dry_flag=""
    [ "$DRY_RUN" = true ] && secret_dry_flag="--dry-run=client"

    kubectl create secret generic zhiyu-backend-secret \
      -n "${K8S_NAMESPACE}" \
      --from-literal=JWT_PRIVATE_KEY="$jwt_private_b64" \
      --from-literal=JWT_PUBLIC_KEY="$jwt_public_b64" \
      --from-literal=SPRING_DATASOURCE_USERNAME="${MYSQL_USER:-zhiyu}" \
      --from-literal=SPRING_DATASOURCE_PASSWORD="${MYSQL_PASSWORD:-}" \
      --from-literal=SPRING_DATA_REDIS_PASSWORD="${REDIS_PASSWORD:-}" \
      --dry-run=client -o yaml | kubectl apply -f - $secret_dry_flag
    log_info "  Secret 已部署（含 JWT 密钥）"
  else
    log_warn "  JWT 密钥不存在，请先运行: $0 $ENV init"
  fi

  # 3. 部署应用核心资源（staging/release 使用 Argo Rollout 金丝雀发布）
  if [ "$ENV" = "release" ] || [ "$ENV" = "staging" ]; then
    if [ -f "${APP_DIR}/rollout.yaml" ]; then
      apply_template "${APP_DIR}/rollout.yaml" "Rollout (Canary)"
    else
      log_warn "rollout.yaml 不存在，回退到 Deployment"
      apply_template "${APP_DIR}/deployment.yaml" "Deployment"
    fi
  else
    apply_template "${APP_DIR}/deployment.yaml" "Deployment"
  fi
  apply_template "${APP_DIR}/service.yaml" "Service"
  apply_template "${APP_DIR}/ingress.yaml" "Ingress"

  # 5. 部署 HPA / PDB / NetworkPolicy / ServiceAccount
  if [ -f "${APP_DIR}/hpa.yaml" ]; then
    apply_template "${APP_DIR}/hpa.yaml" "HPA"
  fi
  if [ -f "${APP_DIR}/pdb.yaml" ]; then
    apply_template "${APP_DIR}/pdb.yaml" "PDB"
  fi
  if [ -f "${APP_DIR}/network-policy.yaml" ]; then
    apply_template "${APP_DIR}/network-policy.yaml" "NetworkPolicy" || log_warn "NetworkPolicy 部署失败（可能 CNI 不支持）"
  fi
  if [ -f "${APP_DIR}/service-account.yaml" ]; then
    apply_template "${APP_DIR}/service-account.yaml" "ServiceAccount"
  fi

  # 6. 等待应用就绪
  log_info "等待应用就绪..."
  if [ "$DRY_RUN" = true ]; then
    log_info "  [DRY-RUN] 跳过等待步骤"
  elif [ "$ENV" = "release" ] || [ "$ENV" = "staging" ]; then
    # Argo Rollout: 等待金丝雀分析通过
    if command -v kubectl-argo-rollouts &>/dev/null; then
      kubectl argo rollouts status zhiyu-backend -n "${K8S_NAMESPACE}" --timeout=300s 2>/dev/null || log_warn "Rollout 等待超时，请手动检查: kubectl argo rollouts status zhiyu-backend -n ${K8S_NAMESPACE}"
    else
      log_warn "kubectl-argo-rollouts 插件未安装，回退到 kubectl wait"
      kubectl wait --for=condition=ready pod -l app=zhiyu-backend -n "${K8S_NAMESPACE}" --timeout=180s 2>/dev/null || log_warn "应用 Pod 可能仍在启动中"
    fi
  else
    kubectl wait --for=condition=ready pod -l app=zhiyu-backend -n "${K8S_NAMESPACE}" --timeout=180s 2>/dev/null || log_warn "应用 Pod 可能仍在启动中，检查: kubectl get pods -n ${K8S_NAMESPACE}"
  fi

  log_info "应用部署完成 ✓"
}

# ── 部署监控栈 ──────────────────────────────────────────────────
do_monitoring() {
  log_step "部署监控栈 (Prometheus + Grafana + kube-state-metrics + node-exporter)..."

  MONITORING_DIR="${SCRIPT_DIR}/monitoring"
  MONITORING_NS="monitoring"

  # 创建 monitoring 命名空间
  if ! kubectl get namespace "$MONITORING_NS" &>/dev/null; then
    kubectl create namespace "$MONITORING_NS"
    kubectl label namespace "$MONITORING_NS" name=monitoring --overwrite
    log_info "  命名空间 $MONITORING_NS 已创建"
  else
    log_info "  命名空间 $MONITORING_NS 已存在"
  fi

  # 部署 kube-state-metrics（RBAC + Deployment + Service）
  log_info "部署 kube-state-metrics..."
  envsubst < "${MONITORING_DIR}/kube-state-metrics.yaml" | kubectl apply -n "$MONITORING_NS" -f -
  log_info "  kube-state-metrics 已部署"

  # 部署 node-exporter（DaemonSet）
  log_info "部署 node-exporter..."
  envsubst < "${MONITORING_DIR}/node-exporter.yaml" | kubectl apply -n "$MONITORING_NS" -f -
  log_info "  node-exporter 已部署"

  # 部署 Prometheus
  log_info "部署 Prometheus..."
  envsubst < "${MONITORING_DIR}/prometheus.yaml" | kubectl apply -n "$MONITORING_NS" -f -
  log_info "  Prometheus 已部署"

  # 部署 Grafana
  log_info "部署 Grafana..."
  envsubst < "${MONITORING_DIR}/grafana.yaml" | kubectl apply -n "$MONITORING_NS" -f -
  log_info "  Grafana 已部署 (NodePort 30000)"

  # 等待所有监控组件就绪
  log_info "等待监控 Pod 就绪（最多 120 秒）..."
  kubectl wait --for=condition=ready pod --all -n "$MONITORING_NS" --timeout=120s 2>/dev/null \
    || log_warn "部分监控 Pod 可能仍在启动中"

  log_info "监控栈部署完成 ✓"
  echo ""
  echo "========================================"
  echo " 监控栈访问"
  echo "========================================"
  echo " Grafana:     http://$(hostname -I 2>/dev/null | awk '{print $1}' || echo '<node-ip>'):30000"
  echo " Prometheus:  kubectl port-forward -n monitoring svc/prometheus 9090:9090"
  echo " 凭据:        admin / ${GRAFANA_PASSWORD}"
  echo "========================================"
}

# ── 状态检查 ──────────────────────────────────────────────────
do_status() {
  log_step "组件状态 (${ENV}):"
  echo ""

  echo "── Namespace ──────────────────────"
  kubectl get namespace "${K8S_NAMESPACE}" 2>/dev/null || echo "  不存在"

  echo ""
  echo "── StatefulSets ───────────────────"
  kubectl get statefulset -n "${K8S_NAMESPACE}" 2>/dev/null || echo "  无 StatefulSet"

  echo ""
  echo "── Deployments/Rollouts ────────────"
  kubectl get deploy -n "${K8S_NAMESPACE}" 2>/dev/null || echo "  无 Deployment"
  if command -v kubectl-argo-rollouts &>/dev/null; then
    kubectl argo rollouts list -n "${K8S_NAMESPACE}" 2>/dev/null || echo "  无 Rollout"
  fi

  echo ""
  echo "── Pods ───────────────────────────"
  kubectl get pods -n "${K8S_NAMESPACE}" -o wide 2>/dev/null || echo "  无 Pod"

  echo ""
  echo "── Services ───────────────────────"
  kubectl get svc -n "${K8S_NAMESPACE}" 2>/dev/null || echo "  无 Service"

  echo ""
  echo "── Ingress ────────────────────────"
  kubectl get ingress -n "${K8S_NAMESPACE}" 2>/dev/null || echo "  无 Ingress"

  echo ""
  echo "── HPA ────────────────────────────"
  kubectl get hpa -n "${K8S_NAMESPACE}" 2>/dev/null || echo "  无 HPA"

  echo ""
  echo "── PDB ────────────────────────────"
  kubectl get pdb -n "${K8S_NAMESPACE}" 2>/dev/null || echo "  无 PDB"

  echo ""
  echo "── NetworkPolicy ──────────────────"
  kubectl get networkpolicy -n "${K8S_NAMESPACE}" 2>/dev/null || echo "  无 NetworkPolicy"

  echo ""
  echo "── 应用健康检查 ───────────────────"
  local backend_pod
  backend_pod=$(kubectl get pods -n "${K8S_NAMESPACE}" -l app=zhiyu-backend -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
  if [ -n "$backend_pod" ]; then
    echo "  Pod: $backend_pod"
    kubectl exec -n "${K8S_NAMESPACE}" "$backend_pod" -- curl -s http://localhost:8080/actuator/health 2>/dev/null | python3 -m json.tool 2>/dev/null || echo "  无法获取健康状态"
  else
    echo "  应用 Pod 未找到"
  fi

  echo ""
  echo "── 访问入口 ───────────────────────"
  if kubectl get ingress -n "${K8S_NAMESPACE}" zhiyu-backend &>/dev/null; then
    echo "  https://${INGRESS_HOST}/api/v1"
  else
    echo "  无 Ingress — 使用 port-forward:"
    echo "  kubectl port-forward -n ${K8S_NAMESPACE} svc/zhiyu-backend 8080:8080"
    echo "  → http://localhost:8080/api/v1"
  fi
}

# ── 主流程 ────────────────────────────────────────────────────
echo "================================================"
echo " ZhiYu-Backend 部署脚本"
echo " 环境: ${ENV}  |  操作: ${ACTION}"
echo "================================================"
echo ""

case "$ACTION" in
  check)
    do_check
    ;;
  infra)
    do_check
    do_infra
    ;;
  init)
    do_check
    do_init
    ;;
  build)
    do_check
    do_build
    ;;
  deploy)
    do_check
    do_deploy
    ;;
  monitoring)
    do_check
    do_monitoring
    ;;
  all)
    do_check
    do_infra
    do_build
    do_init
    do_deploy
    echo ""
    echo "================================================"
    echo " 一键部署完成！"
    echo "================================================"
    do_status
    ;;
  status)
    do_status
    ;;
esac
