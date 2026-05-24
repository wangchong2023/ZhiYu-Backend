#!/bin/bash
# 检查所有 ReactEChartsCore 组件是否传递了 echarts prop
# 缺少 echarts prop 会导致 React 树崩溃 → 白屏
# 参见: fix db6ead8

set -euo pipefail

MISSING=$(grep -rn '<ReactEChartsCore' src/ \
  --include='*.tsx' --include='*.ts' \
  | grep -v 'echarts=' || true)

if [ -n "$MISSING" ]; then
  echo "❌ 以下 ReactEChartsCore 缺少 echarts prop（必须传 echarts={echarts}）："
  echo "$MISSING"
  echo ""
  echo "原因：echarts-for-react v3 的 /lib/core 版本需要显式传入 echarts 实例，"
  echo "缺失时组件卸载会抛出 'Cannot read properties of undefined (reading dispose)'，"
  echo "导致整个 React 树崩溃白屏。"
  exit 1
fi
echo "✓ 所有 ReactEChartsCore 已正确传递 echarts prop"
