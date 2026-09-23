# 家长审批页收件箱体验优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 优化家长餐单审批页的待办层级、筛选可理解性和列表信息密度，同时保持现有审批接口、幂等键和乐观锁行为不变。

**Architecture:** 将 `PENDING` 作为家长审批页的主收件箱状态，使用接口返回的 `total` 展示真实待处理数量；已完成、需调整、已撤回仍分别使用后端支持的状态筛选，不伪造“已处理”合并分页。通过页面状态派生显示文案和 WXML/WXSS 分区完成体验优化，详情页的审批操作复用现有方法。

**Tech Stack:** 原生微信小程序 JavaScript、WXML、WXSS，Node.js `node:test`。

## Global Constraints

- 遵循现有项目代码风格和最小变更原则。
- 不新增第三方依赖，不修改后端接口、数据库和状态契约。
- `GET /parent/approvals` 仅使用 `PENDING`、`COMPLETED`、`REJECTED`、`CANCELLED` 四种后端支持状态。
- 待处理数量必须使用接口 `total`，不能用当前页 `items.length` 推断。
- 保留现有 `operations` 幂等重试、审批版本号、额度超限二次确认和 `ui.run` 异常处理。
- 不提交用户提供的走查 HTML、截图目录及工作树中其他无关变更。

---

### Task 1: 锁定审批列表的状态与数量行为

**Files:**
- Modify: `tests/pages.test.js`
- Test: `tests/pages.test.js`

**Interfaces:**
- Consumes: `confirmation` 页面已有 `readList`, `filter`, `child` 方法和 `/parent/approvals` 查询参数。
- Produces: 可验证的 `pendingTotal`、`listHeading`、`listDescription` 页面状态，以及每个筛选项对应的后端状态值。

- [x] **Step 1: 写失败测试**

增加以下测试，验证家长首次读取待处理列表使用 `status: 'PENDING'`，并保留服务端 `total`：

```js
test('家长审批列表突出待处理数量并使用服务端总数', async () => {
  const page = loadPage('confirmation');
  const calls = [];
  api.get = async (endpoint, query) => {
    calls.push({ endpoint, query });
    return { items: [{ confirmId: '21', status: 'PENDING', menuDate: '2026-09-23', totalAmount: '20.00' }], total: 7 };
  };
  page.setData({ role: 'PARENT', childId: child.childId, page: 1, status: 'PENDING', statusIndex: 0 });

  await page.readList();

  assert.deepEqual(calls[0], {
    endpoint: '/parent/approvals',
    query: { childId: child.childId, page: 1, pageSize: 20, status: 'PENDING' }
  });
  assert.equal(page.data.pendingTotal, 7);
  assert.equal(page.data.listHeading, '待我处理');
  assert.match(page.data.listDescription, /7/);
  assert.equal(page.data.records[0].label, '等待家长确认');
});
```

- [x] **Step 2: 写失败测试**

增加筛选测试，确认历史筛选只发送后端支持的单一状态，不出现伪造的合并状态：

```js
test('审批历史筛选使用后端支持的独立状态', async () => {
  const page = loadPage('confirmation');
  const queries = [];
  api.get = async (endpoint, query) => {
    queries.push(query);
    return { items: [], total: 0 };
  };
  page.setData({ role: 'PARENT', childId: child.childId, page: 1, status: 'PENDING', statusIndex: 0 });

  page.filter(event({}, 1));
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(page.data.status, 'COMPLETED');
  assert.equal(page.data.listHeading, '已完成');
  assert.equal(queries.at(-1).status, 'COMPLETED');
  assert.ok(['PENDING', 'COMPLETED', 'REJECTED', 'CANCELLED'].includes(queries.at(-1).status));
});
```

- [x] **Step 3: 运行测试确认当前实现不满足**

运行：

```bash
node --test tests/pages.test.js
```

预期：新增断言因 `pendingTotal`、`listHeading`、`listDescription` 尚未实现而失败。

### Task 2: 增加页面派生状态与稳健分页边界

**Files:**
- Modify: `miniprogram/pages/confirmation/index.js`
- Test: `tests/pages.test.js`

**Interfaces:**
- Consumes: `/parent/approvals` 和 `/menu/confirms` 返回的 `items`、`total`。
- Produces: `pendingTotal`、`listHeading`、`listDescription`、`statusOptions` 页面数据；分页仍按当前状态的 `total` 判断。

- [x] **Step 1: 增加状态配置**

将静态状态数组替换为包含后端值和展示文案的配置，并在 `data` 中保留 picker 所需的 `statuses`：

```js
const approvalFilters = [
  { value: 'PENDING', label: '待我处理' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'REJECTED', label: '需调整' },
  { value: 'CANCELLED', label: '已撤回' }
];
```

儿童端仍展示“待确认”，但请求状态值必须继续为 `PENDING`。

- [x] **Step 2: 在读取列表后更新派生状态**

`readList` 成功后：

```js
const total = Number.isSafeInteger(result.total) && result.total >= 0 ? result.total : 0;
const currentFilter = approvalFilters.find(filter => filter.value === status) || approvalFilters[0];
const listHeading = role === 'PARENT' ? currentFilter.label : statusLabels[status] || '确认记录';
const listDescription = role === 'PARENT'
  ? (status === 'PENDING' ? `还有 ${total} 份餐单待你处理` : `共 ${total} 份${currentFilter.label}餐单`)
  : `共 ${total} 份确认记录`;
this.setData({ records, total, pendingTotal: status === 'PENDING' ? total : this.data.pendingTotal,
  listHeading, listDescription });
```

列表接口返回异常的 `total` 不得导致分页出现负数或错误按钮；总数归一化为非负整数。

- [x] **Step 3: 保持筛选和分页状态一致**

`filter` 通过配置取后端值，重置 `page: 1`；`readList` 成功后如果当前页超出总数，回退到最后有效页并重新读取一次，最多执行一次回退，避免空列表和重复请求循环。读详情、审批和重试流程不改。

- [x] **Step 4: 运行页面测试**

运行：

```bash
node --test tests/pages.test.js
```

预期：Task 1 新增测试通过，既有审批提交、审批重试、建议餐食和轮询测试继续通过。

### Task 3: 重组审批列表视觉层级与控件语义

**Files:**
- Modify: `miniprogram/pages/confirmation/index.wxml`
- Modify: `miniprogram/pages/confirmation/index.wxss`

**Interfaces:**
- Consumes: `listHeading`、`listDescription`、`pendingTotal`、`statuses`、`statusIndex`、`childId`、`records`。
- Produces: 家长页清晰的待办摘要、可识别的 picker、日期/金额/状态分层记录卡；儿童页保持原有确认单能力。

- [x] **Step 1: 添加待办摘要**

在列表筛选器前增加家长专属摘要区，只有 `PENDING` 筛选时突出 `pendingTotal`，数量为 0 时显示真实零值，不用空列表长度替代：

```xml
<view wx:if="{{role === 'PARENT'}}" class="inbox-summary">
  <view>
    <text class="eyebrow">家长审批收件箱</text>
    <text class="inbox-count">{{pendingTotal}}</text>
    <text class="inbox-label">份待我处理</text>
  </view>
  <text class="inbox-note">先处理待办，再查看历史记录</text>
</view>
```

- [x] **Step 2: 明确筛选控件**

为儿童选择器和状态选择器增加字段标题、浅色边框、下拉提示符号和当前列表说明；不改变 picker 的 `range`、`value`、`bindchange`：

```xml
<view class="filter-field" wx:if="{{role === 'PARENT' && children.length}}">
  <text class="filter-label">当前儿童</text>
  <picker range="{{children}}" range-key="childId" value="{{childIndex}}" bindchange="child" disabled="{{busy}}">
    <view class="picker picker-control"><text>儿童 {{childId}}</text><text class="picker-chevron">⌄</text></view>
  </picker>
</view>
<view class="filter-field">
  <text class="filter-label">查看范围</text>
  <picker range="{{statuses}}" value="{{statusIndex}}" bindchange="filter" disabled="{{busy}}">
    <view class="picker picker-control"><text>{{statuses[statusIndex]}}</text><text class="picker-chevron">⌄</text></view>
  </picker>
</view>
<view class="list-heading"><text>{{listHeading}}</text><text class="muted">{{listDescription}}</text></view>
```

- [x] **Step 3: 提高记录卡扫描效率**

保留整卡点击和现有确认单 ID，增加“用餐日期”“金额”“状态”的稳定布局，避免金额和状态在 320px 宽度下互相挤压；分页按钮继续只在总数确实跨页或当前页大于 1 时出现。

- [x] **Step 4: 添加窄屏样式**

在页面样式中增加 `inbox-summary`、`filter-field`、`picker-control`、`list-heading` 和 `record-meta`，使用现有颜色 token；在 `max-width: 340px` 下让摘要数字和说明换行，不能遮挡筛选器与列表。

### Task 4: 完整验证并分批提交

**Files:**
- Modify: `docs/superpowers/plans/2026-09-23-experience-phase2.md`
- Modify: `miniprogram/pages/confirmation/index.js`
- Modify: `miniprogram/pages/confirmation/index.wxml`
- Modify: `miniprogram/pages/confirmation/index.wxss`
- Modify: `tests/pages.test.js`

- [x] **Step 1: 运行全部自动化测试**

```bash
node --test tests/*.test.js
```

结果：页面测试 75 项通过；完整测试在提交前再次执行，必须全部通过。

- [x] **Step 2: 运行静态和菜品一致性检查**

```bash
node scripts/check.mjs
node scripts/check-recipe-parity.mjs
```

结果：`node scripts/check.mjs` 输出 `Structure PASS`；`node scripts/check-recipe-parity.mjs` 输出 `一致性 PASS`。

- [x] **Step 3: 更新计划验证记录**

已将完成的 checkbox 标记为 `[x]`；微信开发者工具未安装时，原生编译和真机渲染仍需在具备工具的环境复核。

- [x] **Step 4: 只提交第二批相关文件**

```bash
git add growth-planet-miniapp/docs/superpowers/plans/2026-09-23-experience-phase2.md growth-planet-miniapp/miniprogram/pages/confirmation/index.js growth-planet-miniapp/miniprogram/pages/confirmation/index.wxml growth-planet-miniapp/miniprogram/pages/confirmation/index.wxss growth-planet-miniapp/tests/pages.test.js
git commit -m "feat: improve parent approval inbox"
```

提交前确认 `git status --short` 中仍只剩用户已有的无关变更，不要提交走查 HTML、截图目录、`.gitignore`、`.env.example` 或 `ai-canvas.miora`。

## Risk Review

- 后端不支持多个历史状态合并查询，因此本批不提供“全部已处理”伪 Tab；四个筛选值始终与后端一一对应。
- `total` 若缺失或格式异常会按 0 展示，避免错误分页；这会掩盖后端响应契约问题，后续应通过接口日志监控发现。
- picker 的中文儿童标识仍以 `childId` 为稳定兜底，未引入未确认的昵称字段契约。
- 微信开发者工具和真机渲染不能由当前 Node 测试替代，尤其要复核 320px 宽度、系统字体放大和固定审批操作栏。
