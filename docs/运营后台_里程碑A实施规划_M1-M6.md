# 运营后台 · 里程碑 A 实施规划（M1/M3/M4/M5/M6）

> 版本 V1.0 · 2026-09-22 · 承《`运营后台_功能规划方案.html`》《`运营后台_详细设计文档.html`》《`运营后台_实现规划与设计.md`》
> 状态：**待确认后编码**（遵循「先规划后实现」工作流）

---

## 一、基线现状（2026-09-22 晚）

| 项 | 现状 |
|---|---|
| 后端 | Maven 多模块已完成并提交（`77a156d`…`ee3bb48`）：`sk-growthplanet-core`（实体/服务）/`sk-growthplanet-miniapp`（C 端 `/api/mini/**`）/`sk-growthplanet-admin`（运营端 `/api/admin/**`）/`sk-growthplanet-start`（启动+SQL） |
| 已完成模块 | M0 登录/工作台 + M2 账号/角色权限矩阵（地基纵切片，AdminConsoleIT 13/13 + 既有回归全绿） |
| 前端 | `growth-planet-admin/`（Vue3+Vite+Element Plus），API 已同步 `/api/admin` 前缀 |
| 未提交改动 | `sk-growthplanet-start/sql/v008_admin.sql`：登录日志 `admin_id` 改可空（支持记录失败登录尝试），随批次 1 一并提交 |
| 权限资源 | `AdminResource` 已全量预置 36 个资源常量（覆盖 M0–M10），播种矩阵随模块实现逐步启用 |

## 二、本规划范围

**范围内**：里程碑 A 剩余 5 个 P0 模块——M1 数据看板、M3 内容管理（含 UGC 审核）、M4 业务数据管理、M5 合规与隐私中心、M6 操作日志与审计。
**不在范围**：M7 通知（P1）、M8 监控告警（P1）、M9 系统配置（P1）、M10 帮助（P2）；账号风控（M2 的 P1 部分）；导出文件存储/异步导出中心（本期导出为同步 CSV，见风险 R3）。

## 三、设计对齐决策点（需确认，均为详设「设计建议」与代码现状的差异）

### D1 · UGC 审核状态机（M3）
详设流程 2 定义 `PENDING(机审待定) → APPROVED / REJECTED`；但现有 `life_family_dish` 实际为：
- `status`：`ON_SALE / OFF_SALE`（上下架，CHECK 约束）
- `visibility`：`PRIVATE / PUBLIC`（CHECK 约束）

**建议方案 A（零迁移，推荐）**：审核队列 = `visibility=PRIVATE` 且未驳回的家庭菜品；人审「通过」= `visibility → PUBLIC`（可选同时 `ON_SALE`）；「驳回」= 新增列 `review_status VARCHAR(20) DEFAULT 'PENDING'`（PENDING/APPROVED/REJECTED）+ `reject_reason`，仅作审核留痕，不动原状态机。即 **加 2 列、不改旧语义**，C 端展示逻辑不受影响。
（方案 B 为纯用现有字段推导，无留痕、无法区分「家长未提报审核」与「审核中」，不推荐。）

### D2 · 隐私工单状态机（M5）
详设建议 `PENDING → VERIFYING → VERIFIED → PROCESSING → DONE / REJECTED / EXPIRED`；实际代码现状为 `RECEIVED → PROCESSING → READY/FAILED → COMPLETED / REJECTED`（无 CHECK 约束，C 端 ComplianceService 已在用）。
**建议**：后台不复刻详设状态机，按**现状枚举**做运营视图与操作映射——
- 待核验 = `RECEIVED`（CP 核验通过 → 进入 `PROCESSING`，同时写 `sys_privacy_verification`）
- `PROCESSING` → 执行导出/删除 → `READY`（等待用户取件）/ `FAILED` / `REJECTED`（写 reason）
- `READY/COMPLETED` 终态只读
理由：状态机由 C 端既有业务代码驱动，后台另起一套会造成双写冲突；详设明确标注该状态机为「建议」。

### D3 · 「△ 审批」动作落点（M2/M4）
详设中 OP 的敏感操作（超额复核、改余额、补发奖励、冻结家庭）需 SA/CP 复核闭环。M2 现无审批工作流表。
**建议**：里程碑 A 先落 **两级简化**——OP 提交的敏感操作直接落 `sys_audit_log`（`result=SUBMITTED`）+ 前端二次确认（L4），**不建独立审批工单表**；完整审批工作流（M8 告警联动、待办闭环）归入里程碑 B。本期 M4 人工干预仅开放：超额确认单复核、勋章补发；**不开放改余额**（涉资金安全，留待 B 期带审批流后上线）。

## 四、模块实施规划

### M3 · 内容管理（CMS）——批次 2
**后端接口**（`sk-growthplanet-admin` 新增 `controller/` + core 层复用既有 Service/Mapper）：

| 接口 | 方法 | 说明 | 权限点 |
|---|---|---|---|
| `/api/admin/dishes` | GET/POST | 菜品分页列表（分类/状态/过敏原筛选）+ 新建 | 菜品库 view/create |
| `/api/admin/dishes/{id}` | PUT/DELETE | 编辑；删除=软删（`delete_at`），删除需 SA | 菜品库 edit/delete |
| `/api/admin/dishes/{id}/status` | PUT | 上架/下架 | 菜品库 edit |
| `/api/admin/dish-categories` | GET/POST/PUT/DELETE | 分类 CRUD | 菜品分类 |
| `/api/admin/menus/daily` | GET/POST/PUT | SCHOOL 每日菜单查询/编排/发布（复用 `life_menu_daily` 归属键约定） | 菜单编排 |
| `/api/admin/chore-tasks` | GET/POST/PUT | 任务库模板 CRUD | 任务库/奖励库 |
| `/api/admin/medals` | GET/POST/PUT | 勋章定义 CRUD + 启停 | 勋章配置 |
| `/api/admin/wish-menu-config` | GET/PUT | 心愿菜单开关/上限（`usr_family_setting`） | 心愿菜单配置 edit |
| `/api/admin/ugc/queue` | GET | 审核队列（`visibility=PRIVATE` 的家庭菜品，含命中过敏原标记） | UGC审核队列 view |
| `/api/admin/ugc/{id}/review` | POST | 通过（`visibility→PUBLIC`）/驳回（写 `reject_reason`） | UGC审核队列 approve |

**本期不做的 M3 子项**：词库管理（依赖机审引擎，归 B 期）、奖励库（现模型无独立奖励表，随 B 期域建模）、周食谱模板（详设「设计建议」，待产品定稿）。
**数据变更**：`life_family_dish` 加 `review_status`、`reject_reason` 两列（D1 方案 A），同步汇总 schema 与 v009 迁移脚本。
**前端页面**：菜品库（列表+抽屉表单）、菜品分类、菜单编排（按日/餐次网格）、任务库、勋章配置、UGC 审核队列（通过/驳回+原因）。

### M4 · 业务数据管理——批次 3
**原则**：查询/导出为主（`view/export`），人工干预仅 2 项且带 L4 二次确认（D3）。

| 接口 | 方法 | 数据源 | 权限 |
|---|---|---|---|
| `/api/admin/want-eat` | GET | `usr_child_want_eat`（按日期/孩子/餐次筛选） | 每日想吃 view |
| `/api/admin/confirmations` | GET | `life_menu_confirm` + `life_menu_item`（明细） | 确认单 view |
| `/api/admin/confirmations/{id}/review` | POST | 超额确认单复核（写 `life_confirm_approval`） | 确认单 approve |
| `/api/admin/confirm-approvals` | GET | `life_confirm_approval` 只读 | 审批记录 view |
| `/api/admin/wallets` | GET | `life_wallet` + `life_allowance_rule`（余额/限额） | 钱包与流水 view |
| `/api/admin/allowance-logs` | GET | `life_allowance_log` 流水 | 钱包与流水 view |
| `/api/admin/chore-instances` | GET | `life_chore_instance`（完成率） | 家务健康 view |
| `/api/admin/check-records` | GET | `life_check_record`/`life_check_item`（打卡覆盖） | 家务健康 view |
| `/api/admin/medal-awards` | GET/POST | `life_medal_award` 查询；POST=补发（L4+审计） | 勋章发放 view/create |
| `/api/admin/export/{domain}` | POST | 各域 CSV 导出（默认脱敏） | 对应资源 export |

**导出脱敏**：孩子姓名/昵称默认脱敏（`张*`）、家长手机号脱敏（`138****0000`）；DC 角色按授权学校/家庭过滤（范围权限先落「全量只读」，细粒度授权归 B 期，见风险 R2）。
**前端页面**：想吃记录、确认单列表+详情（含超额标记与复核）、审批记录、钱包/流水、家务打卡、勋章发放。

### M6 · 操作日志与审计——批次 1（先做，纯读无争议）
**后端**：
| 接口 | 说明 | 权限 |
|---|---|---|
| `/api/admin/audit-logs` | 分页筛选（actor/action/target_type/时间/结果/错误码），**RA 仅 view+export** | 操作日志 view |
| `/api/admin/audit-logs/{id}` | 详情（detail JSON 回放） | 操作日志 view |
| `/api/admin/audit-logs/export` | CSV 导出 | 操作日志 export |
| `/api/admin/c-audit-logs` | C 端关键操作（`target_type` 白名单过滤：家庭/解绑/额度变更/数据出口） | C端关键操作 view |

**实现要点**：复用既有 `AuditLog`/`AuditLogMapper`（已有表，AUTO_INCREMENT 已 885+）；查询走只读变体；**导出操作本身也落审计**（谁导了什么范围）。
**前端页面**：操作日志（筛选器+表格+详情抽屉）、C 端关键操作。

### M5 · 合规与隐私中心——批次 4
**后端**：
| 接口 | 说明 | 权限 |
|---|---|---|
| `/api/admin/consents` | 同意留痕查询（`usr_consent_log`，按孩子/类型/时间） | 同意留痕 view |
| `/api/admin/privacy-requests` | 工单列表（**CP/SA/RA**；DC/OP/CR 不可见） | 隐私工单 view |
| `/api/admin/privacy-requests/{id}/verify` | CP 核验（写 `sys_privacy_verification.code_hash`） | 隐私工单 approve |
| `/api/admin/privacy-requests/{id}/reject` | 驳回（写 reason，落审计） | 隐私工单 approve |
| `/api/admin/privacy-verifications` | 核验记录只读 | 核验记录 view |
| `/api/admin/compliance-checklist` | 合规清单查看/勾检（静态清单存储于 `sys_notice` 或新表，随实现定） | 合规清单 view/edit |

**要点**：
- 操作映射按 D2（现状枚举）；核验/驳回仅 **CP**（`@AdminRequireRole` 或 `requirePerm(隐私工单, approve)`），SA 走旁路但**前端不隐藏、后端不放行 RA 之外的写**。
- **CP 数据隔离**：本期实现「隐私域隔离」——隐私工单接口仅对具备 `隐私工单:view` 权限的角色开放（SA/CP/RA），C 端明细字段（孩子全名/过敏原文）仅 CP 可见。
- 导出/解密动作全部落 `sys_audit_log`。
**前端页面**：同意留痕、隐私工单（核验/驳回流程抽屉）、核验记录、合规清单。

### M1 · 数据看板与报表——批次 5（最后，聚合各域）
**后端**：
| 接口 | 指标 | 权限 |
|---|---|---|
| `/api/admin/dashboard/overview` | 家庭/孩子累计+新增+活跃、确认单待审/通过/超额、UGC 待审、隐私积压 | 运营看板 view |
| `/api/admin/dashboard/meals` | 想吃热度 Top、确认单量、超额率、SCHOOL vs FAMILY 占比 | 运营看板 view |
| `/api/admin/dashboard/allowance` | 虚拟币发放/消耗/沉淀（`life_wallet`/`life_allowance_log` 聚合） | 运营看板 view |
| `/api/admin/dashboard/chores` | 任务完成率、连续天数分布（`life_chore_streak`）、打卡覆盖 | 运营看板 view |
| `/api/admin/dashboard/medals` | 发放量、获得率、热门勋章 | 运营看板 view |
| `/api/admin/reports/export` | 看板 CSV 导出（脱敏） | 报表导出 export（SA/OP/DC） |

**口径**：严格按详设第八节；「活跃」定义 = 近 7 日有任一确认单/打卡/任务完成（需在实现时随口径表二次确认，见待确认 Q3）。
**实现要点**：聚合查询一律走 Mapper 统计 SQL（`selectCount`/自定义聚合），避免全表拉取；看板数据不做持久化（本期不建汇总表，数据量当前可支撑实时聚合）。
**前端页面**：运营总览、餐食看板、零花钱看板、家务健康看板、勋章看板（ECharts）。

## 五、实施批次与提交计划

| 批次 | 内容 | 交付物 | 提交建议 |
|---|---|---|---|
| **批次 1** | M6 审计（后端+前端+IT）+ v008 未提交改动 | AdminAuditIT | `feat(console): M6 审计查询` + `fix(console): 登录日志 admin_id 可空` |
| **批次 2** | M3 内容管理（含 D1 两列迁移、UGC 队列） | AdminCmsIT | `feat(console): M3 内容管理+UGC审核` |
| **批次 3** | M4 业务数据（查询/导出/超额复核/勋章补发） | AdminBizDataIT | `feat(console): M4 业务数据` |
| **批次 4** | M5 合规隐私（CP 隔离、核验闭环） | AdminPrivacyIT | `feat(console): M5 合规隐私` |
| **批次 5** | M1 看板（5 张看板 + 导出） | AdminDashboardIT | `feat(console): M1 看板` |

每批次内顺序：迁移 SQL（如有）→ core 层复用/扩展 → admin 模块 Controller/Service → 编译 → IT（Testcontainers）→ 前端页面 → 前端构建验证 → 提交。**每批完成即等您审阅，全部批次可随时暂停。**

## 六、测试策略

- **IT**（`sk-growthplanet-start`，Testcontainers）：每批次一个 `Admin*IT`，覆盖：权限矩阵正向/反向（越权 403）、审计落库断言、状态机流转、脱敏输出格式、CP 隔离（DC/OP/CR 访问隐私工单必须 403）。
- **回归**：每批次跑既有相关 IT（含 6 个使用旧 `/api/mini/admin/**` 前缀 C 端 ADMIN 接口的 IT），确保零破坏。
- **前端**：`npm run build` 编译验证；页面手工冒烟（登录→对应页面→权限显隐）。

## 七、风险与回滚

| # | 风险 | 缓解 |
|---|---|---|
| R1 | D1 加列影响 C 端既有 `FamilyDishService` 查询 | 新列带 DEFAULT，MP 实体不删旧字段；IT 全量回归 |
| R2 | DC 范围权限（按学校/家庭授权）未建模 | 本期 DC=全量只读；详设 3.3 范围授权归 B 期，文档标注 |
| R3 | 同步 CSV 导出大数据量阻塞请求 | 当前数据量小（单家庭域 <1 万行）；加 LIMIT 上限 1 万；异步导出中心归 B 期 |
| R4 | 看板实时聚合随数据增长变慢 | 加时间范围强制筛选（默认 30 天）；汇总表归 B 期 |
| R5 | 隐私工单核验与 C 端 `ComplianceService` 状态流转竞态 | 核验/驳回走**手写乐观锁**（`version` 比对，0 行→E007），沿用项目约定 |
| R6 | 权限矩阵播种扩充影响存量角色权限 | initializer 仅新增资源点、不改已有点（沿用 `seeded` 去重策略） |

## 八、待确认问题

- **Q1（D1）**：UGC 审核采用方案 A（加 `review_status`/`reject_reason` 两列）？
- **Q2（D2）**：隐私工单按现状枚举 `RECEIVED/PROCESSING/READY/FAILED/COMPLETED/REJECTED` 做运营视图？
- **Q3**：看板「活跃家庭/孩子」口径按「近 7 日有确认单/打卡/任务完成」定义？
- **Q4（D3）**：本期不开放「改余额」，人工干预仅超额复核+勋章补发？
- **Q5**：批次顺序按 M6→M3→M4→M5→M1（先易后难、先纯读后写）执行？
