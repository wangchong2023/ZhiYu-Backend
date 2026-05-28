/**
 * Copyright (c) 2026 ZhiYu. All rights reserved.
 * 文件名: validate-i18n.js
 * 创建时间: 2026-05-28
 * 描述: 前端中英文 i18n 资源文件双向 Key 对齐自动校验工具。
 * 采用 ES Module 标准语法，作为 CI/CD 流水线的一道安全门禁，确保 zh-CN.json 和 en-US.json 的翻译 Key 完全 100% 对齐。
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

// 解决 ES Module 作用域下缺失 __dirname 的问题
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// 定义翻译源文件的物理路径
const LOCALES_DIR = path.join(__dirname, '../src/i18n/locales');
const ZH_FILE = path.join(LOCALES_DIR, 'zh-CN.json');
const EN_FILE = path.join(LOCALES_DIR, 'en-US.json');

/**
 * 递归展开 JSON 对象中的所有叶子节点路径
 * 例如将 { a: { b: 1 } } 展平成 [ "a.b" ]
 * @param {Object} obj 待解析的嵌套 JSON 树对象
 * @param {String} prefix 当前展开层级的 Key 前缀
 * @param {Array} res 用于累加 Key 的数组容器
 * @returns {Array} 展平后的全限定路径 Key 数组
 */
function extractKeys(obj, prefix = '', res = []) {
  if (!obj || typeof obj !== 'object') {
    return res;
  }
  for (const key in obj) {
    if (Object.prototype.hasOwnProperty.call(obj, key)) {
      const fullPath = prefix ? `${prefix}.${key}` : key;
      if (typeof obj[key] === 'object' && obj[key] !== null && !Array.isArray(obj[key])) {
        extractKeys(obj[key], fullPath, res);
      } else {
        res.push(fullPath);
      }
    }
  }
  return res;
}

try {
  console.log('⏳ 开始校验中英文 i18n 翻译资源文件对齐状态...');

  // 1. 读取并解析中英文本地 JSON 包
  const zhContent = JSON.parse(fs.readFileSync(ZH_FILE, 'utf-8'));
  const enContent = JSON.parse(fs.readFileSync(EN_FILE, 'utf-8'));

  // 2. 递归提取出全量翻译 Key 数组
  const zhKeys = extractKeys(zhContent);
  const enKeys = extractKeys(enContent);

  const zhSet = new Set(zhKeys);
  const enSet = new Set(enKeys);

  // 3. 计算双向对称差集
  const missingInEn = [...zhSet].filter(k => !enSet.has(k));
  const missingInZh = [...enSet].filter(k => !zhSet.has(k));

  let hasError = false;

  // 4. 对英文翻译包中缺失的 Key 做出警告
  if (missingInEn.length > 0) {
    console.error(`\n❌ 【en-US.json】翻译缺失！以下 Key 在中文包中存在，但在英文包中遗漏：`);
    missingInEn.forEach(k => console.error(`   - ${k}`));
    hasError = true;
  }

  // 5. 对中文翻译包中缺失的 Key 做出警告
  if (missingInZh.length > 0) {
    console.error(`\n❌ 【zh-CN.json】翻译缺失！以下 Key 在英文包中存在，但在中文包中遗漏：`);
    missingInZh.forEach(k => console.error(`   - ${k}`));
    hasError = true;
  }

  // 6. 处理最终审计拦截状态
  if (hasError) {
    console.error('\n🚨 校验失败！请对齐中英文 locales JSON 翻译资源文件的 Key 集合后再行提交。');
    process.exit(1);
  }

  console.log('\n✅ 恭喜！中英文 locales 翻译资源 Key 100% 对齐成功，编译无瑕疵。');
  process.exit(0);

} catch (err) {
  console.error('🚨 读取 i18n 资源文件异常:', err.message);
  process.exit(1);
}
