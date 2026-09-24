# 小程序导航与页面寻路优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一小程序一级导航与二级页面返回行为，阻止重复点击造成页面栈膨胀，并确保角色切换后入口状态与返回目标正确。

**Architecture:** 保留原生微信小程序页面和现有 `ui.openTab`/`ui.back` 路由表，在页面工具层增加受控的二级页面导航封装；由 `page-back` 统一处理页面栈返回和无栈角色首页兜底。页面只替换已有直接 `navigateTo` 的入口，不改变业务接口、数据模型或页面内容。

**Tech Stack:** 原生微信小程序 JavaScript、WXML、WXSS、Node.js `node:test`。

## Global Constraints

- 遵循现有项目代码风格和最小变更原则。
- 不新增第三方依赖，不修改后端接口或数据库。
- 保留现有权限校验、会话失效处理、幂等重试和生命周期失效逻辑。
- 一级导航继续使用 `reLaunch`，二级页面使用 `navigateTo` 并防止同一页面连续重复进入。
- 页面隐藏、返回和角色切换后不得由旧导航回调修改新页面状态。

---

### Task 1: 增加受控二级页面导航

**Files:**
- Modify: `miniprogram/utils/page.js`
- Modify: `miniprogram/pages/home/index.js`
- Modify: `miniprogram/pages/menu/index.js`
- Modify: `miniprogram/pages/confirmation/index.js`
- Modify: `miniprogram/pages/family/index.js`
- Modify: `miniprogram/pages/notices/index.js`
- Test: `tests/pages.test.js`

**Interfaces:**
- Consumes: `ui.go` 的 `data-page` 页面入口和页面已有 `role`/`busy` 状态。
- Produces: `ui.openPage(page, url)`，成功、失败或完成后释放本次导航锁；一级 `ui.openTab` 路由保持不变。

- [x] **Step 1: Write the failing tests**

  在 `tests/pages.test.js` 增加以下可观察行为：

  ```js
  test('二级页面连续点击只触发一次 navigateTo，并在导航失败后可重试', async () => {
    const page = loadPage('home', 'PARENT');
    const calls = [];
    global.wx.navigateTo = options => {
      calls.push(options.url);
      options.fail({ errMsg: 'cancelled' });
    };
    page.go({ currentTarget: { dataset: { page: 'family' } } });
    page.go({ currentTarget: { dataset: { page: 'family' } } });
    assert.deepEqual(calls, ['/pages/family/index']);
    page.go({ currentTarget: { dataset: { page: 'family' } } });
    assert.deepEqual(calls, ['/pages/family/index', '/pages/family/index']);
  });

  test('一级入口仍使用根路由，不因二级导航锁影响角色切换', () => {
    const page = loadPage('home', 'PARENT');
    page.changeTab({ detail: { key: 'wallet' } });
    assert.deepEqual(navigation, ['/pages/wallet/index']);
  });
  ```

  测试实现应沿用现有测试桩，不依赖微信真实运行时；页面名称使用当前测试文件已有的 `loadPage` 调用方式。

- [x] **Step 2: Run the focused tests and verify they fail**

  Run:

  ```bash
  node --test tests/pages.test.js
  ```

  Expected: 新增的重复导航断言失败，因为当前 `ui.go` 每次都会直接调用 `wx.navigateTo`，没有导航锁。

- [x] **Step 3: Implement the minimal navigation helper**

  在 `miniprogram/utils/page.js` 增加 `openPage(page, url)`：

  ```js
  function openPage(page, url) {
    if (!page || !url || page.__navigationPending) return null;
    page.__navigationPending = true;
    const options = {
      url,
      complete: () => { page.__navigationPending = false; }
    };
    if (typeof wx.navigateTo !== 'function') {
      page.__navigationPending = false;
      return null;
    }
    return wx.navigateTo(options);
  }
  ```

  将 `go(event)` 改为从 `event.currentTarget.dataset.page` 校验非空后调用 `openPage(this, '/pages/' + page + '/index')`，并将 `openPage` 导出。`complete` 必须释放锁，确保导航失败后可以重试；不修改 `openTab` 和 `back` 的栈策略。

  页面中已有的直接二级跳转改为使用 `ui.openPage`，需要保留查询参数的调用点直接传完整 URL：

  ```js
  ui.openPage(this, '/pages/menu/index?previousConfirmId=' + encodeURIComponent(confirmId));
  ```

  `home.openChildPage`、`home.openMealSource`、`menu` 的确认单/菜品/整周发布、`confirmation.resubmit`、`family.profile`、`notices.open` 等入口只替换导航调用，不改变参数来源和权限判断。

- [x] **Step 4: Run focused and full checks**

  Run:

  ```bash
  node --test tests/pages.test.js
  node --test tests/*.test.js
  node scripts/check.mjs
  node scripts/check-recipe-parity.mjs
  git diff --check
  ```

  Expected: 全部通过，结构检查仍能识别所有页面事件绑定。

- [x] **Step 5: Commit**

  ```bash
  git add miniprogram/utils/page.js miniprogram/pages/home/index.js miniprogram/pages/menu/index.js miniprogram/pages/confirmation/index.js miniprogram/pages/family/index.js miniprogram/pages/notices/index.js tests/pages.test.js
  git commit -m "fix(miniprogram): prevent duplicate secondary navigation"
  ```

### Task 2: 补齐二级页面返回归属

**Files:**
- Modify: `miniprogram/pages/privacy/index.wxml`
- Modify: `miniprogram/pages/privacy/index.json`
- Modify: `miniprogram/pages/privacy/index.js`
- Modify: `tests/pages.test.js`

**Interfaces:**
- Consumes: `page-back` 的 `role`、`fallbackTab`、`disabled` 属性和现有隐私页 `busy` 状态。
- Produces: 家长从隐私页返回“我的”一级入口；无页面栈时仍由 `ui.back` 兜底到家长首页或指定一级入口。

- [x] **Step 1: Write the failing test**

  增加隐私页结构断言和返回目标断言：

  ```js
  test('隐私页属于家长我的模块并提供统一返回入口', () => {
    const template = fs.readFileSync(
      path.join(ROOT, 'miniprogram/pages/privacy/index.wxml'),
      'utf8'
    );
    assert.match(template, /<page-back[^>]+fallback-tab="me"/);
    const page = loadPage('privacy', 'PARENT');
    global.getCurrentPages = () => [];
    page.onShow();
    pageBack.back.call({ data: { role: 'PARENT', fallbackTab: 'me', disabled: false } });
    assert.equal(navigation.at(-1), '/pages/home/index?tab=me');
  });
  ```

  测试中复用当前测试文件已有的 `ui.back` 测试方式；隐私页页面脚本不需要直接实例化 `page-back` 组件，只需用模板结构断言确认组件已挂载，再对同一 `fallbackTab` 参数做行为断言。

- [x] **Step 2: Run the focused test and verify it fails**

  Run:

  ```bash
  node --test tests/pages.test.js
  ```

  Expected: 失败，因为隐私页当前没有 `page-back` 组件。

- [x] **Step 3: Add the existing page-back component**

  在隐私页 WXML 顶部加入：

  ```xml
  <page-back role="{{role}}" fallback-tab="me" disabled="{{busy}}" />
  ```

  在隐私页 JSON 注册已有组件：

  ```json
  {
    "navigationBarTitleText": "同意与数据权利",
    "usingComponents": { "page-back": "/components/page-back/index" }
  }
  ```

  隐私页 JS 不新增导航逻辑，继续使用 `ui.guard(this, 'PARENT')`，使儿童直接打开时回到首页并阻止展示家长数据。

- [x] **Step 4: Run page and native checks**

  Run:

  ```bash
  node --test tests/pages.test.js
  node scripts/check.mjs --native
  git diff --check
  ```

  Expected: 页面模板、组件注册和原生 WXML/WXSS 编译通过。

- [x] **Step 5: Commit**

  ```bash
  git add miniprogram/pages/privacy/index.wxml miniprogram/pages/privacy/index.json tests/pages.test.js
  git commit -m "fix(miniprogram): add privacy page back navigation"
  ```

### Task 3: 更新导航清单和验收记录

**Files:**
- Modify: `docs/superpowers/plans/2026-09-21-miniapp-ui-ux-first-pass.md`
- Modify: `docs/verification.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: Task 1 和 Task 2 的代码与测试结果。
- Produces: 可追溯的一级/二级页面归属、自动化检查数量和本批次剩余实机风险。

- [ ] **Step 1: Document the route ownership table**

  在路线图的 Phase 1 下记录：

  - 儿童一级入口：`meal -> /pages/home/index?tab=meal`、`task -> /pages/chore/index`、`growth -> /pages/home/index?tab=growth`、`me -> /pages/home/index?tab=me`。
  - 家长一级入口：`home -> /pages/home/index?tab=home`、`approvals -> /pages/confirmation/index`、`wallet -> /pages/wallet/index`、`me -> /pages/home/index?tab=me`。
  - 二级返回：餐单、确认单、家庭、菜品、日程、勋章、健康、通知、隐私和档案页均使用 `page-back`；儿童健康/勋章返回成长或任务，儿童餐单/确认单返回点餐，家长页面返回首页或我的。
  - 需要真实微信验证的范围：页面栈、锁屏恢复、角色切换和系统返回键。

- [ ] **Step 2: Update verification counts and limitations**

  根据实际命令结果更新 `docs/verification.md`，不得手填未执行的真机或后端集成结果；记录导航重复点击测试和隐私页组件检查。

- [ ] **Step 3: Run final checks**

  ```bash
  node --test tests/*.test.js
  node scripts/check.mjs
  node scripts/check.mjs --native
  git diff --check
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add docs/superpowers/plans/2026-09-21-miniapp-ui-ux-first-pass.md docs/verification.md README.md
  git commit -m "docs(miniprogram): record navigation ownership"
  ```

## Risks and Follow-up

- `wx.navigateTo` 的 `complete` 回调依赖微信运行时；测试桩需要调用 `complete`，否则导航锁会保持到页面销毁。
- 导航锁只针对同一页面实例，不能替代服务端幂等；写请求仍由原有业务键和版本控制保护。
- `reLaunch`、`navigateBack` 和系统左上角返回键的真实行为仍需在微信开发者工具与真机验证。
- 本批次完成后，下一批进入 Phase 2：语义颜色 token、双角色主题串色、可访问性触控尺寸与系统字体放大验收。
