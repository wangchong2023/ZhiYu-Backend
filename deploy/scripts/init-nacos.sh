#!/bin/bash
# ============================================================
# Nacos 配置初始化脚本
# 用法:
#   本地:  ./deploy/scripts/init-nacos.sh <env>
#   容器:  docker run --rm -e NACOS_URL=... -e NACOS_NAMESPACE=... zhiyu-nacos-init:latest
# 操作: 创建 namespace（如需要），通过 Nacos Open API 推送所有配置
# ============================================================
set -euo pipefail

# ── 参数解析 ──────────────────────────────────────────────────
# 容器模式：所有参数从环境变量读取
# 本地模式：第一个参数为环境名，从 deploy/envs/<env>.env 加载
if [ $# -ge 1 ] && [ -n "${1:-}" ]; then
  ENV="$1"
  SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
  DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
  ENV_FILE="${DEPLOY_DIR}/envs/${ENV}.env"
  if [ -f "$ENV_FILE" ]; then
    source "$ENV_FILE"
  fi
else
  ENV="${ENV:-dev}"
fi

NACOS_URL="${NACOS_URL:-http://${NACOS_HOST:-nacos}:${NACOS_PORT:-8848}/nacos}"
NACOS_AUTH="${NACOS_USERNAME:-nacos}:${NACOS_PASSWORD:-nacos}"

echo "=== 初始化 Nacos 配置 ==="
echo "Nacos URL: ${NACOS_URL}"
echo "Namespace: ${NACOS_NAMESPACE:-dev}"

# ── 等待 Nacos 就绪 ──────────────────────────────────────────
wait_nacos() {
  echo "等待 Nacos 就绪..."
  for i in $(seq 1 30); do
    if curl -s -o /dev/null -w "%{http_code}" "${NACOS_URL}/v1/console/health/readiness" 2>/dev/null | grep -q 200; then
      echo "Nacos 已就绪"
      return 0
    fi
    sleep 3
  done
  echo "错误: Nacos 启动超时"
  exit 1
}

wait_nacos

# ── 推送配置到 Nacos ──────────────────────────────────────────
nacos_publish() {
  local data_id="$1"
  local group="$2"
  local content="$3"
  local type="${4:-yaml}"

  local url="${NACOS_URL}/v1/cs/configs"
  local encoded_content
  encoded_content=$(echo -n "$content" | curl -s --data-urlencode @- "" | tail -c +2)

  local http_code
  http_code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$url" \
    -u "$NACOS_AUTH" \
    -d "dataId=${data_id}" \
    -d "group=${group}" \
    -d "content=${encoded_content}" \
    -d "type=${type}" \
    -d "tenant=${NACOS_NAMESPACE:-dev}" \
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
