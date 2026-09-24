# 小程序主题语义与可访问性优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans (recommended). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收敛双角色主题和状态色，消除家长页面残留儿童蓝色主色，并提升关键操作在系统字体放大与触控场景下的可用性。

**Architecture:** 保留现有页面结构和角色判断，仅在全局 `app.wxss`、导航/返回组件及少数高影响页面中增加语义 token 和最小样式覆盖。角色主色继续由 `.page` 与组件自身的 `role` class 决定，业务数据、接口和页面状态不变。

**Tech Stack:** 原生微信小程序 JavaScript、WXML、WXSS、Node.js `node:test`。

## Global Constraints

- 遵循现有项目代码风格和最小变更原则。
- 不新增第三方依赖，不修改接口、数据库字段或后端协议。
- 语义状态色必须同时有颜色和文字/结构表达，不能只依赖颜色。
- 关键可操作控件最小触控尺寸保持 44px；系统字体放大时允许换行，不使用负字间距或固定裁切。
- 保留儿童页面的蓝色主题、家长页面的绿色主题和登录页同时展示两种角色的明确区分。

---

### Task 1: 建立语义 token 并修复角色主题串色

**Files:**
- Modify: `miniprogram/app.wxss`
- Modify: `miniprogram/components/app-nav/index.wxss`
- Modify: `miniprogram/components/page-back/index.wxss`
- Modify: `miniprogram/pages/medal/index.wxss`
- Modify: `miniprogram/pages/wallet/index.js`

**Interfaces:**
- Consumes: 页面根节点的 `.page` / `.parent` 和导航组件的 `role` class。
- Produces: `--primary-shadow`、`--positive-ink`、`--warning-ink`、`--danger-ink` 等语义变量，以及不依赖儿童蓝色硬编码的导航、勋章和钱包图表样式。

- [x] **Step 1: Write the failing style assertions**

在 `tests/pages.test.js` 增加静态断言：

```js
test('主题和状态样式使用语义 token，不把儿童蓝色写死到家长通用控件', () => {
  const appStyles = fs.readFileSync(path.join(root, '../app.wxss'), 'utf8');
  const navStyles = fs.readFileSync(path.join(root, 'components/app-nav/index.wxss'), 'utf8');
  const medalStyles = fs.readFileSync(path.join(root, 'pages/medal/index.wxss'), 'utf8');
  assert.match(appStyles, /--primary-shadow:/);
  assert.match(appStyles, /--danger-ink:/);
  assert.match(navStyles, /\.active\s*\{[^}]*var\(--primary\)/s);
  assert.doesNotMatch(navStyles, /\.active\s*\{[^}]*#2878ff/s);
  assert.doesNotMatch(medalStyles, /rgba\(40,\s*120,\s*255/);
});
```

- [x] **Step 2: Run the focused test and verify it fails**

Run:

```bash
node --test tests/pages.test.js
```

Expected: 新增断言失败，因为导航、勋章和全局状态仍直接使用角色色或固定状态色。

- [x] **Step 3: Implement the minimal token migration**

在 `.page` 中增加语义 token，`.parent` 只覆盖角色相关变量：

```css
--primary-shadow: rgba(40, 120, 255, .2);
--positive-ink: #08764e;
--warning-ink: #8a4b00;
--danger-ink: #9d241b;
--neutral-ink: #475467;
```

将主按钮阴影、错误/安全徽标、导航激活态、返回按钮和勋章激活阴影改为 `var(--primary-shadow)`、`var(--danger-ink)` 等语义变量。导航组件在 `.nav` 与 `.nav.parent` 内提供角色变量，避免组件样式依赖页面外部选择器。

钱包图表颜色根据当前页面角色使用同一主色：

```js
const chartColor = this.data.role === 'PARENT' ? '#15966a' : '#2878ff';
```

该颜色只传给 canvas 图表库，不能通过 WXSS 变量读取；保留图表坐标轴和网格中性的灰色。

- [x] **Step 4: Run focused and structural checks**

```bash
node --test tests/pages.test.js
node scripts/check.mjs
git diff --check
```

Expected: 测试和结构检查通过，页面事件绑定数量不下降。

- [x] **Step 5: Commit**

```bash
git add miniprogram/app.wxss miniprogram/components/app-nav/index.wxss miniprogram/components/page-back/index.wxss miniprogram/pages/medal/index.wxss miniprogram/pages/wallet/index.js tests/pages.test.js
git commit -m "style(miniprogram): centralize semantic theme tokens"
```

### Task 2: 补齐关键控件触控尺寸和放大文本布局

**Files:**
- Modify: `miniprogram/components/page-back/index.wxss`
- Modify: `miniprogram/pages/profile/index.wxss`
- Modify: `miniprogram/pages/dish-manage/index.wxss`
- Modify: `miniprogram/pages/home/index.wxss`
- Modify: `miniprogram/pages/login/index.wxss`
- Test: `tests/pages.test.js`

**Interfaces:**
- Consumes: Task 1 的 token 和现有页面 class。
- Produces: 返回、删除偏好、行内操作和窄屏列表在 44px 触控尺寸及系统字体放大时的稳定样式。

- [x] **Step 1: Write the failing assertions**

```js
test('关键小按钮保留可触控尺寸并允许长文本换行', () => {
  const pageBack = fs.readFileSync(path.join(root, 'components/page-back/index.wxss'), 'utf8');
  const profile = fs.readFileSync(path.join(root, 'pages/profile/index.wxss'), 'utf8');
  const dishManage = fs.readFileSync(path.join(root, 'pages/dish-manage/index.wxss'), 'utf8');
  const home = fs.readFileSync(path.join(root, 'pages/home/index.wxss'), 'utf8');
  assert.match(pageBack, /\.page-back-button\s*\{[^}]*min-height:\s*44px/s);
  assert.match(profile, /button\.preference-remove\s*\{[^}]*min-height:\s*44px/s);
  assert.match(dishManage, /button\.row-action\s*\{[^}]*min-height:\s*44px/s);
  assert.match(home, /\.home-list-title\s*\{[^}]*overflow-wrap:\s*anywhere/s);
});
```

- [x] **Step 2: Run the focused test and verify it fails**

```bash
node --test tests/pages.test.js
```

Expected: 返回按钮、偏好删除按钮和配方行操作按钮的最小高度断言失败。

- [x] **Step 3: Implement the minimal accessibility styles**

将 `.page-back-button`、`button.preference-remove` 和 `button.row-action` 的最小高度提升到 44px，同时保留现有宽度、圆角和紧凑布局；对行内工具使用 `padding` 和 `line-height` 控制文字垂直居中。为首页列表标题、菜单卡片标题和通知记录增加 `overflow-wrap: anywhere`，不使用 `white-space: nowrap` 裁切用户可读主文案。

登录角色卡保留儿童/家长两色的识别差异，但统一 `role-desc` 最小字号为 13px，避免系统字体放大后说明文字过小。

- [x] **Step 4: Run focused and full checks**

```bash
node --test tests/pages.test.js
node --test tests/*.test.js
node scripts/check.mjs
git diff --check
```

- [x] **Step 5: Commit**

```bash
git add miniprogram/components/page-back/index.wxss miniprogram/pages/profile/index.wxss miniprogram/pages/dish-manage/index.wxss miniprogram/pages/home/index.wxss miniprogram/pages/login/index.wxss tests/pages.test.js
git commit -m "fix(miniprogram): improve touch targets and text wrapping"
```

### Task 3: 更新计划和验证记录

**Files:**
- Modify: `docs/superpowers/plans/2026-09-21-miniapp-ui-ux-first-pass.md`
- Modify: `docs/verification.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: Task 1、Task 2 的实际测试结果。
- Produces: Phase 2 完成项、自动化覆盖范围和原生编译/真机限制的可追溯记录。

- [x] **Step 1: Update Phase 2 checklist**

仅勾选已由代码和自动化检查覆盖的语义 token、主题串色、触控尺寸和长文本换行项；系统字体放大、读屏和不同设备宽度继续保留为真机验收项。

- [x] **Step 2: Update verification counts and limitations**

记录实际执行的 Node 测试和结构检查数量，不填写未执行的开发者工具或真机结果；补充 `--native` 当前因缺少 `wcc` 未完成的事实。

- [x] **Step 3: Run final documentation checks**

```bash
node --test tests/*.test.js
node scripts/check.mjs
git diff --check
```

- [x] **Step 4: Commit**

```bash
git add docs/superpowers/plans/2026-09-21-miniapp-ui-ux-first-pass.md docs/verification.md README.md
git commit -m "docs(miniprogram): record theme accessibility checks"
```

## Risks and Follow-up

- CSS 自定义属性在微信开发者工具不同基础库版本中的继承行为仍需原生编译和真机确认。
- canvas 图表颜色不能读取 WXSS token，必须和角色判断保持同步；新增角色时需要同时更新图表映射。
- 提升行内按钮高度可能增加表单纵向长度，需在 320px 和系统字体放大场景复核换行、滚动和底部固定操作栏。
- 本批完成后继续处理页面状态反馈、读屏语义和设计走查中剩余的真实设备问题。
