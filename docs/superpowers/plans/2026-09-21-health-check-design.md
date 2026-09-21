# 设计文档 · 健康打卡（V0.0.2 F-033~F-035）

> **遵循工作流规范**：本文档为编码前的「分析 + 规划 + 设计」，确认后再编码。
> 关联 PRD：`成长星球_V0.0.2_PRD.html` §D；任务清单 T-017/T-018/T-019。
> 前置能力（已具备，可复用）：勋章服务 `MedalService`（F-048/T-010）、监护人同意 `ChildAuthorizationService.requireConsent`（F-007）、通知中台 `NoticeService.recordEvent`（F-011/E-008）。

## 1. 需求边界（来自 PRD）

| 编号 | 名称 | 优先级 | 关键约束 |
|------|------|--------|----------|
| F-033 | 打卡项配置 | P0 | 喝水/睡眠/运动等项，家长可增删；**属敏感信息，须在 F-007 同意范围内** |
| F-034 | 儿童打卡提交 | P0 | 一键打卡并记录时间；同一打卡项同日可设上限（如喝水多次） |
| F-035 | 打卡日历与连续记录 | P1 | 连续天数 streak 激励，触发连续勋章；断签重置；日历展示历史 |

**明确不做（本期）**：健康数据与健康建议/医疗化分析；真实健康硬件接入；跨设备同步。

## 2. 方案选项与决策

### 2.1 打卡项归属
- **采用：家庭私有打卡项**。`check_item` 归属 `family_id`，家长对**本家庭**增删改查。儿童只读本家庭项并点选打卡。
- 备选（未采用）：全局预置模板+家庭启用开关。理由：PRD 明确「家长可增删」，家庭私有更直接，且复用 `familyId` 隔离范式，无需新增模板表。
- 儿童端**不传** `familyId`（强约束：后端从 `UserContext` 派生）。

### 2.2 streak（连续天数）口径
- **采用：按儿童维度、跨所有打卡项**的「连续打卡 N 天」= 自最近一天往前数、每天（本地日，按家庭时区）至少有 1 条任意项记录的最长连续天数。
- 计算在查询时实时推导（不落地 streak 列），断签自然归零：若今日有记录则含今日；若今日尚无记录但从昨日往前连续，则 streak 计到昨日。
- 备选（未采用）：按单项 streak。理由：跨项整体 streak 对低龄儿童更有激励性，且勋章 `STREAK` 语义一致。

### 2.3 连续勋章触发
- 每次打卡后，计算该儿童当前 streak；对本批播种的 `HEALTH_STREAK_<N>` 勋章（见 §3.3），若 `streak >= N` 则调用 `MedalService.award(childId, familyId, code, refId=N, consecutiveCount=streak)`——幂等，同一阈值仅发一次。断签后 streak 归零不会撤销已发勋章（符合「达成即永久」）。
- 勋章墙展示由既有 `MedalService.listAwards` 的 `STREAK` 分支自动渲染进度。

### 2.4 同日上限（F-034）
- `check_item.daily_target`：`0`=不限；`>0`=该儿童该项当日记录数达上限后拒收（业务错误 `E-0xx_ITEM_DAILY_LIMIT`）。

## 3. 数据模型（新建两表）

### 3.1 `life_check_item`（打卡项配置，F-033）
```sql
CREATE TABLE life_check_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  family_id BIGINT NOT NULL,
  name VARCHAR(32) NOT NULL,
  icon VARCHAR(64) DEFAULT NULL,          -- emoji 或图标标识，选填
  unit VARCHAR(8) DEFAULT NULL,           -- 计量单位，选填（杯/小时/次…）
  daily_target INT NOT NULL DEFAULT 0,    -- 0=不限；>0=当日上限次数
  sort_order INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,         -- 乐观锁
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at BIGINT NOT NULL DEFAULT 0,
  KEY idx_family (family_id, sort_order, id),
  CONSTRAINT fk_check_item_family FOREIGN KEY (family_id) REFERENCES usr_family (id),
  CONSTRAINT chk_item_target CHECK (daily_target >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.2 `life_check_record`（打卡记录，F-034/F-035）
```sql
CREATE TABLE life_check_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  family_id BIGINT NOT NULL,
  child_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  item_name VARCHAR(32) NOT NULL,         -- 写入时快照，项被删后记录仍可辨认
  check_date DATE NOT NULL,               -- 家庭时区下的本地日
  check_time DATETIME NOT NULL,           -- 精确时间戳
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at BIGINT NOT NULL DEFAULT 0,
  KEY idx_child_date (family_id, child_id, check_date),
  KEY idx_item (family_id, item_id, check_date),
  CONSTRAINT fk_record_family FOREIGN KEY (family_id) REFERENCES usr_family (id),
  CONSTRAINT fk_record_child FOREIGN KEY (child_id) REFERENCES usr_user (id),
  CONSTRAINT fk_record_item FOREIGN KEY (item_id) REFERENCES life_check_item (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.3 勋章播种（写入 `life_medal_definition`）
本批随 DDL 播种跨项连续勋章（沿用 `v002_chore_medal.sql` 的播种方式）：

| code | name | category | conditionType | threshold | 说明 |
|------|------|----------|---------------|-----------|------|
| `HEALTH_STREAK_3` | 健康打卡 3 天 | HEALTH | STREAK | 3 | 连续 3 天 |
| `HEALTH_STREAK_7` | 健康打卡一周 | HEALTH | STREAK | 7 | 连续 7 天 |
| `HEALTH_STREAK_14` | 健康打卡双周 | HEALTH | STREAK | 14 | 连续 14 天 |
| `HEALTH_STREAK_30` | 健康打卡满月 | HEALTH | STREAK | 30 | 连续 30 天 |

> 阈值集合如需调整（如只留 7/30）以你的确认为准。

## 4. 接口设计（沿用 `@RequireRole` + `familyId` 后端派生）

### 4.1 家长端 `/api/parent/check-item`
| 方法 | 路径 | 说明 | 授权 |
|------|------|------|------|
| POST | `/` | 新增打卡项（name/icon/unit/dailyTarget） | PARENT + requireConsent |
| GET | `/` | 本家庭打卡项列表（按 sort_order） | PARENT + requireConsent |
| PUT | `/{id}` | 编辑项（`expectedVersion` 乐观锁） | PARENT + requireConsent |
| DELETE | `/{id}` | 软删项（`expectedVersion`） | PARENT + requireConsent |

### 4.2 儿童端 `/api/child/check-in`
| 方法 | 路径 | 说明 | 授权 |
|------|------|------|------|
| GET | `/items` | 本家庭可用打卡项（只读） | CHILD + requireConsent |
| POST | `/` | 一键打卡 `{itemId}`：校验同日上限→写记录→算 streak→触发勋章→（可选）通知 | CHILD + requireConsent |
| GET | `/today` | 今日各项目前次数（驱动「已达上限」展示） | CHILD + requireConsent |
| GET | `/calendar?month=YYYY-MM` | 当月打卡日标记 + 当前 streak + 历史连续段 | CHILD + requireConsent |

> **同意撤回（F-007/NF-5）**：任一健康接口先 `requireConsent`；若 `E-010` 则列表返回空、打卡拒绝，实现「停采并隐藏」。

## 5. 核心逻辑

### 5.1 `CheckService`（@Transactional）
- `listItems(familyId)`：查 `delete_at=0` 按 `sort_order`。
- `createItem(req)`：构造 `CheckItem`，`familyId` 后端派生，乐观锁默认 0。
- `updateItem(id, req, expectedVersion)` / `deleteItem(id, expectedVersion)`：行锁 + `transition()` 校验 version。
- `checkIn(childId, itemId)`：
  1. `member = authorization.lockBoundChild(childId)` + `authorization.requireConsent(member)`；
  2. 行锁 `CheckItem`（FOR UPDATE），校验存在且未删、归属家庭一致；
  3. `daily_target>0` 时统计 `(child, item, 今日)` 记录数，达上限抛 `E-0xx_ITEM_DAILY_LIMIT`；
  4. 插入 `CheckRecord`（含 `item_name` 快照、`check_date`=家庭时区今日、`check_time`=now）；
  5. `streak = computeStreak(childId, today)`（向前数连续有记录的日）；
  6. 对每个 `HEALTH_STREAK_<N>` 定义，若 `streak >= N` → `medalService.award(..., code, refId=N, consecutiveCount=streak)`（MANDATORY 内层事务）；
  7. （可选）streak 达阈值时 `noticeService.recordEvent(...)` 发站内「打卡成就」通知（E-008 降级不回滚）。
- `computeStreak(childId, fromDate)`：自 `fromDate` 起，逐日回退判定 `check_date` 是否有记录；`fromDate` 无记录则从 `fromDate-1` 起算；遇空日中断。

### 5.2 并发与安全
- 不涉及钱包/额度，无需 `WalletService` 行锁；仅 `CheckItem` 行锁防并发编辑冲突。
- `familyId`/`childId` 一律后端派生，客户端禁传；跨家庭查询返回 `E-009`。

## 6. 前端（T-019，pages/health/*）
- 家长：`打卡项管理` 列表 + 新增/编辑/删除（含每日上限设置）。
- 儿童：`今日打卡` 卡片网格（点一下即打卡，显示今日次数/上限）+ `打卡日历`（标记打卡日 + 当前连续天数 + 勋章进度入口）。
- 进入健康页前校验同意状态；撤回后展示「需家长同意」空态。
- 注册 `app.json` + `home` 二级入口 + `app-nav`（如纳入底部导航）。

## 7. 测试（CheckFlowIT）
- 家长建项 → 儿童可见并打卡 → 今日次数 +1；达每日上限拒收。
- streak 推导：构造连续 3/7 天记录 → streak 正确；中间断一天 → 重置。
- 勋章：streak 达阈值触发 `HEALTH_STREAK_*` 且幂等（不重复发放）。
- 同意撤回 → 打卡返回 `E-010`、列表隐藏。
- 跨家庭隔离：儿童 B 不可见/打卡家庭 A 的项（403）。
- 家长删项 → 历史记录保留快照名。

## 8. 落地步骤
| 步 | 内容 |
|----|------|
| S1 | `sql/v004_health_check.sql`：建两表 + 播种 4 枚 HEALTH_STREAK 勋章 |
| S2 | 实体 `CheckItem`/`CheckRecord` + Mapper |
| S3 | DTO：`CheckItemReq`(extends StrictRequest)/`CheckItemResp`/`CheckRecordResp`/`CheckStreakResp` |
| S4 | `CheckService` + `CheckItemController`(家长) + `CheckInController`(儿童) |
| S5 | `application-test.yml` 补 `v004_health_check.sql`（pom testResources 已含 v*.sql） |
| S6 | `CheckFlowIT` |
| S7 | 小程序 `pages/health/*` + 注册/入口 + `node --test` / `check.mjs` |
| S8 | 全量 IT 验证：`DOCKER_HOST=... DOCKER_API_VERSION=1.54 TESTCONTAINERS_RYUK_DISABLED=true mvn -o test -Dtest="*IT" -DargLine="-Dapi.version=1.54"` |

## 9. 待确认点
1. **streak 口径**：采用「按儿童跨所有项」整体连续（推荐），还是「按单项」？
2. **勋章阈值集合**：播种 3/7/14/30（推荐）还是仅 7/30？
3. **每日上限默认值**：新项默认 `daily_target=0`（不限，推荐）还是给个默认（如喝水=8）？
4. **成就通知**：打卡达阈值是否发站内通知（推荐发，复用 E-008）？
