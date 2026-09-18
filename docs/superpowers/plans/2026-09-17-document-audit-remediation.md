# Documentation Audit Remediation Implementation Plan

> **For agentic workers:** Execute this plan inline, task by task. This is documentation maintenance, not an application implementation or a database migration.

**Goal:** 修复 24 项审计发现对应的文档矛盾，补齐关键设计约束和验收条件，保留未经验证的发布阻断项。

**Architecture:** PRD 定义产品行为，详细设计定义接口、数据和事务，开发计划定义依赖和验收，交互与 UI 展示同一套业务状态。修订基线集中记录新增契约、风险和审计追踪，历史报告保留原文并标注失效结论。

**Tech Stack:** Existing HTML/CSS/Mermaid documents; Markdown index and audit register; Node.js built-in modules for static verification.

## Global Constraints

- 仅修改 docs，遵循现有布局；不修改应用代码，不执行 Git 或数据库操作。
- 新字段与接口均为设计提案，不能声明现有服务已支持。
- 未获验证的身份核验、法规适用、数据保留周期、版本兼容与人员排期保留发布闸门。
- 保留用户已有改动；手工修改使用 apply_patch。

### Task 1: 明确修订基线

**Files:** Create `docs/README.md`, `docs/成长星球_V0.0.1_审计修订基线.md`.

- [x] 记录文档权威关系、修订范围、24 项发现映射及未决责任角色。
- [x] 定义同意先于档案、审批原子性、额度重校验、发放、菜单边界及隐私请求契约。

### Task 2: 同步需求与实现设计

**Files:** Modify PRD、详细设计、开发计划、三份规划 HTML.

- [x] 修正原文中的冲突、状态图、接口表与字段说明。
- [x] 开发图改为无环前置依赖；补齐并发和失败恢复验收，明确迁移交付闸门。

### Task 3: 同步交互、原型与历史报告

**Files:** Modify 交互设计、高保真 UI、评估记录下三份 HTML.

- [x] 修正金额、无辣筛选、营养断言、授权主体、字号、对比度和按钮尺寸。
- [x] 为历史报告增加审计版本说明，不伪造已完成实施或通过合规审核。

### Task 4: 静态回归

**Files:** Create `docs/check-docs.mjs`.

- [x] 检查 HTML ID、相对链接与锚点、依赖图环、关键契约、金额示例及对比度。
- [x] 执行 `node docs/check-docs.mjs`；记录执行结果和未执行的浏览器、应用、数据库验证。
- [x] 更新本计划状态并给出变更与剩余风险。

## Verification Record

2026-09-18：Node语法检查通过；621项静态检查通过，覆盖11份HTML和3份Markdown；开发依赖图25节点/32条边，无环及反向Sprint依赖。白字对比度蓝6.70:1、绿5.02:1。检查脚本的内存异常样例自测通过。

文档维护任务已完成，不代表设计全部获批或应用已实现。未执行完整浏览器/Mermaid渲染、接口及并发测试、迁移演练、压测或法规核验；发布阻断项继续保留在修订基线第7至9节。本次未执行Git操作、数据库操作、应用修改或依赖安装。
