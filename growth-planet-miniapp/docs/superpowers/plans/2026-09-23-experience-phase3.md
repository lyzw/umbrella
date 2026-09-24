# 孩子端点餐语义与学校餐单边界优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收敛孩子端点餐页中“推荐/常吃快捷标记、普通菜品标记、心愿菜单提交、家庭餐单确认”四类行为的语义，并明确学校餐单只能查看和标记、不会生成确认单的边界。

**Architecture:** 保留现有 `/menu/mark-favorite`、`/child/wish-mark`、`/child/wish-menu/submit` 和确认单流程不变，仅在菜单页 JavaScript 中增加面向展示的派生字段，减少 WXML 对状态的重复判断。儿童端将“今天想吃”作为轻量的日常标记，将“心愿菜单”作为需要显式提交的候选清单，将“请家长确认”保留为家庭餐单的正式确认入口；学校餐单在来源切换后立即展示只读边界提示。

**Tech Stack:** 原生微信小程序 JavaScript、WXML、WXSS，Node.js `node:test`，现有 `scripts/check.mjs` 和 `scripts/check-recipe-parity.mjs`。

## Global Constraints

- 遵循现有项目代码风格和最小变更原则，不重写菜单页既有读取、提交、鉴权和生命周期逻辑。
- 不新增第三方依赖，不修改后端接口、数据库字段、接口参数、确认单状态或乐观锁行为。
- `/menu/mark-favorite` 继续只处理“今天想吃”标记；推荐卡、常吃卡和普通菜品行可以复用该接口，但展示文案必须明确它们是同一种轻量标记。
- `/child/wish-mark` 继续只处理心愿候选池标记；只有 `/child/wish-menu/submit` 才表示孩子正式提交心愿菜单。
- `checkout()` 继续只允许 `sourceType === 'FAMILY'`、当天且可提交的家庭餐单进入确认单；学校餐单不能显示或触发确认单入口。
- 心愿菜单必须区分功能未开启、日期超出可编辑窗口、已提交锁定和其他不可编辑原因；不可编辑时不得显示可操作的候选目录、搜索、分页或“加入心愿”按钮。
- 保留现有 `ui.run` 错误处理、401/会话失效抛出规则、安全校验和请求失败后的主流程降级行为。
- 不修改用户已有的走查 HTML、截图目录、工作树中其他无关文件。

## File Map

- `miniprogram/pages/menu/index.js`：生成快捷标记、普通菜品标记、心愿菜单和学校餐单边界的展示字段；保留现有接口调用。
- `miniprogram/pages/menu/index.wxml`：按四类行为展示互不混淆的标题、提示、按钮状态和只读入口。
- `miniprogram/pages/menu/index.wxss`：增加轻量标记、正式提交、只读边界和禁用目录的视觉层级，适配窄屏。
- `tests/pages.test.js`：覆盖展示字段、心愿菜单原因、学校餐单边界和现有提交接口不回归。
- `docs/superpowers/plans/2026-09-23-experience-phase3.md`：记录本批实施步骤和验证结果。

---

### Task 1: 锁定四类操作的展示语义和只读边界

**Files:**
- Modify: `tests/pages.test.js`
- Test: `tests/pages.test.js`

**Interfaces:**
- Consumes: `menu` 页面已有的 `recommend`、`frequent`、`dishes`、`wish`、`wishDishes`、`sourceType` 和现有接口桩。
- Produces: 可断言的 `actionLabel`、`actionHint`、`favoriteAriaLabel`、`wish.stateLabel`、`wish.noticeText`、`wish.showCatalog`、`schoolBoundaryText` 页面状态。

- [x] **Step 1: 增加快捷区和普通菜品行的失败测试**

在 `tests/pages.test.js` 的菜单页测试区域增加以下测试。测试使用现有的 `dish`、`emptyWish`、`event`、`loadPage` 辅助方法，要求推荐卡和常吃卡都说明是“今天想吃”轻量标记，普通菜品行的图标操作也不能继续使用“收藏”语义：

```js
test('菜单页：快捷卡和普通菜品行明确都是今天想吃的轻量标记', async () => {
  const page = loadPage('menu', 'CHILD');
  api.get = async endpoint => {
    if (endpoint === '/child/frequent-dish') {
      return { dishes: [{ type: 'PRESET', id: '99', name: '合成餐食', count: 4 }] };
    }
    if (endpoint === '/child/recommend') {
      return { dishes: [{ type: 'PRESET', id: '99', name: '合成餐食', reasons: ['最近常吃'] }] };
    }
    if (endpoint === '/child/menu-week') return { days: [] };
    if (endpoint === '/child/wish-menu') return { ...emptyWish, menuDate: shanghaiDate() };
    if (endpoint === '/child/wish-catalog') return { items: [], total: 0, page: 1, pageSize: 10 };
    return {
      menuId: '20',
      sourceType: 'FAMILY',
      menuDate: shanghaiDate(),
      mealType: 'LUNCH',
      canSubmit: true,
      dishes: [{ ...dish, isFavorite: false, canSelect: true }]
    };
  };

  await page.read();

  assert.equal(page.data.recommend[0].actionLabel, '记入今天想吃');
  assert.equal(page.data.recommend[0].actionHint, '轻量标记，不会提交确认单');
  assert.equal(page.data.frequent[0].actionLabel, '记入今天想吃');
  assert.equal(page.data.dishes[0].favoriteAriaLabel, '标记今天想吃');

  page.allDishes = [{ ...dish, isFavorite: true, canSelect: true }];
  page.setData({ menu: { menuId: '20', canSubmit: true, menuDate: shanghaiDate(), mealType: 'LUNCH' } });
  page.render();
  assert.equal(page.data.dishes[0].favoriteAriaLabel, '取消今天想吃');
});
```

- [x] **Step 2: 增加心愿菜单原因和目录隐藏的失败测试**

增加以下测试，锁定四种心愿菜单状态。`enabled: false` 代表家长未开启；`locked: true` 代表已经正式提交并锁定；日期超出今天起 7 天窗口代表不可编辑；`canEdit: false` 且没有前三种原因代表服务端暂时不允许编辑。每种状态都必须隐藏目录数据和操作入口：

```js
test('心愿菜单：不可编辑原因分别展示，目录不会继续作为可操作入口', async () => {
  const page = loadPage('menu', 'CHILD');
  const today = shanghaiDate();
  const cases = [
    { name: '未开启', raw: { enabled: false, canEdit: true, locked: false, menuDate: today },
      label: '未开启', notice: /家长尚未开启/ },
    { name: '已提交', raw: { enabled: true, canEdit: false, locked: true, menuDate: today, status: 'SUBMITTED' },
      label: '已提交并锁定', notice: /已提交.*不可修改/ },
    { name: '超窗口', raw: { enabled: true, canEdit: true, locked: false, menuDate: '2099-01-01' },
      label: '日期不可编辑', notice: /今天起 7 天内/ },
    { name: '服务端限制', raw: { enabled: true, canEdit: false, locked: false, menuDate: today },
      label: '暂不可编辑', notice: /暂不可编辑/ }
  ];

  for (const item of cases) {
    page.wishRaw = { ...emptyWish, ...item.raw, items: [wishPool()] };
    page.wishSelectionTouched = true;
    page.setData({ menuDate: item.raw.menuDate, wishSelected: [], wishDishes: [{ key: 'PRESET:99' }] });
    page.renderWish();

    assert.equal(page.data.wish.stateLabel, item.label, item.name);
    assert.match(page.data.wish.noticeText, item.notice, item.name);
    assert.equal(page.data.wish.showCatalog, false, item.name);
    assert.deepEqual(page.data.wishDishes, [], item.name);
  }
});
```

- [x] **Step 3: 增加学校餐单边界的失败测试**

增加以下测试，验证学校餐单只影响展示边界，不改变现有菜单读取和普通“今天想吃”接口：

```js
test('学校餐单：展示只读边界，不产生家庭确认单', async () => {
  const page = loadPage('menu', 'CHILD');
  page.setData({ sourceType: 'SCHOOL' });
  page.renderSourceNotice();

  assert.equal(page.data.schoolBoundaryText, '学校餐单只能查看和标记今天想吃，不会生成家长确认单');

  page.setData({
    count: 1,
    childId: child.childId,
    menu: { menuId: '20', canSubmit: true, menuDate: shanghaiDate(), mealType: 'LUNCH' }
  });
  page.checkout();
  assert.equal(context.takeCart(), null);
});
```

- [x] **Step 4: 运行新增测试确认当前实现失败**

运行：

```bash
node --test tests/pages.test.js
```

预期：新增断言因 `actionLabel`、`favoriteAriaLabel`、`wish.stateLabel`、`wish.showCatalog`、`renderSourceNotice` 和 `schoolBoundaryText` 尚未实现而失败；既有测试失败时先确认是展示字段预期变化，不改变接口请求断言。

### Task 2: 增加菜单页展示派生字段并收敛状态原因

**Files:**
- Modify: `miniprogram/pages/menu/index.js`
- Test: `tests/pages.test.js`

**Interfaces:**
- Consumes: 现有 `decorateRecommend`、`decorateFrequent`、`render`、`renderWish`、`loadWishCatalog`、`wishMark`、`favorite` 和 `sourceType`。
- Produces: `renderSourceNotice()`；快捷项的 `actionLabel`、`actionHint`；普通菜品的 `favoriteAriaLabel`；心愿菜单的 `stateLabel`、`showCatalog`、`noticeText`。

- [x] **Step 1: 为快捷标记生成统一展示字段**

在 `decorateRecommend` 和 `decorateFrequent` 的返回对象中保留现有字段，并追加以下派生字段。`isFavorite` 仍由当天菜单匹配结果决定，不能使用推荐接口或常吃接口自身的历史字段替代：

```js
const actionLabel = isFavorite ? '取消今天想吃' : '记入今天想吃';
return {
  ...item,
  available: Boolean(match && match.selectable),
  isFavorite,
  actionLabel,
  actionHint: '轻量标记，不会提交确认单'
};
```

实际实现时分别在两个装饰函数中使用同一套字段，保留 `available` 的今日餐单校验。不可用菜品的 `actionLabel` 改为 `今日餐单暂无`，`actionHint` 改为 `当前日期没有可标记的菜品`，点击校验和 toast 仍由 `quickFavorite` 负责。

- [x] **Step 2: 在 `render()` 中为普通菜品行生成标记语义**

儿童端 `all` 映射中追加 `favoriteAriaLabel`：

```js
favoriteAriaLabel: dish.isFavorite ? '取消今天想吃' : '标记今天想吃'
```

不改变 `favorite()` 发送的 `favorite` 布尔值、菜单 ID、日期和餐次。家长端不生成该字段也可以，但不能让家长维护页面出现儿童端标记按钮。

- [x] **Step 3: 增加 `renderSourceNotice()` 并初始化学校边界字段**

在页面 `data` 中增加 `schoolBoundaryText: ''`，实现以下方法：

```js
renderSourceNotice() {
  this.setData({
    schoolBoundaryText: this.data.role === 'CHILD' && this.data.sourceType === 'SCHOOL'
      ? '学校餐单只能查看和标记今天想吃，不会生成家长确认单'
      : ''
  });
}
```

在 `onShow`、`read` 成功渲染前、`source()` 修改 `sourceType` 后调用该方法；`renderSourceNotice()` 只负责页面展示字段，不替代 `checkout()` 的服务端和前端校验。`onHide` 时将 `schoolBoundaryText` 清空，避免页面复用时残留学校餐单提示。

- [x] **Step 4: 在 `renderWish()` 中收敛心愿菜单状态**

保留现有 `inWindow`、`canEdit`、已提交默认勾选和安全标记逻辑，增加明确的状态计算：

```js
const canEdit = Boolean(wish.enabled && wish.canEdit && inWindow && !wish.locked);
const stateLabel = !wish.enabled
  ? '未开启'
  : wish.locked
    ? '已提交并锁定'
    : !inWindow
      ? '日期不可编辑'
      : wish.canEdit
        ? (wish.status === 'WITHDRAWN' ? '已撤回，可重新编辑' : '可编辑')
        : '暂不可编辑';
const noticeText = !wish.enabled
  ? '家长尚未开启心愿菜单；你仍可以用“今天想吃”做轻量标记。'
  : wish.locked
    ? '已提交给家长，当前不可修改；如需调整请先撤回。'
    : !inWindow
      ? '该日期不在可编辑范围（今天起 7 天内），只能查看已有记录。'
      : wish.canEdit
        ? '先加入心愿候选，再提交给家长；提交后会进入家长可见清单。'
        : '当前暂不可编辑，请稍后重试。';
```

将 `showCatalog` 定义为 `canEdit`，并在 `setData` 中写入 `wish: { ...wish, stateLabel, showCatalog, noticeText }`。当 `showCatalog` 为 `false` 时同步清空 `wishDishes` 和 `wishTotal`，防止上一次可编辑日期的目录残留；`loadWishCatalog()` 仍保留现有窗口和权限前置校验。

- [x] **Step 5: 让心愿目录操作方法复用同一只读边界**

在 `wishMark()`、`wishCatalogTab()`、`wishSearch()` 和 `wishPageDelta()` 入口均使用 `this.data.wish && this.data.wish.showCatalog` 作为前置条件；不可编辑时直接返回，不发送 `/child/wish-mark` 或 `/child/wish-catalog` 请求。`wishSubmit()` 仍使用 `wish.canEdit`，并保留空选择、数量上限、安全状态和 `expectedVersion` 校验。

- [x] **Step 6: 运行菜单页测试确认逻辑通过**

运行：

```bash
node --test tests/pages.test.js
```

预期：Task 1 新增测试和既有菜单、心愿、家庭确认测试全部通过；`/menu/mark-favorite`、`/child/wish-mark`、`/child/wish-menu/submit` 的请求体与现有断言完全一致。

### Task 3: 重组点餐页文案、层级和不可操作状态

**Files:**
- Modify: `miniprogram/pages/menu/index.wxml`
- Modify: `miniprogram/pages/menu/index.wxss`

**Interfaces:**
- Consumes: `recommend`、`frequent` 的 `actionLabel`/`actionHint`，`dishes` 的 `favoriteAriaLabel`，`wish.stateLabel`/`showCatalog`/`noticeText`，`schoolBoundaryText`。
- Produces: 首屏可区分的轻量标记、心愿提交和家庭确认入口；学校来源切换后前置只读说明；只读心愿状态不显示可操作目录。

- [x] **Step 1: 前置学校餐单边界提示**

在儿童端“家庭餐单/学校餐单”来源 tabs 后立即增加：

```xml
<view wx:if="{{schoolBoundaryText}}" class="notice school-boundary-notice">
  {{schoolBoundaryText}}
</view>
```

删除或替换原来位于菜品筛选区下方的重复学校提示，保证来源切换后用户先看到边界，再看到推荐区和菜品区。家庭餐单不显示该提示。

- [x] **Step 2: 明确推荐卡和常吃卡是轻量标记**

两个快捷区的副标题改为说明“今天想吃”而不是泛化的“想吃”，卡片状态使用派生字段：

```xml
<view class="quick-head">
  <text class="quick-title">今天吃什么？</text>
  <text class="small">轻点记入今天想吃，不会提交确认单</text>
</view>
<scroll-view class="quick-scroll" scroll-x="true" enable-flex="true">
  <view wx:for="{{recommend}}" wx:key="key"
    class="quick-card {{item.available ? '' : 'quick-card-disabled'}} {{item.isFavorite ? 'quick-card-active' : ''}}"
    bindtap="quickFavorite" data-key="{{item.key}}">
    <image wx:if="{{item.imageUrl}}" src="{{item.imageUrl}}" mode="aspectFill" class="quick-image" />
    <view wx:else class="quick-image quick-fallback">🍲</view>
    <view class="quick-name">{{item.name}}</view>
    <view class="quick-meta">{{item.available ? item.actionLabel : '今日餐单暂无'}}</view>
    <view class="quick-hint">{{item.actionHint}}</view>
  </view>
</scroll-view>
```

常吃区标题下的说明使用同样语义。不可用卡保留现有不可点击视觉和 `quickFavorite` 的校验，不显示“加入想吃”这类会让用户误以为可以继续操作的文案。

- [x] **Step 3: 明确普通菜品行的轻量标记**

保留圆形图标操作和 `favorite` 事件，更新无障碍标签为：

```xml
aria-label="{{item.favoriteAriaLabel}}"
```

在图标附近增加短的视觉说明，例如：

```xml
<text class="favorite-caption">{{item.isFavorite ? '今天想吃' : '记入今天想吃'}}</text>
```

不得将其写成“收藏”“加入心愿”或“请家长确认”；数量 stepper 和底部家庭确认按钮继续承担正式点餐流程。

- [x] **Step 4: 明确心愿菜单是候选清单加正式提交**

心愿区标题下将状态从 `{{wish.statusLabel}}` 改为 `{{wish.stateLabel}}`，提示文案使用 `{{wish.noticeText}}`。提交按钮文案调整为“提交给家长”，并在按钮上方保留一句短说明：

```xml
<view class="wish-submit-note">提交后家长才能看到这份心愿清单</view>
<button bindtap="wishSubmit" disabled="{{busy || !wish.canEdit || !wishSelected.length}}">
  提交给家长
</button>
```

撤回按钮继续只在 `wish.locked` 时显示，文案保持“撤回”。提交按钮不与普通“今天想吃”标记混用。

- [x] **Step 5: 完全隐藏只读心愿目录和伪操作入口**

将目录容器条件由 `wx:if="{{wish.canEdit}}"` 改为 `wx:if="{{wish.showCatalog}}"`，并在目录列表按钮上保留 `wishMark` 的 disabled 条件：

```xml
<view wx:if="{{wish.showCatalog}}" class="wish-catalog">
  <view wx:for="{{wishDishes}}" wx:key="key" class="wish-dish">
    <view class="wish-dish-main">
      <text class="wish-dish-name">{{item.name}}</text>
      <text class="small">{{item.typeLabel}} · {{item.categoryName || '未分类'}} · {{item.safetyLabel}}</text>
    </view>
    <button class="compact-action"
      bindtap="wishMark"
      data-type="{{item.type}}"
      data-id="{{item.id}}"
      data-marked="{{item.marked}}"
      disabled="{{busy || !wish.showCatalog || (!item.selectable && !item.marked)}}">
      {{item.actionLabel}}
    </button>
  </view>
</view>
```

在只读状态下仍保留已有候选池和原因提示，但不显示搜索框、来源 tabs、分页和“加入心愿”按钮；未开启、超窗口和已提交锁定三种状态的文案必须分别对应 Task 2。

- [x] **Step 6: 调整学校提示、心愿状态和窄屏样式**

在 `index.wxss` 增加以下样式职责：

```css
.school-boundary-notice { margin-top: 8px; border-left-color: #b45309; background: #fffbeb; }
.quick-hint { margin-top: 2px; color: #687386; font-size: 10px; line-height: 16px; text-align: center; }
.favorite-caption { margin-left: 6px; color: #687386; font-size: 11px; }
.wish-submit-note { margin: 6px 0 8px; color: #687386; font-size: 12px; line-height: 18px; }
.wish-catalog-readonly { color: #687386; font-size: 12px; line-height: 18px; }
```

快捷卡保持固定宽度和最小高度，`quick-hint` 在 320px 视口内允许换行但不能撑大卡片；普通菜品标题、图标和 `favorite-caption` 使用 `min-width: 0`，避免覆盖价格和安全提示；学校提示和心愿提示不能遮挡来源 tabs、筛选器或底部确认面板。

- [x] **Step 7: 运行结构检查并确认模板状态**

运行：

```bash
node scripts/check.mjs
```

预期：输出 `Structure PASS`；检查结果中不应出现旧的重复学校提示，不应出现 `wish.canEdit` 作为目录唯一展示条件，也不应出现将“今天想吃”按钮文案写成“加入心愿”的混用。

### Task 4: 完整验证与计划记录

**Files:**
- Modify: `docs/superpowers/plans/2026-09-23-experience-phase3.md`
- Verify: `tests/pages.test.js`
- Verify: `miniprogram/pages/menu/index.js`
- Verify: `miniprogram/pages/menu/index.wxml`
- Verify: `miniprogram/pages/menu/index.wxss`

**Interfaces:**
- Consumes: Task 1 至 Task 3 的测试、页面逻辑和视觉状态。
- Produces: 可复现的自动化验证记录，以及微信开发者工具/真机复核清单。

- [x] **Step 1: 运行全部 Node 测试**

运行：

```bash
node --test tests/*.test.js
```

预期：所有测试通过；重点确认既有  `/menu/mark-favorite`、心愿菜单乐观锁、撤回、过敏安全校验、学校餐单不可确认和家长心愿菜单测试均未改变。

- [x] **Step 2: 运行结构与菜品一致性检查**

运行：

```bash
node scripts/check.mjs
node scripts/check-recipe-parity.mjs
```

预期：结构检查输出 `Structure PASS`，菜品配方一致性检查输出 `一致性 PASS`。

- [x] **Step 3: 检查文案和状态字段没有残留混用**

运行：

```bash
rg -n "加入想吃|今天已想吃|标记想吃|提交心愿菜单|wish\\.canEdit|学校餐单仅供查看" miniprogram/pages/menu/index.js miniprogram/pages/menu/index.wxml miniprogram/pages/menu/index.wxss
```

预期：旧的模糊文案不再作为可操作入口的主文案；`wish.canEdit` 只保留在提交和逻辑校验中，目录展示使用 `wish.showCatalog`；学校边界只保留前置提示，不出现重复的后置提示。

- [ ] **Step 4: 在可用环境执行原生编译和真机复核**

在安装微信开发者工具的环境执行：

```bash
node scripts/check.mjs --native
```

随后至少复核以下场景：

- 家庭餐单：推荐卡、常吃卡和普通菜品行都显示“今天想吃”轻量标记；底部“请家长确认”仍是唯一正式确认入口。
- 学校餐单：来源 tabs 后立即出现“只能查看和标记，不会生成家长确认单”；页面不出现底部确认面板。
- 心愿菜单未开启：显示“家长尚未开启”，仍可使用普通“今天想吃”标记，隐藏心愿目录和提交入口。
- 心愿菜单已提交：显示“已提交并锁定”，保留撤回入口，隐藏目录和修改入口。
- 日期超出 7 天：显示日期不可编辑原因，隐藏目录、搜索和分页。
- 320px 左右窄屏：快捷卡提示可换行，普通菜品行的标记图标、菜名、安全提示、价格不重叠，学校和心愿提示不遮挡筛选器。

- [x] **Step 5: 更新验证记录并标注环境限制**

验证记录（2026-09-24）：

- `node --test tests/pages.test.js`：通过，88 项。
- `node --test tests/*.test.js`：通过，102 项。
- `node scripts/check.mjs`：`Structure PASS`。
- `node scripts/check-recipe-parity.mjs`：`一致性 PASS`。
- 原生 `wcc/wcsc` 编译与微信开发者工具真机复核：当前环境未安装微信开发者工具，未执行。

将实际执行结果填写回本计划的 checkbox。若当前环境没有 `/Applications/wechatwebdevtools.app`，保留 Node 测试、结构检查和配方一致性结果，并明确记录原生编译与真机渲染仍需在具备工具的环境执行，不以 Node 测试替代真机验证。

## Risk Review

- `/menu/mark-favorite` 的后端语义仍是每日标记；本批仅修改前端展示文案，不会让该操作进入确认单或心愿提交流程。
- 心愿菜单目录在不可编辑状态下由页面派生字段清空并隐藏，可能减少用户查看候选菜谱的入口；这是为了避免不可操作控件被误认为可提交，已有候选池仍保留只读回显。
- `wish.canEdit` 为服务端综合状态，前端只能按已知的 `enabled`、`locked`、日期窗口和 `canEdit` 顺序解释；未知原因统一显示“暂不可编辑”，不会猜测具体业务原因。
- 学校餐单继续允许普通标记请求，家长侧是否展示该标记由既有看板和后端数据决定；本批不改统计、通知或家长处理流程。
- 微信开发者工具和真机渲染不由 Node 测试覆盖，必须额外检查 WXML 条件渲染、按钮禁用状态、窄屏换行和底部 sticky 面板。
