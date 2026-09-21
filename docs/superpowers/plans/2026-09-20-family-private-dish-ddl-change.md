# 数据模型变更文档 · 家庭私有菜品录入与菜单混合编排

> **遵循工作流规范**：本文档为编码前的「变更文档」，确认后再编码。配套 PRD：`2026-09-20-family-private-dish-prd.md`。

## 文档信息

| 项目 | 内容 |
|------|------|
| 文档版本 | V1.0 |
| 编写日期 | 2026-09-20 |
| 关联 PRD | `2026-09-20-family-private-dish-prd.md`（C-01~C-06 已确认） |
| 变更类型 | 新建表 + 现有表结构变更 + 数据迁移 |
| 风险等级 | 中（涉及 life_menu_daily.dish_ids 语义变更，历史数据需迁移） |

---

## 1. 变更概述

### 1.1 变更目标

| 编号 | 变更项 | 类型 | 说明 |
|------|--------|------|------|
| D-01 | 新建 `life_family_dish` 表 | 新建 | 家庭私有菜品表，复用 life_dish 全字段 + family_id + visibility |
| D-02 | `life_menu_daily.dish_ids` 语义变更 | 结构变更 | 从纯 ID 数组 `[1,2,3]` 改为对象数组 `[{type,id}]` |
| D-03 | `life_menu_daily` 新增 `version` 列 | 加列 | 乐观锁，支持并发冲突检测（C-03） |
| D-04 | `life_menu_daily.chk_menu_dishes` 约束 | 约束变更 | 适配对象数组（长度约束不变 1-50） |
| D-05 | 历史数据迁移 | 数据迁移 | 旧 dish_ids 一次性补 `{type:"PRESET"}` |
| D-06 | `life_menu_item.uk_confirm_dish` 唯一键扩展 | 约束变更（编码中发现） | 由 `(confirm_id, dish_id)` 扩展为 `(confirm_id, dish_id, source_type)`，避免 PRESET#1 与 FAMILY#1 误判重复（详见 §3.4） |

### 1.2 不变更项（明确）

- `life_dish`（公共预置菜品）表结构**不变**
- `life_dish_category`（预置分类）表结构**不变**，life_family_dish.category_id 引用它
- `life_wallet` / `life_allowance_rule` / `life_allowance_log`（钱包体系）**不变**，私有菜品复用现有扣款路径
- 软删除 `delete_at`、`create_time`/`update_time` 约定**不变**

---

## 2. 新建表 DDL：life_family_dish

### 2.1 DDL（执行文件 `growth-planet/sql/v002_family_dish.sql`）

```sql
-- 家庭私有菜品表：复用 life_dish 全字段 + family_id 归属 + visibility 预留 UGC
-- 执行顺序：在 sprint3_catalog.sql（life_dish / life_dish_category 已建）之后
CREATE TABLE life_family_dish (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  family_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  image_url VARCHAR(255) DEFAULT NULL,
  virtual_price DECIMAL(10,2) NOT NULL DEFAULT 0,
  calories INT DEFAULT NULL,
  tags VARCHAR(255) DEFAULT NULL,
  allergens JSON NOT NULL DEFAULT (JSON_ARRAY()),
  allergen_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
  spice_level TINYINT NOT NULL,
  visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
  status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at BIGINT NOT NULL DEFAULT 0,
  KEY idx_family_status (family_id, status, id),
  KEY idx_family_category (family_id, category_id, id),
  KEY idx_family_update (family_id, update_time),
  CONSTRAINT fk_family_dish_family FOREIGN KEY (family_id) REFERENCES usr_family (id),
  CONSTRAINT fk_family_dish_category FOREIGN KEY (category_id) REFERENCES life_dish_category (id),
  CONSTRAINT chk_fdish_price CHECK (virtual_price >= 0),
  CONSTRAINT chk_fdish_calories CHECK (calories IS NULL OR calories >= 0),
  CONSTRAINT chk_fdish_spice CHECK (spice_level BETWEEN 0 AND 3),
  CONSTRAINT chk_fdish_status CHECK (status IN ('ON_SALE', 'OFF_SALE')),
  CONSTRAINT chk_fdish_visibility CHECK (visibility IN ('PRIVATE', 'PUBLIC')),
  CONSTRAINT chk_fdish_allergen_status CHECK (allergen_status IN ('UNKNOWN', 'DECLARED')),
  CONSTRAINT chk_fdish_allergens CHECK (JSON_TYPE(allergens) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2.2 设计说明

| 字段 | 说明 |
|------|------|
| `family_id` | 家庭归属，**后端从家长鉴权派生，客户端禁传**；跨家庭隔离依据 |
| `category_id` | 引用预置分类 `life_dish_category`，本期不自建分类（C-05 决策） |
| `visibility` | 本期固定 `PRIVATE`；预留 `PUBLIC` 供未来社区公开（UGC），**客户端禁传** |
| `status` | ON_SALE/OFF_SALE，家长上下架（F-04） |
| `version` | 乐观锁，手写 transition() 校验（沿用家务模块约定） |
| `delete_at` | 软删除，删除后历史菜单引用标记缺失（F-05） |
| 无 `family_id + name` 唯一键 | 同家庭允许菜名重复（C-05 决策，个性化） |

### 2.3 与 life_dish 的字段对照

| life_dish 字段 | life_family_dish 对应 | 差异 |
|----------------|----------------------|------|
| category_id | category_id | 同（引用同一预置分类表） |
| name/image_url/virtual_price/calories/tags | 同 | 同 |
| allergens/allergen_status/spice_level | 同 | 同 |
| status (ON_SALE/OFF_SALE) | 同 | 同 |
| — | family_id | 新增（家庭归属） |
| — | visibility | 新增（PRIVATE/PUBLIC 预留） |
| — | version | 新增（乐观锁） |

---

## 3. 现有表变更：life_menu_daily

### 3.1 变更 DDL（执行文件 `growth-planet/sql/v002_menu_dish_ref.sql`）

```sql
-- 变更 1：新增 version 乐观锁列（C-03）
ALTER TABLE life_menu_daily
  ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER status;

-- 变更 2：替换 chk_menu_dishes 约束，适配对象数组
-- 说明：MySQL CHECK 仅能校验 JSON 为数组且长度 1-50；
--       元素结构（{type,id}）由应用层 DishRef 反序列化 + Service 校验保证
--       （与现状一致：现状也只校验数组长度，元素是 Long 由应用层保证）
ALTER TABLE life_menu_daily
  DROP CHECK chk_menu_dishes;

ALTER TABLE life_menu_daily
  ADD CONSTRAINT chk_menu_dishes CHECK (
    JSON_TYPE(dish_ids) = 'ARRAY'
    AND JSON_LENGTH(dish_ids) BETWEEN 1 AND 50
  );
```

### 3.2 dish_ids 语义对照

| 项 | 变更前 | 变更后 |
|----|--------|--------|
| JSON 结构 | `[1, 2, 3]`（纯 Long 数组） | `[{"type":"PRESET","id":1},{"type":"FAMILY","id":101}]`（对象数组） |
| type 取值 | — | `PRESET`（查 life_dish）/ `FAMILY`（查 life_family_dish） |
| 长度约束 | 1-50 | 1-50（不变） |
| 元素校验 | 应用层保证为 Long | 应用层保证为 `{type,id}` 且 type 合法、id 正数 |

### 3.3 约束说明

- `chk_menu_dishes` 仍只校验「数组 + 长度 1-50」，**不校验元素结构**——这与现状一致（现状元素是 Long 也由应用层保证）。
- 元素结构由 `DishRef` 反序列化 + `CatalogService` 发布校验双层保证：反序列化失败→E-003 数据异常跳过；发布校验→type 合法 + id 正数 + 菜品存在且 ON_SALE。

### 3.4 变更 5：扩展 `uk_confirm_dish` 唯一键纳入 source_type（C-01 派生 · D-06 · 编码中发现）

**问题背景**：`life_dish`（预置）与 `life_family_dish`（家庭私有）使用**独立自增序列**，二者 `id` 各自从 1 开始。编码集成测试时发现：同一 confirm 内同时点「预置菜 #1」与「家庭菜 #1」时，原唯一键 `uk_confirm_dish (confirm_id, dish_id)` 会判定为重复，触发并发冲突（E-007 / DuplicateKeyException）。但二者 `source_type` 不同，本质上是两道不同菜品，应允许并存。

**变更 DDL（同文件 `v002_menu_dish_ref.sql` 变更 5 块，建表后执行）**：

```sql
-- 预置与家庭菜品自增序列独立，需 source_type 参与唯一约束
ALTER TABLE life_menu_item
  DROP INDEX uk_confirm_dish,
  ADD UNIQUE KEY uk_confirm_dish (confirm_id, dish_id, source_type);
```

**说明**：
- `source_type` 列由 §3.x 变更 4（实际落于 `v002_menu_dish_ref.sql`）新增（`VARCHAR(20) NOT NULL DEFAULT 'PRESET'`），本变更在其之后执行，列已存在。
- 业务规则层 `ConfirmService.submit` 已通过 `validateOrder` 的 `requested` 去重（同一 `(type,id)` 不允许出现两次），因此 `(confirm_id, dish_id, source_type)` 唯一键与业务规则一致，不会误拦合法请求。
- 仅影响「同一 confirm 混合点单」场景；纯预置或纯家庭点单行为不变（source_type 恒为该类型）。

---

## 4. 数据迁移脚本（幂等可重跑）

### 4.1 迁移脚本（同文件 `v002_menu_dish_ref.sql` 末尾）

```sql
-- 迁移：将历史 dish_ids 纯 ID 数组 → 对象数组 [{type:"PRESET", id:N}]
-- 幂等判断：首个元素无 .type 路径 → 视为未迁移的纯 ID 数组
-- 要求 MySQL 8.0.14+（JSON_TABLE / JSON_ARRAYAGG）
-- 执行前务必备份 life_menu_daily

UPDATE life_menu_daily target
JOIN (
  SELECT t.id,
         (
           SELECT JSON_ARRAYAGG(JSON_OBJECT('type', 'PRESET', 'id', jt.dish_id))
           FROM JSON_TABLE(
             (SELECT dish_ids FROM life_menu_daily WHERE id = t.id),
             '$[*]' COLUMNS(dish_id BIGINT PATH '$')
           ) AS jt
         ) AS new_dish_ids
  FROM life_menu_daily t
  WHERE t.delete_at = 0
    AND JSON_TYPE(t.dish_ids) = 'ARRAY'
    AND JSON_LENGTH(t.dish_ids) > 0
    AND JSON_EXTRACT(t.dish_ids, '$[0].type') IS NULL   -- 首元素无 type → 纯 ID 数组（未迁移）
) src ON target.id = src.id
SET target.dish_ids = src.new_dish_ids
WHERE target.delete_at = 0
  AND JSON_EXTRACT(target.dish_ids, '$[0].type') IS NULL;
```

### 4.2 迁移验证查询

```sql
-- 迁移后校验：不应再有「首元素无 type」的记录
SELECT COUNT(*) AS unmigrated
FROM life_menu_daily
WHERE delete_at = 0
  AND JSON_TYPE(dish_ids) = 'ARRAY'
  AND JSON_LENGTH(dish_ids) > 0
  AND JSON_EXTRACT(dish_ids, '$[0].type') IS NULL;
-- 期望：0

-- 抽样核对：迁移后结构应为对象数组
SELECT id, dish_ids FROM life_menu_daily
WHERE delete_at = 0
ORDER BY id DESC LIMIT 5;
```

### 4.3 迁移注意

- **执行前必须备份**：`CREATE TABLE life_menu_daily_bak_20260920 AS SELECT * FROM life_menu_daily;`
- **MySQL 版本要求**：JSON_TABLE 需 8.0.14+；执行前 `SELECT VERSION();` 确认
- **空数组跳过**：`JSON_LENGTH=0` 的记录不迁移（约束要求 1-50，空数组本就非法，但存量可能存在脏数据，跳过不阻断）
- **软删除记录**：`delete_at<>0` 的记录也一并迁移（保持结构统一，便于历史对账）

---

## 5. 代码影响范围

### 5.1 实体层

| 文件 | 操作 | 变更内容 |
|------|------|----------|
| `entity/MenuDaily.java` | 修改 | `dishIds` 类型 `List<Long>` → `List<DishRef>`；新增 `version` 字段（手写乐观锁，不用 @Version 注解，沿用家务模块 transition() 约定） |
| `entity/DishRef.java` | **新建** | 值对象：`String type`（PRESET/FAMILY）+ `Long id`；含 type 合法性校验方法 |
| `entity/FamilyDish.java` | **新建** | 家庭私有菜品实体，`@TableName("life_family_dish")`，复用 Dish 字段 + familyId/visibility/version；JacksonTypeHandler 处理 allergens |

### 5.2 DTO 层

| 文件 | 操作 | 变更内容 |
|------|------|----------|
| `dto/request/MenuDailyReq.java` | 修改 | `dishIds` 类型 `List<Long>` → `List<DishRef>`；校验：`@Size(min=1,max=50)` + 每个 DishRef 的 type/id 非空 |
| `dto/request/FamilyDishReq.java` | **新建** | 录入/编辑请求，复用 DishReq 字段（categoryId/name/imageUrl/virtualPrice/calories/tags/allergens/allergenStatus/spiceLevel）；**不含** familyId/visibility/status（后端派生）；编辑场景不带 status（上下架独立） |
| `dto/response/FamilyDishResp.java` | **新建** | 私有菜品响应，复用 DishResp 字段 + familyId/sourceType 标记 |
| `dto/response/MenuMaintenanceResp.java` | 修改 | dishes 列表项可带 `sourceType`（PRESET/FAMILY）标记，便于编排页区分来源；missingDishIds 保留 |
| `dto/response/MenuDailyResp.java` | 修改 | MenuDishResp 可带 `sourceType` 标记（儿童端不展示，仅家长编排用） |

### 5.3 Mapper 层

| 文件 | 操作 | 变更内容 |
|------|------|----------|
| `mapper/FamilyDishMapper.java` | **新建** | 继承 BaseMapper<FamilyDish> |

### 5.4 Service 层（核心变更）

| 文件 | 操作 | 变更内容 |
|------|------|----------|
| `service/CatalogService.java` | 修改 | **4 处 dish_ids 读写逻辑改造**（见 5.4.1）+ 新增家庭菜品 CRUD/上下架/列表方法（或拆分到新 Service） |
| `service/FamilyDishService.java` | **新建（建议）** | 家庭私有菜品业务：create/list/detail/update/changeStatus/delete；归属校验 + 审计 + 乐观锁；与 CatalogService 解耦 |

#### 5.4.1 CatalogService 的 4 处 dish_ids 改造点

| 方法 | 现状（只查 life_dish） | 变更后（按 type 分流） |
|------|----------------------|----------------------|
| `upsertMenu` | `lockDishes(proposed.getDishIds())` 查 life_dish | 按 DishRef.type 分流：PRESET 查 life_dish、FAMILY 查 life_family_dish；均校验 ON_SALE + 家庭归属（FAMILY 需校验 family_id 一致） |
| `getFamilyMenu` | `lockVisibleDishes(menu.getDishIds())` 查 life_dish | 按 type 分流查询，合并结果；missing 标记保留 |
| `daily`（儿童/家长查菜单） | `lockVisibleDishes(menu.getDishIds())` 查 life_dish | 按 type 分流查询；FAMILY 菜品同样过 safetyStatus 校验（过敏原/辣度） |
| `validateOrder`（儿童点单） | `lockDishes(requested)` + `menu.getDishIds().containsAll(requested)` | OrderLineReq 需带 DishRef（type+id）；按 type 分流加锁；containsAll 改为 DishRef 级匹配 |

**关键**：`validateOrder` 中 `OrderLineReq` 也需从 `dishId: Long` 改为 `dishRef: {type,id}`，否则无法区分点的是预置菜还是家庭菜。

### 5.5 Controller 层

| 文件 | 操作 | 变更内容 |
|------|------|----------|
| `controller/MenuController.java` | 修改 | 家长菜单接口适配对象数组 dish_ids；发布接口校验 type 合法性 |
| `controller/FamilyDishController.java` | **新建** | `/api/parent/family-dish`：POST 新增、GET 列表、GET /{id} 详情、PUT /{id} 编辑、POST /{id}/status 上下架、DELETE /{id} 删除；@RequireRole("PARENT") |

### 5.6 测试层

| 文件 | 操作 | 变更内容 |
|------|------|----------|
| `CatalogFlowIT.java` | 修改 | dish_ids 断言/构造改为对象数组；补家庭菜品混合菜单用例 |
| `Sprint234MigrationIT.java` | 修改 | 补 dish_ids 迁移用例（纯数组→对象数组，幂等重跑） |
| `FamilyDishFlowIT.java` | **新建** | 私有菜品 CRUD + 上下架 + 删除有引用二次确认 + 跨家庭隔离 + 菜单混合编排 + 私有菜品点单扣款 |

### 5.7 配置层

| 文件 | 操作 | 变更内容 |
|------|------|----------|
| `pom.xml` testResources | 修改 | 补 `v002_family_dish.sql` / `v002_menu_dish_ref.sql`（沿用 v002 教训：必须显式包含 v*.sql） |
| `application-test.yml` schema-locations | 修改 | 补 `v002_family_dish.sql` + `v002_menu_dish_ref.sql`（否则集成测试不建新表/不迁约束） |

### 5.8 小程序前端

| 范围 | 操作 | 说明 |
|------|------|------|
| `pages/dish-manage/*`（我的菜品） | **新建** | 列表/新增/编辑/上下架/删除页（F-01~F-05） |
| `pages/menu/index.*`（菜单编排） | 修改 | 双 Tab（家庭/预置）混合选择；dishIds 提交结构改对象数组（F-07~F-09） |
| `tests/pages.test.js` | 修改 | 补家长菜品管理 + 混合编排测试 |

---

## 6. 回滚方案

### 6.1 回滚顺序

1. 下线小程序「我的菜品」入口（前端开关）
2. 后端切回旧 dish_ids 读写逻辑（纯 Long 数组，临时忽略 FAMILY 引用）
3. 执行反向数据迁移（见 6.2）
4. 回滚 DDL（见 6.3）
5. 删除 `life_family_dish` 表

### 6.2 反向数据迁移脚本

```sql
-- 对象数组 → 纯 ID 数组（仅保留 PRESET 项，FAMILY 项丢弃）
-- 仅回滚时使用，执行前务必备份
UPDATE life_menu_daily target
JOIN (
  SELECT t.id,
         (
           SELECT JSON_ARRAYAGG(jt.dish_id)
           FROM JSON_TABLE(
             (SELECT dish_ids FROM life_menu_daily WHERE id = t.id),
             '$[*]' COLUMNS(dish_id BIGINT PATH '$.id', dtyp VARCHAR(10) PATH '$.type')
           ) AS jt
           WHERE jt.dtyp = 'PRESET'
         ) AS new_dish_ids
  FROM life_menu_daily t
  WHERE t.delete_at = 0
    AND JSON_TYPE(t.dish_ids) = 'ARRAY'
    AND JSON_LENGTH(t.dish_ids) > 0
    AND JSON_EXTRACT(t.dish_ids, '$[0].type') IS NOT NULL  -- 已迁移的对象数组
) src ON target.id = src.id
SET target.dish_ids = IF(src.new_dish_ids IS NULL, JSON_ARRAY(), src.new_dish_ids)
WHERE target.delete_at = 0
  AND JSON_EXTRACT(target.dish_ids, '$[0].type') IS NOT NULL;
-- 注意：回滚后若某菜单仅含 FAMILY 菜品，dish_ids 变空数组，需手工清理或置 delete_at
```

### 6.3 回滚 DDL

```sql
-- 1) 撤销 uk_confirm_dish 扩展：先删 3 列唯一键，再建回 2 列唯一键
ALTER TABLE life_menu_item
  DROP INDEX uk_confirm_dish,
  ADD UNIQUE KEY uk_confirm_dish (confirm_id, dish_id);

-- 2) 撤销 life_menu_item.source_type 列（变更 4 新增）
ALTER TABLE life_menu_item
  DROP COLUMN source_type;

-- 3) 撤销 life_menu_daily 变更（version 列 + 约束替换）
ALTER TABLE life_menu_daily
  DROP COLUMN version,
  DROP CHECK chk_menu_dishes;

ALTER TABLE life_menu_daily
  ADD CONSTRAINT chk_menu_dishes CHECK (
    JSON_TYPE(dish_ids) = 'ARRAY'
    AND JSON_LENGTH(dish_ids) BETWEEN 1 AND 50
  );

DROP TABLE IF EXISTS life_family_dish;
```

### 6.4 回滚点清单

| 回滚点 | 操作 | 可逆性 |
|--------|------|--------|
| R-01 | 前端开关关闭「我的菜品」入口 | 立即生效 |
| R-02 | 后端切旧读写逻辑 | 需部署 |
| R-03 | 反向数据迁移 | 需备份验证 |
| R-04 | DDL 回滚 | 需停机窗口 |
| R-05 | 删除 life_family_dish | 数据丢失（仅回滚末步） |

---

## 7. 验证清单

### 7.1 DDL 验证

- [ ] `life_family_dish` 建表成功，外键/索引/约束齐全
- [ ] `life_menu_daily` 新增 `version` 列，默认 0
- [ ] `chk_menu_dishes` 约束替换成功
- [ ] `life_menu_item` 新增 `source_type` 列（NOT NULL DEFAULT 'PRESET'）
- [ ] `uk_confirm_dish` 已扩展为 `(confirm_id, dish_id, source_type)`（D-06，混合点单不误判重复）

### 7.2 数据迁移验证

- [ ] 备份表 `life_menu_daily_bak_20260920` 已创建
- [ ] 迁移脚本执行成功，`unmigrated` 计数为 0
- [ ] 抽样核对对象数组结构正确
- [ ] 迁移脚本**重跑幂等**（再执行一次无变化）

### 7.3 代码验证

- [ ] `mvn -o -q compile` + `test-compile` EXIT=0
- [ ] 集成测试命令：
      `DOCKER_HOST=unix:///Users/zhouwei/.colima/default/docker.sock DOCKER_API_VERSION=1.54 TESTCONTAINERS_RYUK_DISABLED=true mvn -o test -Dtest="*IT" -DargLine="-Dapi.version=1.54"`
- [ ] `FamilyDishFlowIT` 全绿：CRUD + 上下架 + 删除有引用确认 + 跨家庭隔离 + 混合编排 + 私有菜品点单扣款
- [ ] `CatalogFlowIT` 全绿：对象数组 dish_ids 读写 + 无效引用清理
- [ ] `Sprint234MigrationIT` 全绿：迁移幂等
- [ ] 小程序 `node --test tests/*.test.js` 全绿 + `check.mjs`（含 `--native`）PASS

### 7.4 安全验证

- [ ] 客户端传 `familyId`/`visibility` 被后端忽略（用鉴权派生）
- [ ] 跨家庭查询私有菜返回空（隔离）
- [ ] 越权操作返回统一业务错误码（E-009）

---

## 8. 执行顺序（落地步骤）

| 步骤 | 内容 | 前置 |
|------|------|------|
| S1 | DDL：建 `v002_family_dish.sql` + `v002_menu_dish_ref.sql`（含迁移脚本） | 本文档确认 |
| S2 | 后端：FamilyDish 实体/Mapper/Service/Controller + DTO | S1 |
| S3 | 后端：CatalogService 4 处 dish_ids 改造 + OrderLineReq 适配 | S1 |
| S4 | 后端：pom.xml + application-test.yml 补 v002 SQL | S1 |
| S5 | 后端：集成测试 FamilyDishFlowIT + 改 CatalogFlowIT/Sprint234MigrationIT | S2,S3,S4 |
| S6 | 小程序：dish-manage 页 + menu 编排页改造 + 测试 | S2,S3 |
| S7 | 联调 + 验证清单全绿 | S5,S6 |

> **注**：每步完成后验证再进入下一步；S1 DDL 变更需在独立窗口执行并备份。

---

## 9. 待用户确认

本文档为编码前变更文档。请确认以下两点后进入编码：

1. **DDL 变更方案**（life_family_dish 新表 + life_menu_daily 加 version 列 + dish_ids 约束替换 + 数据迁移）是否认可？
2. **代码影响范围**（尤其 OrderLineReq 从 dishId:Long 改为 dishRef:{type,id}，涉及点单链路改造）是否接受？

确认后按 §8 执行顺序编码，每步验证再推进。
