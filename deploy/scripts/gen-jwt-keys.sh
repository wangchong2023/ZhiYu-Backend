#!/bin/bash
# ============================================================
# JWT RS256 密钥对生成脚本
# 用法: ./deploy/scripts/gen-jwt-keys.sh <output_dir>
# ============================================================
set -euo pipefail

OUTPUT_DIR="${1:-./deploy/secrets/dev}"
mkdir -p "$OUTPUT_DIR"

echo "=== 生成 JWT RS256 密钥对 ==="
echo "输出目录: $OUTPUT_DIR"

# 生成 RSA 2048 位私钥（PKCS#8 PEM）
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "${OUTPUT_DIR}/jwt-private.pem" 2>/dev/null

# 提取公钥
openssl rsa -pubout -in "${OUTPUT_DIR}/jwt-private.pem" \
  -out "${OUTPUT_DIR}/jwt-public.pem" 2>/dev/null

# Base64 编码（去除换行，用于 K8s Secret）
echo "JWT_PRIVATE_KEY=$(base64 < "${OUTPUT_DIR}/jwt-private.pem" | tr -d '\n')"
echo "JWT_PUBLIC_KEY=$(base64 < "${OUTPUT_DIR}/jwt-public.pem" | tr -d '\n')"

echo ""
echo "=== 生成完成 ==="
echo "私钥: ${OUTPUT_DIR}/jwt-private.pem"
echo "公钥: ${OUTPUT_DIR}/jwt-public.pem"
echo ""
echo "将以下内容填入 K8s Secret:"
echo "  JWT_PRIVATE_KEY: $(base64 < "${OUTPUT_DIR}/jwt-private.pem" | tr -d '\n')"
echo "  JWT_PUBLIC_KEY:  $(base64 < "${OUTPUT_DIR}/jwt-public.pem" | tr -d '\n')"
