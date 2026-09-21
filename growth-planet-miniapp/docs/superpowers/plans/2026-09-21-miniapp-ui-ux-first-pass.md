# Miniapp UI/UX First Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Execute this plan task-by-task in the current session. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 优先修复小程序一级导航过载、关键任务流层级不清、长表单操作成本高和钱包信息密度过高的问题。

**Architecture:** 保留现有原生微信小程序页面、接口和数据模型，通过收敛导航配置、增加纯展示状态、重组 WXML 分区和复用全局设计 token 完成调整。业务写操作、幂等键、版本控制和异常处理保持不变。

**Tech Stack:** 原生微信小程序 JavaScript、WXML、WXSS，Node.js `node:test`。

## Global Constraints

- 遵循现有项目代码风格和最小变更原则。
- 不新增第三方依赖，不修改后端接口或数据库。
- 不执行 Git 操作。
- 保留现有参数校验、版本控制、幂等重试和危险操作确认。
- 所有页面兼容儿童与家长角色，并适配安全区和小屏设备。

---

### Task 1: 收敛一级导航

**Files:**
- Modify: `miniprogram/components/app-nav/index.js`
- Modify: `miniprogram/pages/home/index.wxml`
- Modify: `miniprogram/pages/home/index.wxss`
- Modify: `miniprogram/pages/chore/index.js`
- Modify: `miniprogram/pages/medal/index.wxml`
- Modify: `miniprogram/pages/medal/index.js`
- Modify: `miniprogram/pages/schedule/index.wxml`
- Modify: `miniprogram/pages/schedule/index.js`
- Test: `tests/pages.test.js`

**Interfaces:**
- Consumes: `app-nav` 的 `role`、`active` 属性和 `change` 事件。
- Produces: 儿童与家长各 4 个稳定一级入口；勋章与日程保留为二级功能入口。

- [x] 将儿童导航调整为 `meal/chore/growth/me`，家长导航调整为 `home/approvals/wallet/me`。
- [x] 在首页和成长模块增加勋章入口，保证移出一级导航后仍可访问。
- [x] 将日程页面改为二级页面，不再展示底部导航。
- [x] 清理页面中失效的 `learn/tasks/medal` 一级状态映射和错误路由分支。
- [x] 新增测试校验首页、家务页的导航行为。

### Task 2: 重组家庭绑定状态流

**Files:**
- Modify: `miniprogram/pages/family/index.js`
- Modify: `miniprogram/pages/family/index.wxml`
- Modify: `miniprogram/pages/family/index.wxss`
- Test: `tests/pages.test.js`

**Interfaces:**
- Consumes: `selected.bindStatus`、`consent.currentStatus`。
- Produces: `bindingSteps`、`currentBindingStep` 纯展示状态，不改变接口请求。

- [x] 根据申请、同意、绑定、档案状态计算四步进度。
- [x] 将成员列表与当前申请详情分离，选中后只突出当前待办。
- [x] 对已完成步骤、当前步骤和未开始步骤使用一致状态样式。
- [x] 保留年龄校验、同意记录、拒绝和确认绑定逻辑。
- [x] 新增测试覆盖待同意与已绑定的步骤计算。

### Task 3: 降低审批决策负担

**Files:**
- Modify: `miniprogram/pages/confirmation/index.js`
- Modify: `miniprogram/pages/confirmation/index.wxml`
- Modify: `miniprogram/pages/confirmation/index.wxss`
- Test: `tests/pages.test.js`

**Interfaces:**
- Consumes: 现有 `approve/reject/editSuggestion/modify` 方法。
- Produces: `showAdjustment` 展开状态和固定审批操作区。

- [x] 默认只显示“同意并记账”和“需要调整”两个决策。
- [x] 将说明、直接退回和替代餐食放入可展开的调整面板。
- [x] 超额预览继续要求显式二次确认。
- [x] 固定底部操作区适配安全区，并避免遮挡详情内容。
- [x] 新增测试验证调整面板状态不会改变审批请求。

### Task 4: 优化菜品长表单

**Files:**
- Modify: `miniprogram/pages/dish-manage/index.js`
- Modify: `miniprogram/pages/dish-manage/index.wxml`
- Modify: `miniprogram/pages/dish-manage/index.wxss`
- Test: `tests/pages.test.js`

**Interfaces:**
- Consumes: `config.allergens` 过敏原编码和现有表单字段。
- Produces: 本地化 `allergenOptions.label`、三段表单布局和固定操作栏。

- [x] 添加过敏原中文映射，未知编码保留原值。
- [x] 将表单分为基础信息、营养与口味、安全信息。
- [x] 将“虚拟价格（元）”统一为“所需虚拟单位”。
- [x] 保留 HTTPS 图片地址兼容入口，同时增加图片预览与清除操作。
- [x] 固定保存操作栏，并保证键盘与安全区下内容可滚动。
- [x] 新增测试覆盖过敏原本地化和表单保存 payload 不变。

### Task 5: 拆分钱包视图并统一页面样式

**Files:**
- Modify: `miniprogram/pages/wallet/index.js`
- Modify: `miniprogram/pages/wallet/index.wxml`
- Modify: `miniprogram/pages/wallet/index.wxss`
- Modify: `miniprogram/pages/chore/index.wxml`
- Modify: `miniprogram/pages/chore/index.wxss`
- Modify: `miniprogram/pages/medal/index.wxss`
- Modify: `miniprogram/pages/schedule/index.wxss`
- Test: `tests/pages.test.js`

**Interfaces:**
- Consumes: 钱包现有 `overview/records/stats/rule` 数据。
- Produces: `view` 为 `overview/logs/rules` 的一级视图状态，统计图保留在概览视图。

- [x] 钱包拆为“概览、流水、规则”三个视图，首次进入只展示概览。
- [x] 仅在进入流水或概览统计区时加载对应数据，保留查询条件。
- [x] 使用儿童昵称作为主显示，ID 仅作为缺省值。
- [x] 家务、勋章、日程改用全局 token、统一标题和反馈组件。
- [x] 执行全部 Node 测试和静态检查，并在微信开发者工具回归关键页面。
