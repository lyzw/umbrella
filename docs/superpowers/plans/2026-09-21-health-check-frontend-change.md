# 变更/设计文档 · 健康打卡前端（V0.0.2 F-033~F-035）

> **文档性质**：编码已完成的「前端变更 + 设计」文档（对照 `2026-09-21-health-check-design.md` 后端设计，补全小程序侧实现）。
> **编写日期**：2026-09-21
> **关联后端设计**：`2026-09-21-health-check-design.md`（建表 + 4 枚 HEALTH_STREAK 勋章播种 + 接口契约 + `CheckFlowIT`）
> **git 状态**：未提交。新增 `miniprogram/pages/health/`（`??`）；改动 `app.json`/`app.wxss`/`pages/home/*`/`tests/pages.test.js`（`M`）。**待用户授权提交。**

---

## 1. 变更概述

### 1.1 变更清单

| 类型 | 文件 | 改动 | 说明 |
|------|------|------|------|
| 新增 | `miniprogram/pages/health/index.js` | +235 行 | 家长/儿童双角色逻辑主体 |
| 新增 | `miniprogram/pages/health/index.wxml` | +92 行 | 双角色视图（家长配置 / 儿童打卡） |
| 新增 | `miniprogram/pages/health/index.wxss` | +92 行 | 打卡项卡片、连续天数卡、月历样式 |
| 新增 | `miniprogram/pages/health/index.json` | +3 行 | 导航栏标题「健康打卡」 |
| 改动 | `miniprogram/app.json` | +1 行 | 注册 `pages/health/index` |
| 改动 | `miniprogram/app.wxss` | +2 行 | 新增主题变量 `--c-health` / `--c-health-soft` |
| 改动 | `miniprogram/pages/home/index.wxml` | +7 行 | 首页新增「健康打卡」二级入口 |
| 改动 | `miniprogram/pages/home/index.wxss` | +1 行 | 入口图标配色 `.health-icon` |
| 改动 | `tests/pages.test.js` | +127 行 | 健康页家长/儿童双角色用例（前端 48 测试全绿） |

### 1.2 设计依据（来自后端设计文档，已确认决策）

| 决策点 | 结论 | 前端落点 |
|--------|------|----------|
| 打卡项归属 | 家庭私有，`family_id` 后端派生，儿童不传 | 儿童端接口不拼任何归属参数 |
| streak 口径 | 按儿童维度、跨所有项、「连续 N 天」（实时推导，断签归零） | 展示 `currentStreak`、不落地 |
| 连续勋章 | 打卡后服务端发 `HEALTH_STREAK_3/7/14/30`（幂等） | 打卡成功提示引导去勋章墙 |
| 同日上限 | `dailyTarget=0` 不限；`>0` 达上限拒收（`E-0xx_ITEM_DAILY_LIMIT`） | 按钮置灰 + 进度条 + `已达上限` 提示 |
| 同意撤回（F-007） | 任一健康接口先 `requireConsent`，撤回则列表空/打卡拒 | 后端拦截，前端按空列表/错误提示空态 |

---

## 2. 页面架构

单页 `pages/health/index`，**同一页面按 `role` 分支两套 UI**（沿用 home/家务/勋章的「二级入口页」范式，非底部 tab）。

```
onShow
 └─ ui.guard(this)            // 登录态守卫，落定 role(PARENT/CHILD)
 └─ read()
      ├─ role=PARENT → readManaged()    // F-033 打卡项配置
      └─ role=CHILD  → readChild()     // F-034/F-035 今日打卡 + 日历
```

### 2.1 家长端（F-033 打卡项配置）

- **列表**：`GET /parent/check-item` → `managed[]`，含 `itemId/name/icon/unit/dailyTarget/sortOrder/version`。
- **新增/编辑**：抽屉式 `form-panel`，字段 `name(1-32)` / `icon(≤64,建议 emoji)` / `unit(≤8)` / `dailyTarget(0~9999)` / `sortOrder(0~9999)`。
  - 行内校验（长度/整数/范围）走 `save()` 内 `throw new Error(...)`，`ui.run` 捕获写入 `page.data.error` 展示。
  - 编辑带 `expectedVersion` 走乐观锁；`PUT /parent/check-item/{id}?expectedVersion={version}`。
  - 新增 `POST /parent/check-item`。
- **6 个预设快捷填充**：`PRESETS` 常量（喝水/睡眠/运动/刷牙/阅读/洗手），点 chip 自动灌表，仍可改。预设仅用于表单提速，**落库以家长确认为准**。
- **删除**：`remove()` 先 `ui.confirm` 二次确认（提示「历史记录仍保留」），再 `DELETE /parent/check-item/{id}?expectedVersion={version}`。
- **同意提示**：页底固定提示「敏感信息，仅在已授权范围内采集，撤回后自动停采并隐藏」。

### 2.2 儿童端（F-034 打卡提交 / F-035 日历与连续）

- **三并发拉取**：
  - `GET /child/check-in/items` → 本家庭可用项（只读）
  - `GET /child/check-in/today` → 今日各项目前次数 + 上限 + `reached`
  - `GET /child/check-in/calendar?month=YYYY-MM` → `checkedDates[]` + `currentStreak`
- **今日打卡卡**：每项展示 `图标 名称` / `limitText`（如「1 / 2杯」或「不限次数」）/ 进度条 `percent` / 按钮。
  - `reached=true` 时按钮 `disabled` + 文案「今日已完成」。
  - `checkIn(e)`：`POST /child/check-in?itemId={id}`（**itemId 拼 path，因 POST data 走 body**），成功后重拉并弹 `streakReceipt` 成就提示。
- **连续天数卡**：`streak-card` 展示 `currentStreak` 天 + 今日完成 `doneCount/totalCount`。
  - `STREAK_MILESTONES=[3,7,14,30]`，当 streak 命中里程碑时提示「去勋章墙看看新解锁的成就 🏅」。
- **月历**：`monthCells()` 计算当月网格（含首日偏移 pad、未来日 `future` 透明、今日 `today` 描边、打卡日 `checked`）。
  - 翻月 `shiftMonth()`：`delta` 计算目标月；**禁止翻到未来月**（`target > today月` 直接 return；「下月」按钮按 `canNextMonth=month<当前月` 禁用）。
  - 周标签 `日一二三四五六`，本地日口径（上海时区，`shanghaiDate()`）。

---

## 3. 接口契约（前端实际调用）

| 角色 | 方法 | 端点 | 关键参数 | 用途 |
|------|------|------|----------|------|
| 家长 | GET | `/parent/check-item` | — | 打卡项列表 |
| 家长 | POST | `/parent/check-item` | body: `{name,dailyTarget,sortOrder,icon?,unit?}` | 新增 |
| 家长 | PUT | `/parent/check-item/{id}?expectedVersion={v}` | body 同上 | 编辑（乐观锁） |
| 家长 | DELETE | `/parent/check-item/{id}?expectedVersion={v}` | query: `expectedVersion` | 删除（软删） |
| 儿童 | GET | `/child/check-in/items` | — | 可用项（只读） |
| 儿童 | GET | `/child/check-in/today` | — | 今日次数/上限 |
| 儿童 | GET | `/child/check-in/calendar?month=YYYY-MM` | query: `month` | 打卡日集合 + streak |
| 儿童 | POST | `/child/check-in?itemId={id}` | itemId 拼 path、body `{}` | 一键打卡 |

> 说明：所有端点 `familyId`/`childId` 由后端从鉴权派生，**前端不传**。与后端设计文档 §4 完全一致，`CheckFlowIT` 已覆盖。

---

## 4. 状态管理与数据流

- 页面 `data` 分三块：`role/busy/error/receipt/ready`（通用）、儿童端 `checkItems/doneCount/totalCount/streak/cells/month*`、家长端 `managed/presets`。
- `onShow` 重置为 `blankForm()` + 当前上海日 + 当月，再 `ui.run(read)`；`onHide` 清空避免串数据。
- `checkItems` 由 `items × todayRows` 左连接派生（`rows` 用 `itemId` 建 Map），统一产出 `key/count/dailyTarget/reached/percent/targetText/limitText/actionLabel`，视图仅做展示。
- `cells` 由 `monthCells(month, checkedSet, today)` 纯函数产出，便于单测。

---

## 5. 异常处理

| 场景 | 前端处理 |
|------|----------|
| 表单校验失败（名称/图标/单位/上限/排序） | `save()` 抛错 → `ui.run` 捕获 → `page.data.error` 行内提示（不提交） |
| 乐观锁冲突（`expectedVersion` 过期） | 后端业务码 → `ui.run` 写入 `error`，提示刷新后重试 |
| 同日上限（`reached=true`） | 按钮 `disabled`，文案切「今日已完成」，不发起请求 |
| 同意撤回（F-007） | 后端 `requireConsent` 拦截 → 列表空/打卡拒 → 前端展示空态 + 页底敏感提示 |
| 网络/并发失败 | `ui.run` 统一兜底，保留已填表单，不丢数据 |
| 翻到未来月 | `shiftMonth` 直接 return，下月按钮禁用 |

---

## 6. 样式与主题

- `app.wxss` 新增：`--c-health:#ff6b9d`（主题粉）、`--c-health-soft:#ffeef5`（浅底）。
- `health/index.wxss` 复用全局 token（`--surface`/`--line`/`--radius-*`/`--shadow-soft`/`--positive-soft`），新增 `.streak-card` / `.calendar` / 预设 chip 等局部类。
- 连续天数数值用 `font-variant-numeric: tabular-nums` 对齐；卡片用主色描边标记「今日」。
- 响应式：`@media (max-width:420px)` 下工具栏/表单行转竖排，按钮全宽。

---

## 7. 测试覆盖

`tests/pages.test.js` 新增健康页用例（模拟 `loadPage('health'[, role])` + `mock api`）：

- **家长端**：加载调 `GET /parent/check-item`；新增调 `POST` 且 body 字段正确；编辑调 `PUT /parent/check-item/11?expectedVersion=3`；删除调 `DELETE /parent/check-item/11` 带 `query:{expectedVersion:3}`。
- **儿童端**：三并发拉取后 `checkItems` 派生正确（`1 / 2杯`、`percent=50`、不限次数项文案）；`checkIn` 发 `POST /child/check-in?itemId=11` 且 `reached` 翻 true、`percent=100`、按钮置灰；空项列表空态；月历 `checked` 标记命中、翻上/下月 `canNextMonth` 与未来月拦截正确。
- 全量 `node --test` 48 通过；`node scripts/check.mjs` 与 `--native`（wcc+wcsc）PASS（14 pages / 201 bindings）。

---

## 8. 影响范围与回滚点

### 8.1 影响范围
- **新增独立页面** `pages/health/*`，无改动其他业务页逻辑。
- **入口联动**：`home` 新增一行二级入口（双角色文案）；`app.json` 注册一页。
- **全局样式**：`app.wxss` 仅追加 2 个 CSS 变量，无破坏性。
- **依赖**：复用既有 `services/api`（`get/post/put/del`）、`utils/page`（`guard/run/input/confirm`）、`utils/domain`（`shanghaiDate`）；无新依赖。

### 8.2 回滚点
- 纯前端改动，**单页隔离**，回滚即删除 `pages/health/` 四文件 + 还原 `app.json`/`app.wxss`/`home` 两文件 + 移除测试，无需数据迁移。
- 前端不写库，回滚不影响后端 `life_check_item` / `life_check_record` 已存数据。
- 回滚顺序建议：先撤 `home` 入口 → 移除 `app.json` 注册 → 删除 `pages/health/` → 还原 `app.wxss` 变量与 `tests`。

---

## 9. 待确认 / 遗留

1. **提交授权**：当前为工作树改动（未 commit/push），待用户授权后按「前端小程序」单主题提交（不与家庭菜品后端混批）。
2. **同意态前端空态细化**：当前依赖后端返回空列表 + 页底文案提示；是否需前端在读到空且非真正「无项」时区分「未配置」与「已撤回同意」两类空态（需后端在响应中给区分字段，如 `consent:false`）——**待确认**，本期先用统一文案。
3. **勋章墙跳转**：`streakReceipt` 仅文案提示「去勋章墙」，未做实际跳链；勋章墙入口沿用 `medal` 既有页，待确认是否在此页加跳转按钮。
4. **日历范围**：当前允许查看任意历史月（仅禁止未来月），与后端契约一致；若需限制最早可查月份，待确认。
