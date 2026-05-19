#!/bin/bash
# ============================================================
# 密钥生命周期管理 — 首次部署自动生成，后续复用
#
# 用法: source deploy/scripts/ensure-secrets.sh <env>
#
# 安全策略（参考 docs/SECURITY.md §2）:
#   - JWT RS256 密钥:   180 天轮换
#   - 数据库密码:       90 天轮换
#   - Redis 密码:       90 天轮换
#   - 所有密钥仅存于 deploy/secrets/<env>/ (gitignored)
# ============================================================
set -euo pipefail

ENV="${1:?用法: source ensure-secrets.sh <env>}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
SECRETS_DIR="${PROJECT_DIR}/deploy/secrets/${ENV}"
PASSWORD_FILE="${SECRETS_DIR}/passwords.env"

mkdir -p "$SECRETS_DIR"
chmod 700 "$SECRETS_DIR"   # 密钥目录，仅 owner 可访问

# ── 密码自动生成（已有则跳过）────────────────────────────
if [ ! -f "$PASSWORD_FILE" ]; then
  echo "=== 首次部署 — 生成密钥 (${ENV}) ===" >&2

  cat > "$PASSWORD_FILE" <<EOF
# 自动生成的密码 — $(date -u +%Y-%m-%dT%H:%M:%SZ)
# 安全策略: docs/SECURITY.md §2
# 轮换方式: 删除此文件后重新部署即可自动生成新密码
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-$(openssl rand -hex 16)}"
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-$(openssl rand -hex 24)}"
export REDIS_PASSWORD="${REDIS_PASSWORD:-$(openssl rand -hex 16)}"
export NACOS_PASSWORD="${NACOS_PASSWORD:-$(openssl rand -hex 12)}"
export GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-$(openssl rand -hex 12)}"
export NACOS_IDENTITY_KEY="${NACOS_IDENTITY_KEY:-serverIdentity}"
export NACOS_IDENTITY_VALUE="${NACOS_IDENTITY_VALUE:-$(openssl rand -hex 16)}"
EOF
  chmod 600 "$PASSWORD_FILE"   # 包含明文密码，仅 owner 可读写

  echo "  密钥已生成: $PASSWORD_FILE" >&2
else
  echo "  密钥已存在: $PASSWORD_FILE" >&2
fi

# ── 加载持久化密码（允许环境变量覆盖）───────────────────
# 先 source 持久化文件，再让已设置的环境变量覆盖
source "$PASSWORD_FILE"

# 环境变量覆盖（CI/CD 场景）
export MYSQL_PASSWORD="${MYSQL_PASSWORD}"
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD}"
export REDIS_PASSWORD="${REDIS_PASSWORD}"
export NACOS_PASSWORD="${NACOS_PASSWORD}"
export GRAFANA_PASSWORD="${GRAFANA_PASSWORD}"
export NACOS_IDENTITY_KEY="${NACOS_IDENTITY_KEY}"
export NACOS_IDENTITY_VALUE="${NACOS_IDENTITY_VALUE}"

# ── JWT 密钥生成 ─────────────────────────────────────────
if [ -n "${JWT_KEY_DIR:-}" ] && [ ! -f "${JWT_KEY_DIR}/jwt-private.pem" ]; then
  echo "=== 生成 JWT RS256 密钥对 ===" >&2
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
    -out "${JWT_KEY_DIR}/jwt-private.pem" 2>/dev/null
  openssl rsa -pubout -in "${JWT_KEY_DIR}/jwt-private.pem" \
    -out "${JWT_KEY_DIR}/jwt-public.pem" 2>/dev/null
  chmod 600 "${JWT_KEY_DIR}/jwt-private.pem"   # 私钥，仅 owner 可读
  chmod 644 "${JWT_KEY_DIR}/jwt-public.pem"    # 公钥，所有人可读
  echo "  JWT 密钥已生成: ${JWT_KEY_DIR}" >&2
fi

echo "  ✓ 密钥就绪" >&2
