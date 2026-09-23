#!/usr/bin/env node
/**
 * 三端配方规则一致性核对（菜品食材与做法，v011）。
 *
 * 为什么需要它：同一条配方规则被三端各自实现了一遍 ——
 *   后端      DishRecipeService      ← 唯一真源，服务端是最终裁决方
 *   运营后台  DishesView.vue         ← 保存前的本地拦截（提前告知，不必填完才吃 E-400）
 *   小程序    recipes.js / dish-manage ← 同上，家长在 C 端录家庭菜品
 * 任何一端单独调上限，都会造成「前端放行、后端拒绝」或「前端拦得比后端严」；
 * 而各端测试只验证自己的行为，对这种漂移完全无感（越界用例在两端各自都还是绿的）。
 * 本脚本把三方钉在一起，是当前唯一能拦住该漂移的机制。
 *
 * 提取策略：按各端既有写法做精确探针，探针失配（例如某端重构改了写法）
 * 直接失败并提示更新脚本 —— 刻意的 fail loud。静默跳过比对比不比对更危险：
 * 那种「通过」是假的。
 *
 * 只做数值与枚举比对，不做业务逻辑判断。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const SOURCES = {
  后端: 'growth-planet/sk-growthplanet-core/src/main/java/cn/studykid/growthplanet/service/DishRecipeService.java',
  后台: 'growth-planet-admin/src/views/DishesView.vue',
  小程序: 'growth-planet-miniapp/miniprogram/services/recipes.js',
  小程序页面: 'growth-planet-miniapp/miniprogram/pages/dish-manage/index.js'
};
const source = Object.fromEntries(Object.entries(SOURCES).map(([name, relative]) => {
  const file = path.join(root, relative);
  if (!fs.existsSync(file)) throw new Error(`缺少待核对文件（路径可能已调整）：${relative}`);
  return [name, fs.readFileSync(file, 'utf8')];
}));

const NUM = '(\\d+)';

/** 按正则取第一个捕获组；取不到返回 null，由比对环节报错而非静默跳过。 */
const pick = (where, pattern) => {
  const match = source[where].match(new RegExp(pattern));
  return match ? match[1] : null;
};

/**
 * 每条规则给出三方各自的声明值。字段名即端名。
 * 正则以各端现有代码写法为准 —— 重构后需同步更新（失配会失败，不会悄悄放过）。
 */
const RULES = [
  {
    label: '食材条数上限',
    values: {
      后端: pick('后端', `MAX_INGREDIENTS\\s*=\\s*${NUM}`),
      后台: pick('后台', `MAX_INGREDIENTS\\s*=\\s*${NUM}`),
      小程序: pick('小程序', `MAX_INGREDIENTS\\s*=\\s*${NUM}`)
    }
  },
  {
    label: '食材名称长度上限',
    values: {
      后端: pick('后端', `MAX_INGREDIENT_NAME\\s*=\\s*${NUM}`),
      后台: pick('后台', `name\\.length\\s*>\\s*${NUM}`),
      小程序页面: pick('小程序页面', `item\\.name\\.length\\s*>\\s*${NUM}`)
    }
  },
  {
    label: '食材用量长度上限',
    values: {
      后端: pick('后端', `MAX_INGREDIENT_AMOUNT\\s*=\\s*${NUM}`),
      后台: pick('后台', `amount\\s*\\|\\|\\s*''\\)\\.trim\\(\\)\\.length\\s*>\\s*${NUM}`),
      小程序页面: pick('小程序页面', `item\\.amount\\.length\\s*>\\s*${NUM}`)
    }
  },
  {
    label: '做法步骤数上限',
    values: {
      后端: pick('后端', `MAX_STEPS\\s*=\\s*${NUM}`),
      后台: pick('后台', `MAX_STEPS\\s*=\\s*${NUM}`),
      小程序: pick('小程序', `MAX_STEPS\\s*=\\s*${NUM}`)
    }
  },
  {
    label: '单步做法长度上限',
    values: {
      后端: pick('后端', `MAX_STEP_LENGTH\\s*=\\s*${NUM}`),
      后台: pick('后台', `MAX_STEP_LENGTH\\s*=\\s*${NUM}`),
      小程序页面: pick('小程序页面', `step\\.length\\s*>\\s*${NUM}`)
    }
  },
  {
    label: '小贴士长度上限',
    values: {
      后端: pick('后端', `MAX_TIPS\\s*=\\s*${NUM}`),
      后台: pick('后台', `MAX_TIPS\\s*=\\s*${NUM}`),
      小程序页面: pick('小程序页面', `cookTips\\.length\\s*>\\s*${NUM}`)
    }
  },
  {
    label: '烹饪时长下限',
    values: {
      后端: pick('后端', `MIN_COOK_MINUTES\\s*=\\s*${NUM}`),
      后台: pick('后台', `form\\.cookMinutes"\\s+:min="${NUM}"`),
      小程序页面: pick('小程序页面', `cookMinutes\\s*<\\s*${NUM}`)
    }
  },
  {
    label: '烹饪时长上限',
    values: {
      后端: pick('后端', `MAX_COOK_MINUTES\\s*=\\s*${NUM}`),
      后台: pick('后台', `MAX_COOK_MINUTES\\s*=\\s*${NUM}`),
      小程序页面: pick('小程序页面', `cookMinutes\\s*>\\s*${NUM}`)
    }
  },
  {
    label: '份量下限',
    values: {
      后端: pick('后端', `MIN_SERVINGS\\s*=\\s*${NUM}`),
      后台: pick('后台', `form\\.servings"\\s+:min="${NUM}"`),
      小程序页面: pick('小程序页面', `servings\\s*<\\s*${NUM}`)
    }
  },
  {
    label: '份量上限',
    values: {
      后端: pick('后端', `MAX_SERVINGS\\s*=\\s*${NUM}`),
      后台: pick('后台', `MAX_SERVINGS\\s*=\\s*${NUM}`),
      小程序页面: pick('小程序页面', `servings\\s*>\\s*${NUM}`)
    }
  }
];

/** 难度枚举：三端都必须是同一组英文码（落库为英文，界面各自映射中文）。 */
const DIFFICULTY_RULES = [
  {
    label: '难度枚举集合',
    values: {
      后端: Array.from(source.后端.match(/DIFFICULTIES\s*=\s*Set\.of\(([^)]*)\)/)?.[1].matchAll(/"(\w+)"/g) || [], m => m[1]),
      后台: Array.from(source.后台.match(/DIFFICULTIES\s*=\s*\[([\s\S]*?)\]/)?.[1].matchAll(/value:\s*'(\w+)'/g) || [], m => m[1]),
      小程序: Array.from(source.小程序.match(/DIFFICULTY_LABELS\s*=\s*\{([^}]*)\}/)?.[1].matchAll(/(\w+):/g) || [], m => m[1])
    }
  }
];

const failures = [];
const rows = [];

const show = value => (value === null ? '（未找到声明）' : String(value));

for (const rule of [...RULES, ...DIFFICULTY_RULES]) {
  const entries = Object.entries(rule.values);
  const missing = entries.filter(([, value]) => value === null || (Array.isArray(value) && value.length === 0));
  const comparable = entries.map(([name, value]) => [name, Array.isArray(value) ? [...value].sort().join('/') : value]);
  const baseline = comparable[0][1];
  const drifted = comparable.filter(([, value]) => value !== baseline);

  rows.push([rule.label, ...comparable.map(([, value]) => value)]);
  if (missing.length) {
    failures.push(`${rule.label}：${missing.map(([name]) => name).join('、')} 未匹配到声明`
      + '（脚本探针已失效，请更新 check-recipe-parity.mjs 的正则）');
  } else if (drifted.length) {
    failures.push(`${rule.label}：${drifted.map(([name, value]) => `${name}=${value}`).join('、')} 与后端 ${baseline} 不一致`);
  }
}

// 后台配方区各输入框的 maxlength 是纯 UX 提示，值必须落在后端已知上限集合内，否则会出现
//「输入框还能打字、逻辑却已判越界」这类自相矛盾的体验。
// 只取配方区的四个控件：菜品名称框的 maxlength="64" 不属于配方规则，不应纳入核对。
const knownLimits = new Set(RULES.flatMap(rule => Object.values(rule.values)).filter(Boolean));
const templateMaxlength = [
  ...source.后台.matchAll(/v-model="it\.(?:name|amount)"\s+maxlength="(\d+)"/g),
  ...source.后台.matchAll(/v-model="form\.cookSteps\[idx\]"[^>]*?maxlength="(\d+)"/g),
  ...source.后台.matchAll(/v-model="form\.cookTips"[^>]*?maxlength="(\d+)"/g)
].map(match => match[1]);
const strayMaxlength = templateMaxlength.filter(value => !knownLimits.has(value));

const allLabels = ['规则', ...Object.keys(SOURCES)];
const widths = allLabels.map((label, index) => Math.max(
  label.length,
  ...rows.map(row => String(row[index] ?? '').length)
));
const format = row => row.map((cell, index) => String(cell ?? '').padEnd(widths[index] + 2)).join('');

console.log('三端配方规则一致性核对（真源：后端 DishRecipeService）');
console.log('-'.repeat(widths.reduce((sum, width) => sum + width + 2, 0)));
console.log(format(allLabels));
for (const row of rows) console.log(format(row));
console.log('-'.repeat(widths.reduce((sum, width) => sum + width + 2, 0)));

if (strayMaxlength.length) {
  failures.push(`后台模板 maxlength 出现后端未定义的值：${[...new Set(strayMaxlength)].join('、')}`);
}

if (failures.length) {
  console.error(`一致性 FAIL：${failures.length} 项`);
  for (const failure of failures) console.error(`  - ${failure}`);
  process.exitCode = 1;
} else {
  console.log(`一致性 PASS：${rows.length} 条规则 x ${allLabels.length - 1} 端全部一致`
    + `（含后台模板 maxlength ${templateMaxlength.length} 处）。`);
}
