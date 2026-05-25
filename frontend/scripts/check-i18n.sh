#!/bin/bash
# 检查 JSX 中是否硬编码了中文字符串（应使用 t('key') i18n 函数）
# 白名单：注释、import 路径、console.log、后端代码中的中文
set -euo pipefail

# 搜索 JSX 中硬编码的中文（排除注释行、console.log、import 语句）
VIOLATIONS=$(grep -rn '["'"'"']\(.*[一-鿿]+.*\)["'"'"']' src/ \
  --include='*.tsx' --include='*.ts' \
  | grep -v '//' \
  | grep -v 'console\.' \
  | grep -v "import " \
  | grep -v 'from ' \
  | grep -v 't(' \
  | grep -v 'i18n' \
  | grep -v '\.test\.' \
  | grep -v 'zh-CN\|en-US' \
  | grep -v 'label\.\|common\.\|error\.\|user\.\|auth\.\|monitor\.\|dashboard\.\|notification\.\|subscription\.\|payment\.\|admin\.\|audit\.\|account\.\|sidebar\.\|login\.\|privacy\.\|session\.' \
  | grep -v node_modules || true)

if [ -n "$VIOLATIONS" ]; then
  echo "❌ 以下 JSX 中硬编码了中文字符串，应使用 t('section.key')："
  echo "$VIOLATIONS"
  echo ""
  echo "修复：使用 const { t } = useTranslation() 然后 t('section.key')"
  exit 1
fi
echo "✓ 无硬编码中文字符串"
