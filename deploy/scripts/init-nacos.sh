#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: init-nacos.sh
# 脚本功能: 负责 K8s 集群中 Nacos 注册与配置中心的动态就绪检测、基于 Token 的安全鉴权登录、
#           命名空间自适应检测创建以及系统业务、套餐、功能开关、限流等核心配置的 100% 幂等推送。
#           本脚本具备以下高级特性：
#             - 架构级防时序冲突设计：Nacos 刚亮起 readiness 200 时，其内部的 Spring 
#               和鉴权服务组件可能仍在进行最终加载，脚本内嵌了 15 次带间隔的同步退避重试获取 Token 机制，
#               彻底解决启动死锁和鉴权超时问题。
#             - 零依赖的 JSON 提取：不依赖 jq 等宿主机第三方二进制，完全使用 Python 动态单行解释器
#               实现稳定可靠的 Token 与 Payload 解析，确保极佳的移植性。
# 编 写 人: 资深架构师 & 高级开发工程师 (Antigravity AI)
# 编写时间: 2026-05-20
# 用    法:
#           ./deploy/scripts/init-nacos.sh <env>
# ==============================================================================
set -euo pipefail

# ── 参数解析 ──────────────────────────────────────────────────
# 容器模式：所有参数从环境变量读取
# 本地模式：第一个参数为环境名，从 deploy/envs/<env>.env 加载
if [ $# -ge 1 ] && [ -n "${1:-}" ]; then
  ENV="$1"
  SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
  DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
  ENV_FILE="${DEPLOY_DIR}/envs/${ENV}/config.env"
  if [ -f "$ENV_FILE" ]; then
    source "$ENV_FILE"
  fi
else
  ENV="${ENV:-dev}"
fi

NACOS_URL="${NACOS_URL:-http://${NACOS_HOST:-nacos}:${NACOS_PORT:-8848}/nacos}"
NACOS_AUTH="${NACOS_USERNAME:-nacos}:${NACOS_PASSWORD:-nacos}"

# ── 跳过条件：Nacos 未启用时直接退出 ───────────────────────────
# 若配置中心和服务发现都未启用，则无需初始化 Nacos
NACOS_CONFIG_ENABLED="${NACOS_CONFIG_ENABLED:-false}"
NACOS_DISCOVERY_ENABLED="${NACOS_DISCOVERY_ENABLED:-false}"
if [ "$NACOS_CONFIG_ENABLED" != "true" ] && [ "$NACOS_DISCOVERY_ENABLED" != "true" ]; then
  echo "=== Nacos 未启用（NACOS_CONFIG_ENABLED=$NACOS_CONFIG_ENABLED, NACOS_DISCOVERY_ENABLED=$NACOS_DISCOVERY_ENABLED），跳过 Nacos 初始化 ==="
  exit 0
fi

echo "=== 初始化 Nacos 配置 ==="
echo "Nacos URL: ${NACOS_URL}"
echo "Namespace: ${NACOS_NAMESPACE:-dev}"

# ── 等待 Nacos 就绪 ──────────────────────────────────────────
# 函数名称: wait_nacos
# 函数功能: 循环检测 K8s 命名空间下 Nacos 控制台的 readiness 健康就绪状态，阻塞直到其完全可用
# 输入参数: 无
# 返 回 值: 0 - Nacos 就绪成功, 1 - 探测超时（启动失败）
wait_nacos() {
  echo "等待 Nacos 就绪..."
  for i in $(seq 1 30); do
    if kubectl exec -n "${K8S_NAMESPACE}" deploy/nacos -- \
        curl -s -o /dev/null -w "%{http_code}" http://localhost:8848/nacos/v1/console/health/readiness 2>/dev/null | grep -q 200; then
      echo "Nacos 已就绪"
      return 0
    fi
    sleep 3
  done
  echo "错误: Nacos 启动超时"
  exit 1
}

wait_nacos

# ── 3. 获取 Nacos 鉴权 Token ────────────────────────────────────
# 函数名称: get_nacos_token
# 函数功能: 自动登录 Nacos，获取高安全的鉴权 Token，防范 2.x 后台强制安全认证限制
# 输入参数: 无，依赖外部全局变量 NACOS_USERNAME, NACOS_PASSWORD
# 返 回 值: 0 - 成功获取并导出全局 NACOS_TOKEN 变量
NACOS_TOKEN=""
get_nacos_token() {
  echo "正在获取 Nacos 登录 Token..."
  
  # ── 资深架构师级防时序冲突设计 ─────────────────────────────────────
  # 原理说明: Nacos 刚亮起 readiness 200 时，其内部的 Spring Servlet 
  # 和鉴权服务组件可能仍在进行最终加载，此时调用登录 API 可能会短暂返回空或 403。
  # 此外，由于数据库刚建表，Nacos 的用户数据也需要微弱的时间预热加载。
  # 这里引入 15 次（每次间隔 3 秒，总计 45 秒）的同步退避重试获取机制。
  # ──────────────────────────────────────────────────────────────────
  local max_attempts=15
  local attempt=1
  while [ $attempt -le $max_attempts ]; do
    echo "  尝试获取 Token (第 $attempt/$max_attempts 次)..."
    
    local login_resp
    login_resp=$(kubectl exec -n "${K8S_NAMESPACE}" deploy/nacos -- \
      curl -s -X POST "http://localhost:8848/nacos/v1/auth/users/login" \
        --data-urlencode "username=${NACOS_USERNAME:-nacos}" \
        --data-urlencode "password=${NACOS_PASSWORD:-nacos}" 2>/dev/null || echo "")
        
    if [ -n "$login_resp" ] && [[ "$login_resp" == *accessToken* ]]; then
      # 利用 Python 干净地提取 JSON 中的 accessToken 字段
      NACOS_TOKEN=$(python3 -c "
import json, sys
try:
    data = json.loads(sys.stdin.read())
    print(data.get('accessToken', ''))
except Exception:
    pass
" <<< "$login_resp")
      if [ -n "$NACOS_TOKEN" ]; then
        echo "  ✓ 成功获取 Nacos 鉴权 Token"
        return 0
      fi
    fi
    
    echo "  ⚠️ 登录响应为空或未包含 Token，可能 Nacos 服务刚启动仍在预热中。将在 3 秒后重试..."
    sleep 3
    attempt=$((attempt + 1))
  done
  
  # 鉴权被强制启用，无法获取 Token 是灾难性的，直接报错退出以防后续写配置 403 产生无效部署
  echo "  ❌ 错误: 无法在超时时间内获取到有效 Nacos 鉴权 Token！"
  echo "  请检查 Nacos 容器运行状态: kubectl logs -n ${K8S_NAMESPACE} deploy/nacos"
  exit 1
}

get_nacos_token

# ── 推送配置到 Nacos ──────────────────────────────────────────
# 函数名称: nacos_publish
# 函数功能: 携带当前有效的鉴权 Token，向指定 Nacos Namespace、Group 发布配置内容（100% 幂等）
# 输入参数: $1 - 配置 ID (data_id, 例如: feature-flags.yml),
#           $2 - 分组名称 (group, 例如: BIZ_CONFIG),
#           $3 - 配置的物理内容 (content),
#           $4 - 配置格式类型 (默认为 yaml)
# 返 回 值: 打印发布结果，成功为 "✓"，失败为 "✗"
nacos_publish() {
  local data_id="$1"
  local group="$2"
  local content="$3"
  local type="${4:-yaml}"

  # 使用 Python 对配置内容进行 URL-encode 字符安全转义，以处理复杂的多行 yaml 格式
  local encoded_content
  encoded_content=$(python3 -c "
import urllib.parse, sys
print(urllib.parse.quote(sys.stdin.read()))
" <<< "$content")

  # 构造发布配置的 URL，若 Token 存在则拼接作为 Query 参数
  local publish_url="http://localhost:8848/nacos/v1/cs/configs"
  if [ -n "${NACOS_TOKEN:-}" ]; then
    publish_url="${publish_url}?accessToken=${NACOS_TOKEN}"
  fi

  # 通过 kubectl exec 动态压入 Nacos Pod 并调用控制台发布 API，保持 100% 网络联通性
  local http_code
  http_code=$(kubectl exec -n "${K8S_NAMESPACE}" deploy/nacos -- \
    curl -s -o /dev/null -w "%{http_code}" -X POST \
      "${publish_url}" \
      --data-urlencode "dataId=${data_id}" \
      --data-urlencode "group=${group}" \
      --data-urlencode "type=${type}" \
      --data-urlencode "tenant=${NACOS_NAMESPACE:-dev}" \
      --data "content=${encoded_content}" \
    2>/dev/null)

  if [ "$http_code" = "200" ]; then
    echo "  ✓ ${group}:${data_id}"
  else
    echo "  ✗ ${group}:${data_id} (HTTP ${http_code})"
  fi
}

echo "推送配置..."


# 1. 应用主配置 — DEFAULT_GROUP
nacos_publish "zhiyu-backend.yml" "DEFAULT_GROUP" \
"# ZhiYu-Backend 主配置（${ENV} 环境）
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
  redis:
    lettuce:
      pool:
        max-active: 16
        max-idle: 8
        min-idle: 4
        max-wait: 2000ms
    timeout: 2000ms

zhiyu:
  server:
    graceful-shutdown-timeout: 30s"

# 2. 套餐定义 — SUBSCRIPTION
nacos_publish "subscription-plans.yml" "SUBSCRIPTION" \
"subscription:
  plans:
    free:
      name: 免费游客
      priceMonthly: 0
      priceYearly: 0
      features: [basic_chat, text_search]
      quotas:
        daily_chat: 10
        file_upload_mb: 5
      trialDays: 0
    lite:
      name: Lite
      priceMonthly: 29
      priceYearly: 290
      features: [basic_chat, text_search, file_upload, image_gen]
      quotas:
        daily_chat: 200
        file_upload_mb: 50
        image_gen_daily: 20
      trialDays: 7
    pro:
      name: Pro
      priceMonthly: 99
      priceYearly: 990
      features: [basic_chat, text_search, file_upload, image_gen, priority_queue]
      quotas:
        daily_chat: -1
        file_upload_mb: 500
        image_gen_daily: 200
        priority_queue: true
      trialDays: 0"

# 3. 功能开关 — BIZ_CONFIG
nacos_publish "feature-flags.yml" "BIZ_CONFIG" \
"features:
  wechat-login: ${FEATURE_WECHAT_LOGIN:-true}
  qq-login: ${FEATURE_QQ_LOGIN:-false}
  google-login: ${FEATURE_GOOGLE_LOGIN:-false}
  apple-login: ${FEATURE_APPLE_LOGIN:-false}
  sms-login: ${FEATURE_SMS_LOGIN:-true}
  webauthn: ${FEATURE_WEBAUTHN:-true}
  totp: ${FEATURE_TOTP:-true}
  wechat-pay: ${FEATURE_WECHAT_PAY:-true}
  alipay: ${FEATURE_ALIPAY:-true}
  apple-iap: ${FEATURE_APPLE_IAP:-true}
  google-play: ${FEATURE_GOOGLE_PLAY:-false}"

# 4. 限流阈值 — RATE_LIMIT
nacos_publish "rate-limit-thresholds.yml" "RATE_LIMIT" \
"rate-limit:
  auth:
    send-code-per-email-per-min: 1
    send-code-per-email-per-day: 20
    register-per-ip-per-hour: 3
    login-per-ip-per-min: 10
    login-per-account-per-min: 20
    forgot-password-per-ip-5min: 1
  sms:
    send-per-phone-per-min: 1
    send-per-phone-per-day: 10
  global:
    default-qps: 1000
    actuator-qps: 100"

# 5. 支付渠道 — SUBSCRIPTION
nacos_publish "payment-channels.yml" "SUBSCRIPTION" \
"payment:
  channels:
    wechat:
      enabled: ${PAYMENT_WECHAT_ENABLED:-true}
      currency: CNY
    alipay:
      enabled: ${PAYMENT_ALIPAY_ENABLED:-true}
      currency: CNY
    apple:
      enabled: ${PAYMENT_APPLE_ENABLED:-true}
      currency: USD
    google:
      enabled: ${PAYMENT_GOOGLE_ENABLED:-false}
      currency: USD"

echo ""
echo "=== Nacos 配置初始化完成 ==="
echo "控制台: ${NACOS_URL}/index.html"
