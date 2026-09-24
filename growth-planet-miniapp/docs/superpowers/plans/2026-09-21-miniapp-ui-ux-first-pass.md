# Miniapp UI/UX First Pass and Follow-up Plan

> **Status:** 首轮 5 项优化已于 2026-09-21 完成并提交；后续规划于 2026-09-21 更新。步骤使用 checkbox（`- [ ]`）跟踪。

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

## First-pass Baseline

- 一级导航、首页功能入口、家务、日程、勋章页面已完成首轮调整。
- 家庭绑定、餐单审批、菜品管理和钱包已完成信息分层与关键操作优化。
- 首页功能列表已统一为图标和文案靠左、箭头靠右，长文案区域可收缩换行。
- 截至首轮提交，`node --test tests/core.test.js tests/pages.test.js` 共 44 项通过。
- 健康打卡页面及其首页入口当前处于独立开发中，不计入上述 44 项完成基线。
- 真机、弱网、读屏和完整业务端到端验收仍未完成，不能以自动化测试通过替代发布验收。

---

## Follow-up Roadmap

### Phase 0: 收口健康打卡功能（P0，进行中）

**Scope:**
- `miniprogram/pages/health/*`
- `miniprogram/pages/home/index.wxml`
- `miniprogram/pages/home/index.wxss`
- `miniprogram/app.json`
- `miniprogram/app.wxss`
- `tests/pages.test.js`

**Goal:** 完成家长配置打卡项、儿童今日打卡、连续天数和月历回顾的完整闭环，并保证敏感健康数据只在有效同意范围内展示和写入。

- [ ] 校验家长新增、编辑、删除打卡项的参数边界、版本冲突和重复操作反馈。
- [ ] 校验儿童重复点击、达到每日上限、空打卡项、跨日和未来月份限制。
- [ ] 补齐加载、空数据、请求失败、无权限和同意失效状态，失败后提供明确恢复操作。
- [ ] 核对健康数据页面隐藏、账号切换和同意撤回后的内存清理与迟到响应隔离。
- [ ] 检查 320 / 375 / 430px 下表单、日历、长名称、长单位和系统字体放大布局。
- [ ] 执行页面测试、静态检查、原生 WXML/WXSS 编译和微信开发者工具回归。
- [ ] 健康功能代码、测试和必要文档独立提交，不与后续视觉整理混合。

**Exit Criteria:**
- 家长和儿童关键路径均可独立完成，异常状态可恢复。
- 写请求保留既有版本控制、幂等和结果未知处理约束。
- 自动化测试、静态检查与开发者工具回归全部通过。

### Phase 1: 收口导航与页面寻路（P1）

**Goal:** 让一级入口行为稳定，二级页面可以清楚返回所属模块，避免重复压栈和角色错位。

- [x] 建立一级页面与二级页面清单，明确每个页面的导航归属和返回目标。
- [x] 复核 `home/chore/wallet` 的底部导航高亮、重复点击和返回栈行为。
- [x] 为餐单审批、家庭绑定、菜品管理、日程、勋章、健康、通知和隐私页统一二级页返回策略。
- [x] 验证儿童和家长切换后不会保留另一角色的 `active` 状态或错误入口。
- [ ] 检查首页新增功能后的列表长度、重复入口和首屏任务优先级，低频功能统一放入“我的”。

**Route ownership recorded in this batch:**

- 儿童一级入口：`meal -> /pages/home/index?tab=meal`、`task -> /pages/chore/index`、`growth -> /pages/home/index?tab=growth`、`me -> /pages/home/index?tab=me`。
- 家长一级入口：`home -> /pages/home/index?tab=home`、`approvals -> /pages/confirmation/index`、`wallet -> /pages/wallet/index`、`me -> /pages/home/index?tab=me`。
- 二级页面由 `page-back` 返回所属一级入口；餐单、确认单、家庭、菜品、日程、勋章、健康、通知、隐私和档案页不再自行定义互相冲突的返回策略。
- 导航锁、页面栈、锁屏恢复、角色切换和系统返回键仍需在微信开发者工具及真机复核。

**Exit Criteria:**
- 任意页面最多一次明确操作即可回到所属一级页面。
- 一级导航切换不重复创建页面栈，返回行为与用户预期一致。
- 儿童和家长看不到无权限入口。

### Phase 2: 统一视觉 token 与可访问性（P1）

**Goal:** 清理残留硬编码状态色，统一双角色主题、可点击语义和文本可读性。

- [ ] 增加并复用 danger、warning、health 等语义 token，优先替换错误态、危险操作和徽标中的硬编码颜色。
- [ ] 复核家长主题下徽标、收藏、空态、通知和页面图标，避免儿童蓝色主题残留。
- [ ] 统一通知已读/未读、成功/警告/错误状态的颜色、图标和文案层级。
- [ ] 统一审批明细中的“单价、数量、小计、虚拟单位”字段表达。
- [ ] 校验正文和小字对比度、禁用态辨识度、44px 触控目标及系统字体放大表现。
- [ ] 减少 emoji 和特殊字形承担关键操作语义；关键按钮使用稳定文字或项目既有图标方案。

**Exit Criteria:**
- 家长与儿童主题不存在无业务含义的串色。
- 关键信息不只依赖颜色表达，所有操作目标尺寸满足触控要求。
- 320px 小屏和系统字体放大后无文字遮挡、按钮挤压或横向滚动。

### Phase 3: 统一页面状态与操作反馈（P1）

**Goal:** 让所有核心页面在加载、成功、失败、空数据和危险操作时使用一致反馈模型。

- [ ] 盘点核心页面的 loading、empty、error、receipt、disabled 和 retry 状态。
- [ ] 区分页面级加载与按钮级提交，避免一次局部操作阻塞整页无关区域。
- [ ] 对删除、拒绝、撤回、覆盖和版本冲突提供结果明确且可恢复的反馈。
- [ ] 统一长表单的保存区、安全区、键盘遮挡处理和离开页面未保存提醒策略。
- [ ] 对昵称缺失、超长名称、超大数字、空数组和后端缺字段增加展示兜底。
- [ ] 为后续排错补充必要日志或断言，日志不得包含儿童敏感健康数据。

**Exit Criteria:**
- 每个核心请求都具备加载、成功、失败和重试路径。
- 页面隐藏或账号切换后，旧请求不会覆盖新状态或恢复敏感内容。
- 危险操作均有二次确认，结果未知时不伪装成成功或失败。

### Phase 4: 设计 QA 与发布前验收（P0）

**Goal:** 用真实微信运行环境验证首轮和后续调整，不以静态检查代替平台验收。

- [ ] 在开发者工具覆盖儿童、家长两种角色及未授权、空数据、错误、长文本状态。
- [ ] 覆盖 320 / 375 / 430px、平板宽度、全面屏安全区和键盘弹起场景。
- [ ] 实机覆盖弱网、断网恢复、重复点击、锁屏恢复、跨日和账号切换。
- [ ] 完成建家庭、同意、绑定、建档、菜单发布、儿童提报、家长审批、钱包和健康打卡端到端流程。
- [ ] 核对正式 AppID、HTTPS 合法域名、图片域名、隐私配置、订阅消息和敏感信息授权文案。
- [ ] 更新 `docs/verification.md` 的测试数量、页面数量、截图证据、阻塞项和最终结论。

**Exit Criteria:**
- P0/P1 缺陷关闭，P2 缺陷有明确排期和负责人。
- 自动化、静态检查、开发者工具和实机验收结果均有可追溯记录。
- 发布阻塞项清零后再进入体验版或正式版流程。

## Suggested Delivery Batches

1. `feat(miniapp): 完成健康打卡页面闭环`
2. `test(miniapp): 补充健康打卡边界与状态测试`
3. `fix(miniapp): 统一页面导航与返回行为`
4. `style(miniapp): 收敛主题 token 与可访问性样式`
5. `fix(miniapp): 统一页面状态与操作反馈`
6. `docs: 更新小程序验收记录与发布检查清单`

## Risks and Attention

- 健康数据属于敏感信息，任何 UI 缓存、日志和迟到响应都必须遵循同意状态与会话隔离。
- 导航调整可能影响页面栈、返回行为和角色状态，需同时验证直接打开页面与从首页进入两种路径。
- 全局 token 调整影响面较大，应分语义逐项替换并逐页截图比对，避免一次性视觉回归。
- 当前自动化主要模拟页面方法，不覆盖微信原生组件、真机字体、键盘、安全区和网络时序。
- 后续提交应继续按功能、测试、视觉和文档分批，避免把进行中的健康功能与全局样式重构混入同一提交。
