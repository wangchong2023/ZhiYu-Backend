#!/bin/bash
# 检查是否使用了共享 hooks/components 而非手写样板代码
set -euo pipefail
EXIT=0

echo "── 检查 useAsyncData 使用情况 ──"
# 检查手写的 loading/error/fetchData 模式（应使用 useAsyncData）
MANUAL_ASYNC=$(grep -rn 'useState.*loading.*useState.*error.*useEffect' src/pages/ \
  --include='*.tsx' | grep -v 'useAsyncData\|usePaginatedData' || true)
if [ -n "$MANUAL_ASYNC" ]; then
  echo "⚠  发现手写 loading/error 状态（建议使用 useAsyncData）："
  echo "$MANUAL_ASYNC"
  EXIT=1
fi

echo "── 检查 usePaginatedData 使用情况 ──"
# 检查手写的 page/size/total/handleTableChange 模式
MANUAL_PAGE=$(grep -rn 'handleTableChange\|setPage.*setSize\|const \[page.*const \[size.*const \[total' src/pages/ \
  --include='*.tsx' | grep -v 'usePaginatedData' || true)
if [ -n "$MANUAL_PAGE" ]; then
  echo "⚠  发现手写分页逻辑（建议使用 usePaginatedData）："
  echo "$MANUAL_PAGE" | head -20
  EXIT=1
fi

echo "── 检查 CosmicStatCard 使用情况 ──"
# 检查手写的 cosmic-stat-card div（应使用 CosmicStatCard 组件）
MANUAL_CARD=$(grep -rn 'cosmic-stat-card' src/pages/ \
  --include='*.tsx' | grep -v 'CosmicStatCard' || true)
if [ -n "$MANUAL_CARD" ]; then
  echo "⚠  发现手写 cosmic-stat-card div（建议使用 CosmicStatCard）："
  echo "$MANUAL_CARD"
  EXIT=1
fi

echo "── 检查 label 常量重复定义 ──"
# 检查是否重复定义了 providerLabelMap / statusColor / resultColor
DUP_LABELS=$(grep -rn 'providerLabelMap\|statusColor.*=.*{\|resultColor.*=.*{' src/pages/ \
  --include='*.tsx' | grep -v 'constants/labels' || true)
if [ -n "$DUP_LABELS" ]; then
  echo "⚠  发现重复定义 label 映射（应从 constants/labels 导入）："
  echo "$DUP_LABELS"
  EXIT=1
fi

if [ $EXIT -eq 0 ]; then
  echo "✓ 共享模式检查通过"
else
  echo ""
  echo "以上为建议性警告，当前不阻断构建。"
  echo "参考共享模式：useAsyncData, usePaginatedData, CosmicStatCard, constants/labels"
fi
# 当前仅 warning，不阻断 CI
exit 0
