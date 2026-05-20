#!/bin/bash
# ==============================================================================
# 项目名称: ZhiYu-Backend (智宇后端)
# 脚本名称: gen-jwt-keys.sh
# 脚本功能: JWT RS256 非对称密钥对一键生成辅助工具脚本。
#           负责在 K8s 部署前手动或 CI 场景中生成 2048 位 RSA PEM 格式的私钥与配套公钥，
#           并将其以 Base64 单行编码格式输出，可直接写入 K8s Secret 的 stringData 字段。
#
#           安全等级说明：
#             - RSA 2048 位密钥等效安全强度约为 AES-112 位，满足企业级签名和金融级安全要求。
#             - 私钥文件一旦生成，必须严格保密，禁止提交 Git，务必通过安全通道传递给运维人员。
#             - 实际部署中推荐使用 ensure-secrets.sh 自动管理密钥对生命周期（包含自愈、权限控制等）。
#
# 编 写 人: 资深架构师 & 高级开发工程师 (Antigravity AI)
# 编写时间: 2026-05-20
# 调用方式: ./deploy/scripts/gen-jwt-keys.sh <output_dir>
# 依赖工具: openssl (系统内置或 Homebrew 安装)、base64
# 注意事项: 本脚本为手动操作辅助工具，日常部署由 ensure-secrets.sh 自动处理密钥对生成逻辑
# ==============================================================================
set -euo pipefail

# ── 输出目录参数解析 ──────────────────────────────────────────
# 第一个参数为密钥对输出目录，若未指定则默认输出到 ./deploy/envs/dev
OUTPUT_DIR="${1:-./deploy/envs/dev}"
mkdir -p "$OUTPUT_DIR"

echo "=== 生成 JWT RS256 非对称密钥对 ==="
echo "输出目录: $OUTPUT_DIR"
echo ""

# ── 第一步：生成 RSA 2048 位私钥（PKCS#8 PEM 格式） ────────────────────
# -algorithm RSA: 指定使用 RSA 算法
# -pkeyopt rsa_keygen_bits:2048: 密钥长度 2048 位（生产级最小推荐值）
# 2>/dev/null: 屏蔽 openssl 的警告输出，只保留关键错误
echo "[步骤 1/3] 生成 2048 位 RSA 私钥..."
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "${OUTPUT_DIR}/jwt-private.pem" 2>/dev/null

# 强制锁定私钥文件权限为 600（仅 owner 可读写，防止组/其他用户越权访问）
chmod 600 "${OUTPUT_DIR}/jwt-private.pem"
echo "  ✓ 私钥已生成并锁定 600 权限: ${OUTPUT_DIR}/jwt-private.pem"

# ── 第二步：从私钥中提取配套公钥 ──────────────────────────────
# -pubout: 输出 PEM 编码的公钥（PKCS#8 SubjectPublicKeyInfo 格式）
# 公钥可分发给微服务和 API 网关用于 JWT Token 验签，无需保密
echo "[步骤 2/3] 从私钥导出配套 RSA 公钥..."
openssl rsa -pubout -in "${OUTPUT_DIR}/jwt-private.pem" \
  -out "${OUTPUT_DIR}/jwt-public.pem" 2>/dev/null

chmod 644 "${OUTPUT_DIR}/jwt-public.pem"
echo "  ✓ 公钥已导出: ${OUTPUT_DIR}/jwt-public.pem"

# ── 第三步：Base64 单行编码，输出 K8s Secret 可用的键值对 ──────────────
# K8s Secret 的 stringData 字段不支持多行文本，必须将 PEM 文件内容
# Base64 编码后去除所有换行符(\n)，压缩成单行字符串进行注入。
# 微服务内 JwtUtils 将在运行时对该 Base64 字符串进行解码还原并加载密钥对象。
echo "[步骤 3/3] 生成 K8s Secret 可用的 Base64 单行编码值..."

# ── 生成结果汇总 ──────────────────────────────────────────────
echo ""
echo "============================================================"
echo " JWT RS256 密钥对生成完成"
echo "============================================================"
echo " 私钥文件: ${OUTPUT_DIR}/jwt-private.pem  (权限 600)"
echo " 公钥文件: ${OUTPUT_DIR}/jwt-public.pem   (权限 644)"
echo ""
echo " 以下为 K8s Secret 可直接使用的 Base64 编码（请妥善保管）:"
echo ""
echo "  JWT_PRIVATE_KEY: $(base64 < "${OUTPUT_DIR}/jwt-private.pem" | tr -d '\n')"
echo "  JWT_PUBLIC_KEY:  $(base64 < "${OUTPUT_DIR}/jwt-public.pem" | tr -d '\n')"
echo ""
echo " 示例 kubectl 命令（手动注入 Secret）:"
echo "  kubectl -n <namespace> create secret generic zhiyu-backend-secret \\"
echo "    --from-literal=JWT_PRIVATE_KEY=\"\$(base64 < ${OUTPUT_DIR}/jwt-private.pem | tr -d '\\n')\" \\"
echo "    --from-literal=JWT_PUBLIC_KEY=\"\$(base64 < ${OUTPUT_DIR}/jwt-public.pem | tr -d '\\n')\""
echo "============================================================"
echo ""
echo "⚠️  安全提醒：私钥文件 jwt-private.pem 为最高机密，请勿提交至 Git 仓库！"
