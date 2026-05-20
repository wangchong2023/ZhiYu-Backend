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

# 创建 Nacos 数据库并初始化表结构（Nacos 不会自动建表）
create_nacos_db() {
  local nacos_db="nacos"
  echo "检查 Nacos 数据库: $nacos_db"

  kubectl exec -n "${K8S_NAMESPACE}" statefulset/mysql -- \
    mysql -u root -p"${MYSQL_ROOT_PASSWORD}" \
    -e "CREATE DATABASE IF NOT EXISTS \`${nacos_db}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" \
    2>/dev/null || { echo "  (跳过 — Nacos 使用内置 Derby)"; return; }

  echo "  Nacos 数据库已就绪: $nacos_db"

  # 执行 Nacos MySQL schema（12 张表），从 Nacos 2.4.0 镜像提取
  local schema_file="${SCRIPT_DIR}/nacos-mysql-schema.sql"
  if [ -f "$schema_file" ]; then
    echo "  初始化 Nacos 表结构..."
    kubectl exec -i -n "${K8S_NAMESPACE}" statefulset/mysql -- \
      mysql -u root -p"${MYSQL_ROOT_PASSWORD}" "$nacos_db" \
      < "$schema_file" 2>/dev/null \
      && echo "  Nacos 表结构已创建（12 张表）" \
      || echo "  (跳过 — 表可能已存在或 schema 执行失败)"

    # ── 资深架构师级安全加固 ─────────────────────────────────────
    # 原理说明: Nacos v2.4.0 在开启鉴权后，若 users 表里的超级管理员 nacos 
    # 的密码仍为默认哈希（即明文 nacos），在内存加载时会出于安全机制将此账号彻底锁死或拒绝响应。
    # 这里检测到由本地高能算出来的自定义强密码 BCrypt 哈希环境变量后，
    # 立即强行执行 UPDATE 进行预先覆盖。这保证了 Nacos StatefulSet 启动时
    # 载入数据库内的数据直接就是安全的强密码哈希！
    # ────────────────────────────────────────────────────────────
    if [ -n "${NACOS_PASSWORD_HASH:-}" ]; then
      echo "  [安全加固] 检测到自定义强密码 BCrypt 哈希，正在覆写 Nacos 默认超级管理员密码..."
      kubectl exec -i -n "${K8S_NAMESPACE}" statefulset/mysql -- \
        mysql -u root -p"${MYSQL_ROOT_PASSWORD}" "$nacos_db" \
        -e "UPDATE users SET password = '${NACOS_PASSWORD_HASH}' WHERE username = 'nacos';" 2>/dev/null \
        && echo "  ✓ Nacos 默认超级管理员密码覆写成功！" \
        || echo "  ⚠️ 覆写 Nacos 默认超级管理员密码失败，可能表结构异常"
    else
      echo "  [警告] 未检测到 NACOS_PASSWORD_HASH 环境变量，将维持默认密码哈希，可能会触发 Nacos 鉴权锁死安全机制！"
    fi
  else
    echo "  (警告: nacos-mysql-schema.sql 不存在，跳过表创建)"
  fi
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
echo "迁移文件位置: backend/zhiyu-server/src/main/resources/db/migration/"
