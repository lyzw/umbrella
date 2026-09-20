# 成长星球小程序 Implementation Plan

**Goal:** 实现可导入微信开发者工具的原生小程序首版，连接现有后端核心接口，不将前端代码交付标记为上线验收。

**Architecture:** 新建独立 `growth-planet-miniapp/` 工程。页面消费统一 API 客户端，复用角色路由、金额、日期和幂等工具；不修改后端鉴权模型。角色来自服务端，不提供身份切换或家长代提交。

**Tech Stack:** 原生 JavaScript / WXML / WXSS，Node 内置 test runner，无新增第三方依赖。

## Global Constraints

- 不执行 Git 操作，不操作业务库，不保存儿童档案、点餐草稿或行为日志到本地存储。
- 仅保存会话 token、角色及到期时间；401 清理会话和页面敏感状态。
- 默认合成开发模式。真实微信登录、真实数据采集须显式配置并通过 Q-01/Q-07；默认游客 AppID 不能作为真实微信验证证据。
- 金额使用整数分做展示计算，API 传两位小数字符串；ID 始终为字符串。
- 不确定写入结果保留同一请求及幂等键，不自动重试写请求，不用新键掩盖超时。
- SCHOOL 只展示和收藏；UNKNOWN、过敏冲突、下架、非当日菜单不可提报。服务端仍是安全校验权威。
- 家长明确确认后才能提交 E-011 预览字段；不能自动同意超额。
- 后端没有家长菜品目录接口，家长菜单编辑器暂不伪造可选目录。真实订阅适配器未完成，不展示订阅发送成功。
- 微信开发者工具编译、手机预览、正式隐私配置与域名审核分别记录，不以 Node 测试冒充实机验收。

## Task 1: 客户端基础与契约测试

**Files:** `growth-planet-miniapp/project.config.json`、`miniprogram/app.*`、`miniprogram/config.js`、`miniprogram/services/*.js`、`miniprogram/utils/*.js`、`tests/core.test.js`。

**Interfaces:** `api.get(path, query)`、`api.post(path, body, key)`、`api.put(path, body)` 返回响应 data；失败抛出含 code/status/data/requestId 的错误。`session.set(auth)`、`session.get()`、`session.clear()`。`operations.run(scope, body, send)` 在本次会话中保留未知结果请求。

- [x] 写入并执行金额、上海日期、幂等、HTTP/网络错误、会话到期测试。
- [x] 实现客户端、错误映射、严格会话清理与开发配置。
- [x] 检查配置和原生页面注册完整性。

## Task 2: 身份与家庭

**Files:** `pages/login/*`、`pages/family/*`、`pages/profile/*`。

**Interfaces:** 使用已有 `/auth/wx-login`、`/auth/select-role`、`/family/*`、`/compliance/consent`、`/child/profile`、`/child/preferences`。

- [x] 实现登录、一次角色选择、家庭创建/邀请/申请/审批及关系刷新。
- [x] 先查询当前协议，独立勾选并记录自报年龄，GRANTED 后才能审批绑定和显示档案表单。
- [x] 档案仅家长可编辑；儿童只可编辑 dislikes/tastes。隐藏、退出、同意失效后清空表单。

## Task 3: 儿童导航与点餐确认

**Files:** `components/app-nav/*`、`pages/home/*`、`pages/menu/*`、`pages/confirmation/*`。

**Interfaces:** `/menu/daily`、`/menu/mark-favorite`、`/menu/confirm`、`/menu/confirms`、`/parent/approvals` 及确认动作接口。

- [x] 儿童五 Tab 和家长四入口；任务/学习仅占位，成长仅静态幼苗。
- [x] 日期/餐次/来源切换、严格无辣筛选、收藏、安全提示、份数和整数分总额。
- [x] 实现提报、历史列表、详情、撤回、拒绝/建议/重提和显式超额确认。
- [x] 页面隐藏停止轮询；超时保留原命令，展示查询/重试入口。

## Task 4: 钱包、通知与权利请求

**Files:** `pages/wallet/*`、`pages/notices/*`、`pages/privacy/*`。

**Interfaces:** `/wallet/*`、`/notices`、`/notices/{id}/read`、`/compliance/data-export`、`/compliance/data-delete`、`/compliance/requests/{id}`。

- [x] 钱包余额/额度/31天内流水、家长发放和版本化额度设置。
- [x] 本人通知分页、未读筛选、重复已读处理；不伪造真实订阅授权。
- [x] 权利申请、重新登录核验删除申请、工单查询、同意撤回及有限导出边界提示。

## Task 5: 验证与交付

**Files:** `tests/*.test.js`、`scripts/check.mjs`、`README.md`、`docs/verification.md`；更新后端 README 的前端交付边界。

- [ ] `node --test tests/*.test.js`，覆盖安全、幂等、错误和页面核心行为。
- [ ] `node scripts/check.mjs`，验证每页 JS/JSON/WXML、处理器和资源引用。
- [ ] 使用本机微信开发者工具尝试编译/打开，记录阻塞及真实验证范围。
- [ ] 更新本计划勾选状态、测试证据和待办；不关闭生产发布闸门。
