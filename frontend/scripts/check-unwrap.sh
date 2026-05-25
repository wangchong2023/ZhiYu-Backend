#!/bin/bash
# 检查是否使用 unwrap(res) 而非裸 res.data?.data
# 违反此规则会导致 API 响应解包不一致
set -euo pipefail

VIOLATIONS=$(grep -rn '\.data\?\.data' src/ \
  --include='*.ts' --include='*.tsx' \
  | grep -v 'unwrap' \
  | grep -v 'node_modules' \
  | grep -v '\.test\.' \
  | grep -v '__mocks__' || true)

if [ -n "$VIOLATIONS" ]; then
  echo "❌ 以下文件使用了裸 res.data?.data 访问，应使用 unwrap(res)："
  echo "$VIOLATIONS"
  echo ""
  echo "修复：import { unwrap } from '@/utils/unwrap'; 然后使用 unwrap(res)"
  exit 1
fi
echo "✓ 所有 API 调用正确使用 unwrap()"
