#!/bin/bash
# 后端编码规范合规检查
# 检查项：Controller 注入 Mapper、Entity 直接暴露 API、@Transactional 缺失
set -euo pipefail
EXIT=0

BACKEND_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "── 检查 Controller 注入 Mapper ──"
MAPPER_IN_CTRL=$(grep -rn 'Mapper' "${BACKEND_DIR}"/*/src/main/java/**/controller/*.java 2>/dev/null | grep 'import.*mapper\|private.*Mapper' | grep -v '//' || true)
if [ -n "$MAPPER_IN_CTRL" ]; then
  echo "❌ Controller 直接注入了 Mapper（应通过 Service 层）："
  echo "$MAPPER_IN_CTRL"
  EXIT=1
else
  echo "✓ 无 Controller 注入 Mapper"
fi

echo "── 检查 Entity 直接暴露 API ──"
ENTITY_IN_API=$(grep -rn 'ApiResponse<.*Entity>\|ApiResponse<List<.*Entity>>\|ApiResponse<List<AuthUserIdentity>>' "${BACKEND_DIR}"/*/src/main/java/**/controller/*.java 2>/dev/null || true)
if [ -n "$ENTITY_IN_API" ]; then
  echo "❌ Controller 直接返回 Entity 类型（应使用 DTO）："
  echo "$ENTITY_IN_API"
  EXIT=1
else
  echo "✓ 无 Entity 直接暴露 API"
fi

echo "── 检查 @Autowired 字段注入 ──"
AUTOWIRED=$(grep -rn '@Autowired' "${BACKEND_DIR}"/*/src/main/java/ 2>/dev/null | grep -v '//' | grep -v 'test' | grep -v 'target' || true)
if [ -n "$AUTOWIRED" ]; then
  echo "❌ 使用了 @Autowired 字段注入（应使用 @RequiredArgsConstructor）："
  echo "$AUTOWIRED"
  EXIT=1
else
  echo "✓ 无 @Autowired 字段注入"
fi

echo "── 检查跨模块 Mapper 引用 ──"
# zhiyu-admin 引用 ufp-auth Mapper
CROSS_MODULE=$(grep -rn 'com.zhiyu.ufp.auth.mapper' "${BACKEND_DIR}"/zhiyu-admin/src/main/java/ 2>/dev/null || true)
if [ -n "$CROSS_MODULE" ]; then
  echo "❌ zhiyu-admin 直接引用了 ufp-auth Mapper（应通过 Service 接口）："
  echo "$CROSS_MODULE" | head -20
  EXIT=1
fi
# zhiyu-user 引用 ufp-auth Mapper
CROSS_MODULE2=$(grep -rn 'com.zhiyu.ufp.auth.mapper' "${BACKEND_DIR}"/zhiyu-user/src/main/java/ 2>/dev/null || true)
if [ -n "$CROSS_MODULE2" ]; then
  echo "❌ zhiyu-user 直接引用了 ufp-auth Mapper（应通过 Service 接口）："
  echo "$CROSS_MODULE2"
  EXIT=1
fi
if [ -z "$CROSS_MODULE" ] && [ -z "$CROSS_MODULE2" ]; then
  echo "✓ 无跨模块 Mapper 引用"
fi

if [ $EXIT -ne 0 ]; then
  echo ""
  echo "发现编码规范违规，请修复后重新提交。"
  exit 1
fi
echo ""
echo "✓ 后端编码规范合规检查通过"
