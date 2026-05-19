#!/bin/bash
# ============================================================
# MySQL 数据库初始化脚本
# 用法: ./deploy/scripts/init-db.sh <env>
# 操作: 创建数据库（如不存在），等待 Flyway 自动迁移
# ============================================================
set -euo pipefail

ENV="${1:-dev}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_DIR="$(cd "$DEPLOY_DIR/.." && pwd)"

# 加载环境变量
ENV_FILE="${DEPLOY_DIR}/envs/${ENV}.env"
if [ -f "$ENV_FILE" ]; then
  source "$ENV_FILE"
else
  echo "错误: 环境文件不存在: $ENV_FILE"
  exit 1
fi

echo "=== 初始化数据库 ($ENV) ==="

# 创建 Nacos 数据库（如使用内置 Derby 则跳过）
create_nacos_db() {
  local nacos_db="nacos"
  echo "检查 Nacos 数据库: $nacos_db"

  kubectl exec -n "${K8S_NAMESPACE}" statefulset/mysql -- \
    mysql -u root -p"${MYSQL_ROOT_PASSWORD}" \
    -e "CREATE DATABASE IF NOT EXISTS \`${nacos_db}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" \
    2>/dev/null && echo "  Nacos 数据库已就绪: $nacos_db" || echo "  (跳过 — Nacos 使用内置 Derby)"
}

# 创建应用数据库
create_app_db() {
  echo "检查应用数据库: ${MYSQL_DATABASE}"

  kubectl exec -n "${K8S_NAMESPACE}" statefulset/mysql -- \
    mysql -u root -p"${MYSQL_ROOT_PASSWORD}" \
    -e "CREATE DATABASE IF NOT EXISTS \`${MYSQL_DATABASE}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" \
    2>/dev/null

  kubectl exec -n "${K8S_NAMESPACE}" statefulset/mysql -- \
    mysql -u root -p"${MYSQL_ROOT_PASSWORD}" \
    -e "GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO '${MYSQL_USER}'@'%'; FLUSH PRIVILEGES;" \
    2>/dev/null

  echo "  应用数据库已就绪: ${MYSQL_DATABASE}"
}

# 如果 MySQL 在 K8s 内（开发/预发布环境）
if [ -n "${MYSQL_STORAGE:-}" ]; then
  echo "MySQL 在 K8s 集群内，执行建库..."
  create_nacos_db
  create_app_db
else
  echo "MySQL 使用外部 RDS 实例: ${MYSQL_HOST}:${MYSQL_PORT}"
  echo "请手动创建数据库: ${MYSQL_DATABASE}"
  echo "  CREATE DATABASE IF NOT EXISTS \`${MYSQL_DATABASE}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
fi

echo ""
echo "=== 数据库初始化完成 ==="
echo "Flyway 迁移将在应用首次启动时自动执行"
echo "迁移文件位置: zhiyu-server/src/main/resources/db/migration/"
