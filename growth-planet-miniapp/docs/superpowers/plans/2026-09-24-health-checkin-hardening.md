# Health Check-in Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收口健康打卡家长配置、儿童打卡和日历浏览的异常状态，确保重复点击、失败重试、页面隐藏和迟到响应不会造成错误数据或敏感数据残留。

**Architecture:** 保留现有 `ui.page`、`ui.run`、生命周期失效和后端乐观锁协议，在健康页内部增加请求代次、单项操作锁和可派生的空态/错误态。儿童任务页复用相同的打卡防重复与响应隔离策略，避免健康页和任务页出现不同口径。只改小程序前端和页面测试，不调整接口及数据库。

**Tech Stack:** 微信小程序 JavaScript、WXML、WXSS、Node.js built-in test runner。

## Global Constraints

- 遵循现有项目代码风格和最小变更原则。
- 不新增第三方依赖，不修改后端接口、数据库字段、确认单状态或乐观锁协议。
- 保留现有 `ui.run`、会话失效、权限和错误处理逻辑。
- 所有敏感健康数据在页面隐藏、账号切换或请求失效后清理。
- 参数校验必须覆盖空值、长度、整数范围、非法 itemId 和未来月份。
- 验证命令：`node --test tests/pages.test.js`、`node --test tests/*.test.js`、`node scripts/check.mjs`、`node scripts/check-recipe-parity.mjs`。

### Task 1: 建立健康页状态和请求隔离测试

**Files:**
- Modify: `tests/pages.test.js`
- Reference: `miniprogram/pages/health/index.js`
- Reference: `miniprogram/pages/chore/index.js`

**Interfaces:**
- `health.checkIn(event)` 必须忽略空或未知 `itemId`，同一 item 的请求未完成时不重复发起。
- `health.readChild()` 的迟到响应不得覆盖新的月份、页面隐藏或账号切换后的数据。
- 家长保存和删除失败后保留可重试状态，不能把表单或列表清空。

- [x] **Step 1: 写失败测试**

覆盖以下场景：

```js
test('健康打卡：同一项重复点击只发送一次请求，未知 itemId 不请求', async () => {});
test('健康打卡：打卡失败可重试且不会伪造成功提示', async () => {});
test('健康打卡：月份切换后迟到响应不能覆盖当前月份', async () => {});
test('健康打卡：页面隐藏后迟到响应不回填健康数据', async () => {});
test('健康打卡：家长保存冲突后保留编辑表单并展示错误', async () => {});
});
```

- [x] **Step 2: 运行健康测试确认测试先失败**

运行：

```bash
node --test tests/pages.test.js
```

预期：新增的防重复、迟到响应和失败恢复断言至少有一项失败，现有测试继续通过。

### Task 2: 收口健康页儿童端请求与打卡行为

**Files:**
- Modify: `miniprogram/pages/health/index.js`
- Modify: `miniprogram/pages/chore/index.js`
- Test: `tests/pages.test.js`

**Interfaces:**
- 页面实例维护 `requestToken`、`checkInPending` 和 `readToken`，仅当前页面、当前会话、当前月份的响应允许写入数据。
- `checkIn(event)` 仅接受正整数或非空字符串形式的 itemId，成功后刷新今日数据和日历；失败时由 `ui.run` 展示错误并释放单项锁。
- 达到每日上限、无打卡项、未来月份仍由服务端结果和现有页面状态决定，不发额外越权请求。

- [x] **Step 1: 增加页面级代次和单项操作锁**

初始化空对象状态：

```js
requestToken: 0,
checkInPending: {},
readToken: 0
```

在 `onShow`、`onHide`、儿童刷新和月份切换时递增对应代次；写回前校验代次、角色和当前月份。

- [x] **Step 2: 为儿童打卡增加参数校验、重复点击保护和成功刷新**

实现要求：

```js
const itemId = text(e && e.currentTarget && e.currentTarget.dataset && e.currentTarget.dataset.id);
if (!itemId || !/^\d+$/.test(itemId)) return;
if (this.data.checkInPending[itemId]) return;
```

请求开始前复制锁对象并设置当前 item 为 `true`，在 `finally` 中清除；成功后只在响应仍属于当前页面时刷新并设置提示。刷新失败不能覆盖原来的打卡成功状态，错误仍交给 `ui.run`。

- [x] **Step 3: 让儿童任务页使用相同的重复点击保护**

在 `chore/index.js` 增加同名 `checkInPending`，校验 itemId、成功后刷新 `readChild`，并在任何异常路径释放锁。保持任务页现有健康数据和导航结构不变。

- [x] **Step 4: 运行相关测试并修正回归**

运行：

```bash
node --test tests/pages.test.js
```

预期：全部通过，并新增重复点击、非法 itemId、失败重试覆盖。

### Task 3: 收口家长端配置表单和删除状态

**Files:**
- Modify: `miniprogram/pages/health/index.js`
- Modify: `miniprogram/pages/health/index.wxml`
- Modify: `miniprogram/pages/health/index.wxss`
- Test: `tests/pages.test.js`

**Interfaces:**
- 保存前校验 itemId、名称、图标、单位、每日上限和排序字段；编辑必须携带当前 `expectedVersion`。
- 保存或删除失败时保留用户输入、当前编辑模式和列表，不清理表单；成功后才关闭表单并刷新。
- 删除仍必须二次确认，重复操作由页面级 `busy` 防止。

- [x] **Step 1: 补齐家长端边界测试**

覆盖空名称、空白名称、超长名称、非法数字、超过 9999、编辑缺失 itemId、保存失败和版本冲突：

```js
test('健康打卡：家长表单拒绝空白和非法边界值', async () => {});
test('健康打卡：家长保存失败保留表单内容和编辑模式', async () => {});
test('健康打卡：家长删除失败保留列表', async () => {});
```

- [x] **Step 2: 实现最小校验和失败恢复**

保存前验证编辑 id 和版本；新增时不发送空字段；`readManaged` 对非数组响应按空列表处理并让错误继续冒泡；成功响应后再重置表单。

- [x] **Step 3: 调整小屏布局**

保持 320px 宽度下输入框、按钮和长名称可换行：

```css
.form-row { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); }
.form-row input { min-width: 0; }
.form-actions button { min-width: 0; }
@media (max-width: 360px) {
  .form-panel { padding: 14px; }
  .form-row { grid-template-columns: 1fr; }
}
```

不改变全局组件样式，不引入横向滚动。

- [x] **Step 4: 运行页面测试和静态检查**

运行：

```bash
node --test tests/pages.test.js
node scripts/check.mjs
node scripts/check-recipe-parity.mjs
```

### Task 4: 完成全量验证并记录原生工具限制

**Files:**
- Modify: `docs/superpowers/plans/2026-09-24-health-checkin-hardening.md`

- [x] **Step 1: 运行全量 Node 测试**

```bash
node --test tests/*.test.js
```

- [x] **Step 2: 运行可用的静态检查**

```bash
node scripts/check.mjs
node scripts/check-recipe-parity.mjs
```

- [x] **Step 3: 尝试原生编译检查并记录结果**

结果：原生检查已尝试，但环境缺少 `/Applications/wechatwebdevtools.app/Contents/Resources/package.nw/node_modules/wcc-exec/wcc`，无法完成 WXML 编译。

```bash
node scripts/check.mjs --native
```

若环境仍缺少微信开发者工具 `wcc`，记录为环境阻塞，不修改业务代码绕过。

- [x] **Step 4: 更新本计划执行状态**

将已完成任务勾选，并在最终变更说明中列出改动文件、风险和未完成的真机复核项。
