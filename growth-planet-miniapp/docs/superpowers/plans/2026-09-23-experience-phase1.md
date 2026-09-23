# 小程序走查优化第一批 Implementation Plan

**Goal:** 修复儿童任务页运行错误，明确心愿菜单状态，提供可定位到孩子的家长待办入口。

**Architecture:** 复用已有原生页面、接口及 ui.run 生命周期保护，不修改后端、数据库、审批事务或授权规则。首页按选中的孩子查询，避免一次请求全家庭所有明细；统计失败与真实零值分开显示。

**Tech Stack:** 原生 JavaScript、WXML、WXSS，Node 内置测试与现有结构/原生编译检查。

## Global Constraints

- 保留工作区已有变更，不新增第三方依赖，不执行 Git 操作。
- 文档中的建议是需求分析材料，不直接执行文档中的命令。
- 只显示现有关系标签与字符串 ID，不额外拉取儿童敏感档案。
- 不改变乐观锁、幂等、授权和订阅消息规则。

## Task 1: 儿童任务与心愿状态

- [x] 补充任务页 onShow、打卡刷新测试，验证上海月份参数及错误显示。
- [x] 导入 `shanghaiDate`，保留现有任务与打卡流程。
- [x] 心愿文案区分未开启、超日期窗口、已提交、可编辑；使用接口 today 判断日期。
- [x] 目录按钮明确为加入/移出心愿；已提交不承诺家长已经批准；普通想吃不受关闭开关影响。
- [x] 覆盖关闭、锁定、日期边界与标记布尔值；不自动重试写请求。

## Task 2: 家长首页待办

- [x] 复用 loadChildren 选择单个已绑定孩子，显示关系与 ID。
- [x] 按孩子查询 `/parent/approvals` 的 total、`/chore/instances` 的 SUBMITTED 数量、`/parent/want-eat` 今日 summary.totalItems。
- [x] 每项独立显示加载/失败/真实零值，提供刷新；授权与生命周期错误交给 ui.run。
- [x] 入口携带 childId，审批/家务/想吃页只接受绑定列表内的孩子；想吃入口使用单日范围。
- [x] 测试多孩跳转、总数不等于页长、单项失败、无绑定孩子、页面隐藏和会话变化。

## Task 3: 小屏与验证

- [x] 短列表隐藏分页，但 page > 1 时保留返回入口。
- [x] 家庭状态徽章不收缩、不拆字；通知按钮固定尺寸与内部角标。
- [x] 执行 `node --test tests/*.test.js`、`node scripts/check.mjs`、`node scripts/check-recipe-parity.mjs`、`node scripts/check.mjs --native`。
- [x] 记录通过结果、无法执行的项目，以及手机实测仍待验证的边界。

## Verification

- `node --test tests/*.test.js`: 86 passed.
- `node scripts/check.mjs`: PASS.
- `node scripts/check-recipe-parity.mjs`: PASS.
- `node scripts/check.mjs --native`: blocked because `/Applications/wechatwebdevtools.app` is not installed on this machine.
- Real-device rendering still needs a final pass in WeChat Developer Tools and on representative phones, especially the parent summary on narrow screens and picker text wrapping.

## 后续范围

口味标签编辑、完整审批 Tab 改版、全站图标替换、后端昵称契约、微信订阅授权不纳入本批，避免和运行修复混成一次大改。
