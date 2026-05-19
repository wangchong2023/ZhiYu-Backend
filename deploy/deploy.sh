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
#   cleanup — 一键清理所有部署资源（应用 + 基础设施 + 监控栈，需确认）
#   status  — 显示各组件状态
#   show-secrets — 显示数据库/Redis/Nacos/Grafana 密码（运维登录用）
#
# 示例:
#   ./deploy/deploy.sh dev all        # 开发环境一键部署 (ACK)
#   ./deploy/deploy.sh test all       # 测试环境一键部署
#   ./deploy/deploy.sh kubeadm all    # 本地 kubeadm 一键部署
#   ./deploy/deploy.sh staging infra  # 仅部署预发布环境基础设施
#   ./deploy/deploy.sh release deploy # 仅部署应用到生产环境
#   ./deploy/deploy.sh kubeadm monitor # 部署 Prometheus + Grafana 监控栈
#   ./deploy/deploy.sh dev cleanup    # 清理开发环境所有资源
#   ./deploy/deploy.sh kubeadm cleanup # 清理 kubeadm 环境所有资源
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
    check|infra|init|build|deploy|monitoring|cleanup|all|status|show-secrets)
      ACTION="$arg"
      ;;
    *)
      log_error "无效参数: $arg"
      echo "用法: $0 <env> [action] [--dry-run]"
      echo "  env:     dev | test | staging | release | kubeadm"
      echo "  action:  check | infra | init | build | deploy | monitoring | all | status | show-secrets"
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
  echo "  action:  check | infra | init | build | deploy | monitoring | all | status | show-secrets"
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

  # 加载已持久化的密码（deploy/secrets/<env>/passwords.env）
  # 允许环境变量覆盖（CI/CD 通过 export 预先设置）
  password_file="${SCRIPT_DIR}/secrets/${ENV}/passwords.env"
  if [ -f "$password_file" ]; then
    source "$password_file"
  fi
else
  log_error "环境文件不存在: $ENV_FILE"
  exit 1
fi

# 确保证书/密钥就绪（首先生成密码，后续动作依赖密码非空）
if [ -f "${SCRIPTS_DIR}/ensure-secrets.sh" ]; then
  source "${SCRIPTS_DIR}/ensure-secrets.sh" "$ENV"
fi

# ── 默认值 ────────────────────────────────────────────────────
# 注意：使用 ${VAR-value}（不带冒号），仅未设置时使用默认值，空字符串保留
DOCKER_REGISTRY="${DOCKER_REGISTRY-registry.cn-hangzhou.aliyuncs.com}"
DOCKER_IMAGE="${DOCKER_IMAGE-zhiyu/zhiyu-backend}"
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

  # 1. 密钥生命周期管理（首次自动生成密码 + JWT 密钥，后续复用）
  #    安全策略: docs/SECURITY.md §2 密钥管理策略
  if [ -f "${SCRIPTS_DIR}/ensure-secrets.sh" ]; then
    log_info "确保证书/密钥就绪..."
    source "${SCRIPTS_DIR}/ensure-secrets.sh" "$ENV"
  else
    log_warn "ensure-secrets.sh 不存在，跳过密钥初始化"
  fi

  # 2. 创建数据库
  if [ -f "${SCRIPTS_DIR}/init-db.sh" ]; then
    bash "${SCRIPTS_DIR}/init-db.sh" "$ENV"
  else
    log_warn "init-db.sh 不存在，跳过数据库初始化"
  fi

  # 3. 初始化 Nacos 配置
  if [ -f "${SCRIPTS_DIR}/init-nacos.sh" ]; then
    log_info "初始化 Nacos 配置..."
    bash "${SCRIPTS_DIR}/init-nacos.sh" "$ENV"
  else
    log_warn "init-nacos.sh 不存在，跳过 Nacos 初始化"
  fi

  log_info "数据初始化完成 ✓"
}

# ── 构建镜像 ──────────────────────────────────────────────────
# 仅构建 Docker 镜像，Maven 编译应在本地完成后再上传 JAR 到远端
do_build() {
  log_step "构建 Docker 镜像 (${ENV})..."

  cd "$PROJECT_DIR"

  # 1. 检查预编译 JAR（离线部署：本地编译后 scp 上传到远端）
  local jar_file
  jar_file=$(ls backend/zhiyu-server/target/zhiyu-server-*.jar 2>/dev/null | head -1)
  if [ -z "$jar_file" ]; then
    log_error "未找到编译产物"
    log_error "本地编译:  ./mvnw -f backend/pom.xml clean package -DskipTests -pl zhiyu-server -am"
    log_error "上传 JAR:   scp backend/zhiyu-server/target/zhiyu-server-*.jar <user>@<host>:<path>/backend/zhiyu-server/target/"
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
      TMP_TAR="${TMPDIR:-/tmp}/zhiyu-backend-$$.tar" ; 
      docker save "$IMAGE_FULL" -o "$TMP_TAR" \
        && echo root | sudo -S ctr -n k8s.io images import "$TMP_TAR" \
        && rm -f "$TMP_TAR" \
        || log_warn "ctr import 失败，手动执行: docker save $IMAGE_FULL | sudo ctr -n k8s.io images import -"
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

# ── 一键清理 ──────────────────────────────────────────────────
do_cleanup() {
  log_step "清理部署资源 (${ENV})..."
  echo ""

  local namespace="${K8S_NAMESPACE}"
  local monitoring_ns="monitoring"

  # 预览将要删除的资源
  log_info "将要清理以下资源:"
  echo "  ┌─ 应用层 (${namespace}):"
  echo "  │  - Deployment / Rollout"
  echo "  │  - Service, Ingress"
  echo "  │  - HPA, PDB, NetworkPolicy, ServiceAccount"
  echo "  │  - ConfigMap, Secret"
  echo "  │"
  echo "  ├─ 基础设施 (${namespace}):"
  echo "  │  - MySQL StatefulSet + Services + PVC + Secret"
  echo "  │  - Redis StatefulSet + Services + PVC + Secret"
  echo "  │  - Nacos Deployment + Service + PVC + Secret"
  echo "  │"
  echo "  ├─ 监控栈 (${monitoring_ns}):"
  echo "  │  - Prometheus + Grafana + kube-state-metrics + node-exporter"
  echo "  │"
  echo "  └─ Namespace: ${namespace}, ${monitoring_ns}"
  echo ""

  if [ "$DRY_RUN" = true ]; then
    log_info "[DRY-RUN] 仅展示，不实际删除"
    echo ""
    echo "将执行 (dry-run):"
    echo "  kubectl delete namespace ${namespace} ${monitoring_ns}"
    echo ""
    if [ "$ENV" = "kubeadm" ]; then
      echo "  sudo kubeadm reset --force"
    fi
    return
  fi

  # 安全确认
  echo "================================================"
  log_warn "此操作将永久删除 ${ENV} 环境的所有资源！"
  echo ""
  echo "  环境:       ${ENV}"
  echo "  Namespace:  ${namespace}"
  if [ "$ENV" = "release" ] || [ "$ENV" = "staging" ]; then
    echo ""
    echo "  ⚠️  生产/预发布环境！请输入环境名确认:"
    read -p "  输入 '${ENV}' 以确认: " confirm_env
    if [ "$confirm_env" != "$ENV" ]; then
      log_info "已取消清理"
      return
    fi
  else
    echo "  将在 10 秒后执行，Ctrl+C 取消..."
    sleep 10
  fi
  echo "================================================"

  # 1. 应用资源
  log_info "清理应用资源..."
  kubectl delete deploy -n "$namespace" -l app=zhiyu-backend --ignore-not-found --wait=false 2>/dev/null || true
  kubectl delete rollout -n "$namespace" -l app=zhiyu-backend --ignore-not-found --wait=false 2>/dev/null || true
  kubectl delete svc -n "$namespace" zhiyu-backend --ignore-not-found 2>/dev/null || true
  kubectl delete ingress -n "$namespace" zhiyu-backend --ignore-not-found 2>/dev/null || true
  kubectl delete hpa -n "$namespace" zhiyu-backend --ignore-not-found 2>/dev/null || true
  kubectl delete pdb -n "$namespace" zhiyu-backend --ignore-not-found 2>/dev/null || true
  kubectl delete networkpolicy -n "$namespace" zhiyu-backend --ignore-not-found 2>/dev/null || true
  kubectl delete sa -n "$namespace" zhiyu-backend --ignore-not-found 2>/dev/null || true
  kubectl delete configmap -n "$namespace" zhiyu-backend-config --ignore-not-found 2>/dev/null || true
  kubectl delete secret -n "$namespace" zhiyu-backend-secret --ignore-not-found 2>/dev/null || true
  log_info "  应用资源已清理"

  # 2. 基础设施资源
  log_info "清理基础设施资源..."
  kubectl delete sts -n "$namespace" mysql --ignore-not-found --wait=false 2>/dev/null || true
  kubectl delete svc -n "$namespace" mysql mysql-headless --ignore-not-found 2>/dev/null || true
  kubectl delete pvc -n "$namespace" data-mysql-0 --ignore-not-found 2>/dev/null || true
  kubectl delete secret -n "$namespace" mysql-secret --ignore-not-found 2>/dev/null || true

  kubectl delete sts -n "$namespace" redis --ignore-not-found --wait=false 2>/dev/null || true
  kubectl delete svc -n "$namespace" redis redis-headless --ignore-not-found 2>/dev/null || true
  kubectl delete pvc -n "$namespace" data-redis-0 --ignore-not-found 2>/dev/null || true
  kubectl delete secret -n "$namespace" redis-secret --ignore-not-found 2>/dev/null || true

  kubectl delete deploy -n "$namespace" nacos --ignore-not-found --wait=false 2>/dev/null || true
  kubectl delete svc -n "$namespace" nacos --ignore-not-found 2>/dev/null || true
  kubectl delete pvc -n "$namespace" nacos-data --ignore-not-found 2>/dev/null || true
  kubectl delete secret -n "$namespace" nacos-secret --ignore-not-found 2>/dev/null || true

  sleep 3
  log_info "  基础设施资源已清理"

  # 3. 监控命名空间
  if kubectl get namespace "$monitoring_ns" &>/dev/null; then
    log_info "清理监控栈 (${monitoring_ns})..."
    kubectl delete namespace "$monitoring_ns" --ignore-not-found --wait=false
    log_info "  监控栈已清理"
  else
    log_info "  监控命名空间不存在，跳过"
  fi

  # 4. 应用命名空间
  if kubectl get namespace "$namespace" &>/dev/null; then
    log_info "清理命名空间 (${namespace})..."
    kubectl delete namespace "$namespace" --ignore-not-found --wait=false
    log_info "  命名空间已删除"
  fi

  # 5. ClusterRoleBinding (kube-state-metrics)
  kubectl delete clusterrolebinding kube-state-metrics --ignore-not-found 2>/dev/null || true
  kubectl delete clusterrole kube-state-metrics --ignore-not-found 2>/dev/null || true

  # 6. kubeadm: 可选集群重置
  if [ "$ENV" = "kubeadm" ]; then
    echo ""
    echo "================================================"
    log_warn "检测到 kubeadm 环境，是否同时执行 kubeadm reset？"
    echo "  这将删除整个 K8s 集群数据（包括所有非 zhiyu 工作负载）！"
    echo ""
    read -p "  输入 'yes' 确认 kubeadm reset，其他键跳过: " reset_confirm
    if [ "$reset_confirm" = "yes" ]; then
      log_info "执行 kubeadm reset..."
      sudo kubeadm reset --force || log_warn "kubeadm reset 失败，请手动执行"
      log_info "  kubeadm reset 完成"
    else
      log_info "跳过 kubeadm reset"
    fi
  fi

  # 7. 本地文件清理（可选）
  echo ""
  read -p "  是否清理本地 Docker 镜像和密码文件？[y/N]: " clean_local
  if [ "$clean_local" = "y" ] || [ "$clean_local" = "Y" ]; then
    docker rmi "$IMAGE_FULL" 2>/dev/null || true
    rm -f "${SCRIPT_DIR}/secrets/${ENV}/passwords.env"
    rm -f "${SCRIPT_DIR}/secrets/${ENV}/jwt-private.pem"
    rm -f "${SCRIPT_DIR}/secrets/${ENV}/jwt-public.pem"
    log_info "  本地文件已清理"
  fi

  echo ""
  log_info "清理完成 ✓"
}

# ── 显示密码（运维登录用）───────────────────────────────────
do_show_secrets() {
  local namespace="${K8S_NAMESPACE}"

  echo ""
  echo "================================================"
  echo " ${ENV} 环境凭证"
  echo "================================================"
  echo ""

  # 优先读取本地密码文件
  local password_file="${SCRIPT_DIR}/secrets/${ENV}/passwords.env"
  if [ -f "$password_file" ]; then
    echo "来源: ${password_file}"
    echo ""
    source "$password_file"

    printf "  %-24s %-20s %s\n" "组件" "用户名" "密码"
    printf "  %-24s %-20s %s\n" "────" "────" "────"
    printf "  %-24s %-20s %s\n" "MySQL (root)" "root" "${MYSQL_ROOT_PASSWORD:-<未设置>}"
    printf "  %-24s %-20s %s\n" "MySQL (应用)" "${MYSQL_USER:-zhiyu}" "${MYSQL_PASSWORD:-<未设置>}"
    printf "  %-24s %-20s %s\n" "Redis" "<无用户名>" "${REDIS_PASSWORD:-<未设置>}"
    printf "  %-24s %-20s %s\n" "Nacos" "nacos" "${NACOS_PASSWORD:-<未设置>}"
    printf "  %-24s %-20s %s\n" "Grafana" "admin" "${GRAFANA_PASSWORD:-<未设置>}"
    echo ""
    echo "  Nacos Identity: ${NACOS_IDENTITY_KEY:-serverIdentity} / ${NACOS_IDENTITY_VALUE:-<未设置>}"
    echo ""
  fi

  # 从 K8s Secret 读取（兜底）
  echo "── K8s Secrets (${namespace}) ──"
  echo ""

  if kubectl get namespace "$namespace" &>/dev/null; then
    # MySQL
    local mysql_pw
    mysql_pw=$(kubectl get secret mysql-secret -n "$namespace" -o jsonpath='{.data.password}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
    if [ -n "$mysql_pw" ]; then
      echo "  MySQL (zhiyu): ${mysql_pw}"
    else
      echo "  MySQL: <Secret 不存在>"
    fi
    local mysql_root_pw
    mysql_root_pw=$(kubectl get secret mysql-secret -n "$namespace" -o jsonpath='{.data.root-password}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
    [ -n "$mysql_root_pw" ] && echo "  MySQL (root):  ${mysql_root_pw}"

    # Redis
    local redis_pw
    redis_pw=$(kubectl get secret redis-secret -n "$namespace" -o jsonpath='{.data.password}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
    if [ -n "$redis_pw" ]; then
      echo "  Redis:         ${redis_pw}"
    else
      echo "  Redis:         <Secret 不存在>"
    fi

    # Nacos
    local nacos_pw
    nacos_pw=$(kubectl get secret nacos-secret -n "$namespace" -o jsonpath='{.data.password}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
    if [ -n "$nacos_pw" ]; then
      echo "  Nacos (nacos): ${nacos_pw}"
    else
      echo "  Nacos:         <Secret 不存在>"
    fi

    # App (datasource)
    local app_db_pw
    app_db_pw=$(kubectl get secret zhiyu-backend-secret -n "$namespace" -o jsonpath='{.data.SPRING_DATASOURCE_PASSWORD}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
    local app_redis_pw
    app_redis_pw=$(kubectl get secret zhiyu-backend-secret -n "$namespace" -o jsonpath='{.data.SPRING_DATA_REDIS_PASSWORD}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
    echo ""
    echo "  应用连接:"
    [ -n "$app_db_pw" ] && echo "    DB 密码:    ${app_db_pw}"
    [ -n "$app_redis_pw" ] && echo "    Redis 密码: ${app_redis_pw}"
  else
    echo "  Namespace 不存在，无法读取 K8s Secrets"
  fi

  # Grafana (monitoring namespace)
  echo ""
  echo "── Monitoring ──"
  local grafana_pw
  grafana_pw=$(kubectl get secret grafana-secret -n monitoring -o jsonpath='{.data.admin-password}' 2>/dev/null | base64 -d 2>/dev/null || echo "")
  if [ -n "$grafana_pw" ]; then
    echo "  Grafana (admin): ${grafana_pw}"
  else
    echo "  Grafana:         <Secret 不存在或 monitoring namespace 未创建>"
  fi

  echo ""
  log_warn "以上密码为敏感信息，请勿截图传播或发送到聊天工具"
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
    cleanup)
      do_check
      do_cleanup
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
  show-secrets)
    do_show_secrets
    ;;
esac
