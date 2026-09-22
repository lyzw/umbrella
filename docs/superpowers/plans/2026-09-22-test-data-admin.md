# 测试数据管理后台 Implementation Plan

> **For agentic workers:** 按任务顺序实施并逐项验证。用户未要求 Git 操作，执行本计划时不得自行创建分支、提交或推送。

**Goal:** 建设一套仅在 `dev/test` 环境启用的测试数据管理后台，让测试人员能够按场景创建、定位、复用和安全清理合成数据，同时保留操作审计并避免直接修改业务表破坏业务约束。

**Architecture:** 后端继续使用现有 Spring Boot 单体和 JWT/`ADMIN` 角色模型，新增受双重开关保护的 `/api/dev/admin/**` 管理接口。测试数据以“数据集”为管理边界，创建过程复用现有业务 Service，并区分登记数据集自有资源与只读引用资源；清理过程只允许针对已登记的自有资源执行显式、逆依赖顺序的物理删除。管理端作为独立 Vue 应用，只调用受控接口，不提供任意 SQL 和通用表编辑能力。

**Tech Stack:** Java 21、Spring Boot 4.1.1、MyBatis-Plus、MySQL 8.0、Redis 7.2、JWT、JUnit 5、Testcontainers；管理端推荐 Vue 3 + Vite + TypeScript + Element Plus + Axios + Vitest。新增前端依赖须在实施前单独确认；若要求零新增 UI 依赖，可退化为 Vue 3 + 原生 CSS。

## Global Constraints

- 遵循现有 Controller → Service → Mapper 分层、统一 `Result` 响应、`BizException` 错误处理和 `AuditService` 审计方式。
- 仅 `dev/test` profile 且 `test-data-admin.enabled=true` 时加载测试数据后台；`prod` 必须启动失败或保持完全不可用。
- 默认关闭后台，不在仓库提交访问密钥；访问密钥仅从 `TEST_DATA_ADMIN_ACCESS_KEY` 注入，长度至少 32 字符。
- 所有业务样本必须使用可识别的合成标识，禁止录入真实儿童、学校、手机号、openid 或其他个人信息。
- 不建设通用 SQL 控制台、不允许按任意业务 ID 删除、不允许前端直接编辑状态机字段。
- 创建数据优先复用现有 Service，确保家庭绑定、同意、档案、钱包、菜单、任务、打卡等数据符合现有业务约束。
- 清理仅针对 `sys_test_dataset_resource` 中 `OWNED` 资源或由这些根资源可确定归属的数据；`REFERENCED` 资源永不由数据集清理，不得影响其他数据集和人工数据。
- 管理员 token 和测试用户 token 不持久化到数据库或日志；需要时即时签发。
- 数据集创建、清理、会话签发、失败重试都必须写审计；日志只记录数据集 ID、场景、数量、耗时和结果，不记录访问密钥、token、档案正文。
- 新增表放在独立开发环境脚本中，不混入生产 Sprint 初始化链路；生产迁移不得执行该脚本。
- 不执行 Git 操作，不连接或修改现有业务数据库；实施和验证使用独立开发库及 Testcontainers。

---

## 1. 现状结论

### 1.1 已有能力

- 后端已有 `ADMIN` 角色和 `/api/admin/dish-category`、`/api/admin/dish`、`/api/admin/menu-daily` 等运营接口。
- 鉴权由 `JwtInterceptor` 和 `@RequireRole` 完成，每次请求重新读取账号、角色、状态和 `token_version`。
- `DevController`/`DevBindingService` 已提供儿童一键绑定家长的合成数据能力，并通过 `@Profile({"dev","test"})` 隔离。
- `BaseIT` 使用 MySQL/Redis Testcontainers，测试数据主要通过接口和测试代码临时构造。
- `sys_audit_log` 已支持成功、拒绝、失败和 requestId 审计。

### 1.2 主要缺口

- 没有受控的 ADMIN 登录/初始化链路；现有集成测试直接插入 ADMIN 用户并自行签发 token。
- 没有数据集概念，无法判断一批测试数据由谁创建、属于哪个场景、能否整体清理。
- 测试夹具散落在多个 `*IT` 中，创建流程重复，不能给人工测试直接复用。
- 没有可视化管理端，菜品、学校菜单和业务场景仍需调用接口或手工操作数据库。
- 没有覆盖全部业务表的清理策略；直接删用户/家庭会留下钱包、任务、菜单、通知、审批等孤立数据。
- 没有防止测试后台误开到生产的专项回归。

## 2. 方案对比

| 方案 | 说明 | 优点 | 风险/代价 | 结论 |
| --- | --- | --- | --- | --- |
| A. 通用数据库 CRUD 后台 | 按表展示并允许新增、修改、删除 | 开发快、覆盖面广 | 绕过授权、事务、乐观锁、幂等和状态机，极易制造无效数据 | 不采用 |
| B. 仅维护 SQL seed 脚本 | 每个场景提供插入/删除 SQL | 无需管理端 | 维护成本高，ID/FK/状态演进脆弱，人工执行风险高 | 仅保留为灾备和目录种子工具 |
| C. 场景化数据集后台 | 业务 Service 生成，登记数据集资源，受控查询和整体清理 | 数据有效、可审计、可重复、便于人工测试 | 需要新增数据集模型、清理器和管理端 | 推荐 |

## 3. 目标范围

### 3.1 P0 必做

1. 测试管理员受控登录。
2. 数据集列表、详情、创建、清理。
3. 三个内置场景：基础家庭、点餐钱包、完整成长。
4. 测试账号登录码展示和临时 token 签发。
5. 菜品分类、预置菜品、学校菜单维护，复用已有 ADMIN API。
6. 数据集操作审计、失败状态和重试提示。
7. Testcontainers 全链路与生产禁用回归。

### 3.2 P1 建议

1. 数据集克隆与基于 seed 的可重复生成。
2. 数据快照导出/导入，仅导出合成配置和业务摘要，不导出 JWT。
3. 按家庭、儿童、场景、状态、创建人和时间检索。
4. 数据集过期时间和一键清理过期数据。
5. 场景版本管理，业务结构升级后明确旧数据集是否可继续使用。

### 3.3 不在本期

- 生产运营后台、真实管理员账号体系、密码找回、MFA、组织权限。
- 通用 SQL 执行器和任意业务表编辑。
- 真实数据脱敏复制。
- 自动修改合规闸门、真实微信账号或生产配置。
- 删除真实隐私工单、审计证据或备份数据。

## 4. 数据模型

### 4.1 开发环境迁移脚本

**Create:** `growth-planet/sql/dev/v001_test_data_admin.sql`

```sql
CREATE TABLE sys_test_dataset (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dataset_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  name VARCHAR(64) NOT NULL,
  scenario_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  scenario_version INT NOT NULL DEFAULT 1,
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  seed BIGINT NOT NULL,
  config_json JSON NOT NULL,
  summary_json JSON NOT NULL,
  creator_id BIGINT NOT NULL,
  request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  failure_code VARCHAR(32) DEFAULT NULL,
  failure_message VARCHAR(255) DEFAULT NULL,
  version INT NOT NULL DEFAULT 0,
  expires_at BIGINT DEFAULT NULL,
  purged_at BIGINT DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_test_dataset_code (dataset_code),
  UNIQUE KEY uk_test_dataset_request (creator_id, request_key),
  KEY idx_test_dataset_status (status, expires_at, id),
  KEY idx_test_dataset_creator (creator_id, create_time),
  CONSTRAINT chk_test_dataset_status CHECK (
    status IN ('CREATING', 'READY', 'FAILED', 'DELETING', 'DELETED')
  ),
  CONSTRAINT chk_test_dataset_json CHECK (
    JSON_TYPE(config_json) = 'OBJECT' AND JSON_TYPE(summary_json) = 'OBJECT'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开发测试数据集登记';

CREATE TABLE sys_test_dataset_resource (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dataset_id BIGINT NOT NULL,
  resource_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  resource_id BIGINT NOT NULL,
  ownership_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  resource_key VARCHAR(128) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_test_dataset_resource (dataset_id, resource_type, resource_id),
  KEY idx_test_resource_lookup (resource_type, resource_id, ownership_type),
  CONSTRAINT fk_test_resource_dataset
    FOREIGN KEY (dataset_id) REFERENCES sys_test_dataset (id),
  CONSTRAINT chk_test_resource_ownership CHECK (
    ownership_type IN ('OWNED', 'REFERENCED')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试数据集自有资源与引用资源登记';
```

### 4.2 迁移注意事项

- 此脚本只允许在本地开发库、共享测试库和 Testcontainers 中执行。
- `application-test.yml` 将脚本追加到 schema 初始化末尾。
- `application-dev.yml` 不自动建表，本地开发者需在独立开发库人工执行一次。
- 不加入生产迁移目录，不允许生产发布流程自动扫描 `sql/dev/`。
- 两张表不改变既有业务表，不需要回填。
- 回滚前必须确保测试后台已关闭；先删除 `sys_test_dataset_resource`，再删除 `sys_test_dataset`。
- 数据集清理不会删除登记行，最终状态保留为 `DELETED`，便于审计和幂等重试。
- P0 场景创建的家庭、账号、分类、菜品和菜单均使用数据集唯一标识并登记为 `OWNED`；人工预置目录和系统勋章定义只能登记为 `REFERENCED`。

## 5. 后端接口契约

统一响应继续使用 `{code,data,message,requestId}`，业务 ID 保持十进制字符串。

### 5.1 登录

`POST /api/dev/admin/auth/login`

```json
{
  "accessKey": "local-only-access-key-with-at-least-32-characters"
}
```

```json
{
  "code": 0,
  "data": {
    "token": "jwt-token",
    "role": "ADMIN",
    "expiresIn": 1800
  },
  "message": "success",
  "requestId": "request-id"
}
```

规则：

- 接口仅在 `dev/test` profile 注册。
- `test-data-admin.enabled` 必须为 `true`。
- 配置密钥不足 32 字符时应用启动失败。
- 使用 `MessageDigest.isEqual` 做常量时间比较。
- 不存在 ADMIN 合成账号时创建固定受控账号；已存在但角色或状态异常时拒绝登录，不自动修复。
- 登录失败返回 401，不区分“未启用”和“密钥错误”的外部文案。

### 5.2 数据集列表

`GET /api/dev/admin/datasets?page=1&pageSize=20&status=READY&keyword=menu`

返回字段：

```json
{
  "code": 0,
  "data": {
    "items": [
      {
        "datasetId": "101",
        "datasetCode": "TD-20260922-0001",
        "name": "点餐回归数据",
        "scenarioCode": "MENU_WALLET",
        "scenarioVersion": 1,
        "status": "READY",
        "seed": 202609220001,
        "childCount": 2,
        "createTime": "2026-09-22T10:30:00",
        "expiresAt": 1790037000000,
        "version": 1
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 20
  },
  "message": "success",
  "requestId": "request-id"
}
```

### 5.3 创建数据集

`POST /api/dev/admin/datasets`

请求头：

```http
Idempotency-Key: create-menu-wallet-20260922-01
Authorization: Bearer <admin-token>
```

请求体：

```json
{
  "name": "点餐回归数据",
  "scenarioCode": "MENU_WALLET",
  "seed": 202609220001,
  "childCount": 2,
  "initialBalance": "100.00",
  "menuDays": 7,
  "expiresInHours": 72
}
```

返回：

```json
{
  "code": 0,
  "data": {
    "datasetId": "101",
    "datasetCode": "TD-20260922-0001",
    "status": "READY",
    "parentAccounts": [
      {
        "userId": "201",
        "loginCode": "td_101_parent_1"
      }
    ],
    "childAccounts": [
      {
        "userId": "202",
        "loginCode": "td_101_child_1"
      },
      {
        "userId": "203",
        "loginCode": "td_101_child_2"
      }
    ],
    "familyId": "301",
    "resourceCounts": {
      "users": 3,
      "families": 1,
      "menus": 21,
      "dishes": 8
    },
    "version": 1
  },
  "message": "success",
  "requestId": "request-id"
}
```

校验：

- `name` 1 至 64 字符。
- `scenarioCode` 只能是已注册场景。
- `childCount` 1 至 5。
- `initialBalance` 0.00 至 9999.99，最多两位小数。
- `menuDays` 1 至 14。
- `expiresInHours` 1 至 720。
- 同一创建人和 `Idempotency-Key` 同参返回原数据集；同键不同参返回 409。

### 5.4 数据集详情

`GET /api/dev/admin/datasets/{id}`

详情包含：

- 数据集基本信息、场景版本、状态和失败原因。
- 合成账号登录码、用户 ID、家庭 ID、儿童 ID。
- 分类、菜品、菜单、钱包、任务、打卡、日程、勋章、通知等聚合数量。
- 最近 50 条数据集管理审计。
- 不返回访问密钥、JWT、openid、unionid 和档案安全字段正文。

### 5.5 临时会话

`POST /api/dev/admin/datasets/{id}/sessions`

```json
{
  "userId": "202"
}
```

```json
{
  "code": 0,
  "data": {
    "userId": "202",
    "role": "CHILD",
    "token": "temporary-jwt",
    "expiresIn": 1800
  },
  "message": "success",
  "requestId": "request-id"
}
```

规则：

- 只能为该数据集登记的 `USER` 资源签发。
- 每次按数据库当前状态和家庭关系重新生成，不能恢复已禁用、已删除账号。
- token 不写入数据库、审计详情或应用日志。

### 5.6 清理数据集

`POST /api/dev/admin/datasets/{id}/purge`

```json
{
  "expectedVersion": 1,
  "confirmDatasetCode": "TD-20260922-0001"
}
```

```json
{
  "code": 0,
  "data": {
    "datasetId": "101",
    "status": "DELETED",
    "deletedCounts": {
      "users": 3,
      "families": 1,
      "businessRows": 126
    },
    "version": 3
  },
  "message": "success",
  "requestId": "request-id"
}
```

规则：

- 校验确认码、乐观锁版本和当前状态。
- `DELETED` 重复清理返回原结果；`CREATING`/`DELETING` 返回 409。
- 清理全程锁定数据集行。
- 只删除该数据集登记为 `OWNED` 的业务资源；`REFERENCED` 资源永不删除。
- 若其他未删除数据集仍引用本数据集的 `OWNED` 资源，预检返回 409，要求先清理引用方，禁止静默跳过或转移所有权。
- 所有 `sys_audit_log`、管理操作审计和数据集登记行均保留。

## 6. 场景定义

### 6.1 `BASIC_FAMILY`

- 1 个 PARENT、1 至 5 个 CHILD。
- 1 个家庭，儿童均完成 PENDING → 同意 → BOUND。
- 每个儿童有 COMPLETE 档案和可配置偏好。
- 返回可直接用于小程序登录的合成 code。

验收：

- 家长可查询全部儿童。
- 儿童可查询本人绑定与偏好。
- 任一儿童不能访问其他儿童数据。

### 6.2 `MENU_WALLET`

包含 `BASIC_FAMILY`，并新增：

- 1 个分类、8 个预置菜品，覆盖无过敏原、PEANUT、MILK、EGG、UNKNOWN、OFF_SALE 和不同辣度。
- 当前日起 1 至 14 天的家庭菜单，每天三餐。
- 每个儿童的钱包、额度规则和初始发放流水。
- 至少一个想吃标记、一个待审批确认、一个已完成确认和一个被拒绝确认。

验收：

- 菜单展示可覆盖安全可选、过敏冲突、未知过敏信息、下架缺项。
- 钱包余额与流水守恒。
- 待审批、完成、拒绝状态均可在双端查询。

### 6.3 `GROWTH_FULL`

包含 `MENU_WALLET`，并新增：

- 家务任务、领取、提交、确认和拒绝实例。
- 健康打卡项目、连续打卡记录和勋章。
- 一次、每日、每周三类日程。
- 已读和未读通知。
- 想吃状态 `MARKED`、`ADOPTED`、`COOKED`。

验收：

- 任务奖励只能记账一次。
- 连续打卡和勋章满足现有幂等约束。
- 日程、通知、想吃数据均严格按家庭和儿童隔离。

## 7. 创建与清理事务设计

### 7.1 创建

1. 在独立事务插入 `CREATING` 数据集，写入请求摘要和配置。
2. 主事务按固定顺序创建数据集独享目录、家长、家庭、儿童、同意、绑定、档案和场景业务数据。
3. 每创建一个根资源就以 `OWNED` 写入 `sys_test_dataset_resource`；使用人工预置目录或系统种子时仅以 `REFERENCED` 登记。
4. 业务数据全部成功后计算摘要，将状态改为 `READY` 并递增版本。
5. 主事务失败时回滚全部业务数据；独立事务将数据集改为 `FAILED`，只记录安全错误码和异常类型。
6. 禁止在场景创建中调用会独立提交的批量方法；菜单逐项调用普通 upsert，确保数据集创建保持原子性。

### 7.2 清理顺序

清理器根据登记的 `FAMILY`、`USER`、`DISH_CATEGORY`、`DISH` 等根资源先解析范围，再按以下顺序执行显式物理删除：

1. `sys_notice_subscription`
2. `sys_notice`
3. `sys_privacy_verification`
4. `sys_privacy_request`
5. `life_confirm_approval`
6. `life_menu_item`
7. `life_menu_confirm`
8. `life_allowance_log`
9. `life_allowance_rule`
10. `life_wallet`
11. `usr_child_want_eat`
12. `life_medal_award`
13. `life_chore_streak`
14. `life_chore_instance`
15. `life_chore_task`
16. `life_check_record`
17. `life_check_item`
18. `life_schedule`
19. `life_menu_daily`
20. `life_family_dish`
21. `usr_child_profile`
22. `usr_consent_log`
23. `usr_family_member`
24. `usr_family`
25. 仅删除本数据集 `OWNED` 且无活动数据集引用的 `life_dish`
26. 仅删除本数据集 `OWNED`、无活动数据集引用且无菜品引用的 `life_dish_category`
27. `usr_user`
28. 保留 `sys_audit_log` 全部记录，不更新、不逻辑删除、不物理删除
29. 将数据集状态改为 `DELETED`，写入删除计数和 `purged_at`

注意：

- 清理使用专用 Mapper 的明确 SQL，不调用 MyBatis-Plus 逻辑删除，避免唯一键残留导致相同 seed 无法重建。
- 每个删除语句必须同时带数据集解析出的 family/user/resource 范围，禁止无条件删除。
- “活动数据集引用”仅统计 `CREATING`、`READY`、`FAILED`、`DELETING` 数据集的 `REFERENCED` 记录；已 `DELETED` 的历史登记不阻塞清理。
- 清理前后都统计行数；实际删除数超出预估上限时立即回滚并记录 `FAILED` 审计。
- 新增任何带 `family_id`、`child_id`、`user_id` 或业务外键的表时，必须同步清理分类测试，否则集成测试失败。

## 8. 文件结构

### 8.1 后端新增

```text
growth-planet/
  sql/dev/v001_test_data_admin.sql
  src/main/java/cn/studykid/growthplanet/config/TestDataAdminProperties.java
  src/main/java/cn/studykid/growthplanet/config/TestDataAdminGuard.java
  src/main/java/cn/studykid/growthplanet/controller/TestDataAdminAuthController.java
  src/main/java/cn/studykid/growthplanet/controller/TestDataAdminController.java
  src/main/java/cn/studykid/growthplanet/dto/request/TestAdminLoginReq.java
  src/main/java/cn/studykid/growthplanet/dto/request/TestDatasetCreateReq.java
  src/main/java/cn/studykid/growthplanet/dto/request/TestDatasetPurgeReq.java
  src/main/java/cn/studykid/growthplanet/dto/request/TestSessionReq.java
  src/main/java/cn/studykid/growthplanet/dto/response/TestAdminLoginResp.java
  src/main/java/cn/studykid/growthplanet/dto/response/TestDatasetResp.java
  src/main/java/cn/studykid/growthplanet/dto/response/TestDatasetDetailResp.java
  src/main/java/cn/studykid/growthplanet/dto/response/TestSessionResp.java
  src/main/java/cn/studykid/growthplanet/entity/TestDataset.java
  src/main/java/cn/studykid/growthplanet/entity/TestDatasetResource.java
  src/main/java/cn/studykid/growthplanet/mapper/TestDatasetMapper.java
  src/main/java/cn/studykid/growthplanet/mapper/TestDatasetResourceMapper.java
  src/main/java/cn/studykid/growthplanet/mapper/TestDataCleanupMapper.java
  src/main/java/cn/studykid/growthplanet/service/TestAdminAuthService.java
  src/main/java/cn/studykid/growthplanet/service/TestDatasetService.java
  src/main/java/cn/studykid/growthplanet/service/TestDataScenarioRegistry.java
  src/main/java/cn/studykid/growthplanet/service/TestDataScenario.java
  src/main/java/cn/studykid/growthplanet/service/TestDataCleanupService.java
  src/main/java/cn/studykid/growthplanet/service/scenario/BasicFamilyScenario.java
  src/main/java/cn/studykid/growthplanet/service/scenario/MenuWalletScenario.java
  src/main/java/cn/studykid/growthplanet/service/scenario/GrowthFullScenario.java
  src/test/java/cn/studykid/growthplanet/TestDataAdminFlowIT.java
  src/test/java/cn/studykid/growthplanet/TestDataAdminProductionGuardTest.java
  src/test/java/cn/studykid/growthplanet/TestDataCleanupCoverageIT.java
```

### 8.2 后端修改

- `growth-planet/src/main/resources/application.yml`
  - 增加默认关闭的 `test-data-admin` 配置。
- `growth-planet/src/main/resources/application-dev.yml`
  - 只引用环境变量，不提交默认访问密钥。
- `growth-planet/src/main/resources/application-test.yml`
  - 启用测试后台并加载 `sql/dev/v001_test_data_admin.sql`。
- `growth-planet/src/main/java/cn/studykid/growthplanet/config/WebMvcConfig.java`
  - 仅放行测试管理员登录接口，其他 `/api/dev/admin/**` 继续要求 JWT。
- `growth-planet/src/main/java/cn/studykid/growthplanet/service/impl/DevBindingService.java`
  - 抽取可复用的合成身份/上下文切换能力，保持现有接口行为不变。
- `growth-planet/src/test/java/cn/studykid/growthplanet/BaseIT.java`
  - 增加测试管理员登录和数据集创建辅助方法，不替换现有业务夹具。
- `growth-planet/README.md`
  - 增加启用方式、环境变量、独立开发库要求和生产禁用说明。

### 8.3 管理端新增

```text
growth-planet-admin/
  package.json
  tsconfig.json
  vite.config.ts
  src/main.ts
  src/App.vue
  src/router/index.ts
  src/api/client.ts
  src/api/auth.ts
  src/api/datasets.ts
  src/api/catalog.ts
  src/stores/session.ts
  src/layouts/AdminLayout.vue
  src/views/LoginView.vue
  src/views/DashboardView.vue
  src/views/DatasetListView.vue
  src/views/DatasetCreateView.vue
  src/views/DatasetDetailView.vue
  src/views/DishCategoryView.vue
  src/views/DishView.vue
  src/views/SchoolMenuView.vue
  src/components/ConfirmDatasetCodeDialog.vue
  src/components/StatusTag.vue
  src/components/CopyValueButton.vue
  src/styles/base.css
  src/__tests__/dataset-form.spec.ts
  src/__tests__/session.spec.ts
  e2e/test-data-admin.spec.ts
```

管理端不保存 access key；JWT 默认存 `sessionStorage`，关闭浏览器会话后失效。

## 9. 实施任务

### Task 1: 环境隔离与管理员登录

**Files:** `TestDataAdminProperties`、`TestDataAdminGuard`、登录 Controller/Service/DTO、`WebMvcConfig`、三份 application 配置。

**Interfaces:**

```java
public TestAdminLoginResp login(TestAdminLoginReq request);
public void requireEnabled();
```

- [ ] 编写配置绑定和启动保护单元测试，覆盖默认关闭、短密钥、prod 启用、dev 正常启用。
- [ ] 实现 `@Profile({"dev","test"})` Controller 和 Service。
- [ ] 创建或读取固定合成 ADMIN 账号，校验角色、状态和逻辑删除标记。
- [ ] 签发 30 分钟 JWT，写 `TEST_ADMIN_LOGIN` 审计。
- [ ] 验证无 token 不能访问数据集接口，PARENT/CHILD token 返回 403。

### Task 2: 数据集登记与幂等状态机

**Files:** 开发 SQL、Entity、Mapper、创建/列表/详情 DTO、`TestDatasetService`。

**Interfaces:**

```java
public TestDatasetResp create(TestDatasetCreateReq request, String idempotencyKey);
public PageResp<TestDatasetResp> page(int page, int pageSize, String status, String keyword);
public TestDatasetDetailResp detail(Long datasetId);
```

- [ ] 编写 SQL 初始化与 Entity 映射测试。
- [ ] 实现参数校验、分页上限、场景白名单、请求摘要和同键同参幂等。
- [ ] 使用数据集行乐观锁管理 `CREATING/READY/FAILED/DELETING/DELETED`。
- [ ] 对失败消息做安全截断，只保留异常类型和用户可操作提示。
- [ ] 添加 `TEST_DATASET_CREATE`、`TEST_DATASET_QUERY` 审计。

### Task 3: 合成身份与基础家庭场景

**Files:** `TestDataScenario`、Registry、`BasicFamilyScenario`、`DevBindingService`。

**Interfaces:**

```java
public interface TestDataScenario {
    String code();
    int version();
    TestScenarioResult create(TestScenarioContext context, TestDatasetCreateReq request);
}
```

- [ ] 从 `DevBindingService` 抽取合成账号创建、`UserContext` 切换和恢复逻辑。
- [ ] 所有上下文切换使用 `try/finally`，异常时不得泄漏前一个用户上下文。
- [ ] 通过现有 Auth/Family/Compliance Service 完成家长、家庭、儿童、同意、绑定和档案。
- [ ] 登记 USER、FAMILY 和必要的根资源。
- [ ] 集成测试验证家庭隔离、同意有效、档案完整和合成 code 可重新登录。

### Task 4: 点餐钱包场景

**Files:** `MenuWalletScenario`、场景结果 DTO、相关测试。

- [ ] 通过现有 Catalog/Wallet/Confirm Service 创建分类、菜品、菜单、余额、额度和审批状态。
- [ ] 使用固定 seed 生成稳定但唯一的名称、requestKey 和日期分布。
- [ ] 不调用 `REQUIRES_NEW` 批量发布路径，确保整体失败可回滚。
- [ ] 验证钱包余额、流水、确认快照和菜单菜品引用一致。
- [ ] 验证 UNKNOWN、过敏冲突、OFF_SALE 和缺失引用的展示边界。

### Task 5: 完整成长场景

**Files:** `GrowthFullScenario` 和相关测试。

- [ ] 通过 Chore/Check/Schedule/Medal/Notice Service 创建完整状态数据。
- [ ] 构造领取、提交、确认、拒绝和奖励幂等样本。
- [ ] 构造连续打卡和勋章边界样本。
- [ ] 构造三类日程与已读/未读通知。
- [ ] 验证跨家庭不可见、奖励不重复、通知不串用户。

### Task 6: 受控清理

**Files:** `TestDataCleanupMapper`、`TestDataCleanupService`、Purge DTO/响应。

**Interfaces:**

```java
public TestDatasetResp purge(Long datasetId, TestDatasetPurgeReq request);
public TestDataDeletionPlan plan(Long datasetId);
```

- [ ] 先实现只读删除计划，返回每张表的预计影响行数。
- [ ] 编写逆依赖删除 SQL，每条 SQL 都要求数据集范围参数。
- [ ] 增加最大删除行数保护和事务回滚测试。
- [ ] 验证重复清理幂等、错误确认码拒绝、并发清理仅一个成功。
- [ ] 验证数据集 A 清理后数据集 B 和人工插入的哨兵数据保持不变。
- [ ] 增加 `information_schema` 覆盖测试，发现新的家庭/儿童范围表时强制补清理策略。

### Task 7: 临时会话与可观测性

**Files:** Session DTO、数据集 Controller/Service、README。

- [ ] 只允许为数据集登记账号签发 token。
- [ ] 禁止给 DISABLED、已逻辑删除或不属于数据集的用户签发。
- [ ] 增加结构化日志字段：`requestId`、`datasetId`、`scenarioCode`、`status`、`durationMs`、`resourceCount`。
- [ ] 确认日志和审计不包含 access key、JWT、openid、unionid、儿童档案正文。
- [ ] 文档化 curl 示例、配置和故障排查步骤。

### Task 8: 管理端基础框架

**Files:** `growth-planet-admin` 基础工程、路由、API client、会话 store、布局。

- [ ] 初始化 Vue 3 + Vite + TypeScript；依赖清单先评审再安装。
- [ ] 实现登录页、401 自动清会话、统一错误提示和 requestId 展示。
- [ ] 实现安静、紧凑的工作台布局：侧边导航、顶部环境标识、内容区。
- [ ] 明确显示当前为合成测试环境，不展示营销型首页。
- [ ] Vitest 覆盖登录、token 生命周期和统一响应解析。

### Task 9: 数据集页面

**Files:** Dataset 列表、创建、详情、确认弹窗和状态组件。

- [ ] 列表支持状态、关键字、分页和刷新，不允许直接行内改状态。
- [ ] 创建页按场景显示合法参数，金额、数量、天数做前后端双校验。
- [ ] 详情页展示账号 code、关键 ID、资源计数、失败原因和审计。
- [ ] 清理必须输入完整 `datasetCode` 并展示预计删除数量。
- [ ] token 通过显式按钮临时签发，默认隐藏并支持一键复制。

### Task 10: 目录与学校菜单页面

**Files:** Category、Dish、SchoolMenu 视图和 `catalog.ts`。

- [ ] 复用现有 ADMIN API，不另建重复接口。
- [ ] 菜品表单完整覆盖价格、热量、标签、过敏原状态、辣度和上下架状态。
- [ ] 学校菜单按学校、日期、餐段维护，只允许选择当前可用菜品。
- [ ] 删除菜品前展示历史引用风险，沿用后端逻辑删除行为。
- [ ] 增加表单校验和 API 错误状态测试。

### Task 11: 端到端与发布保护

**Files:** 后端三个专项测试、管理端 E2E、README。

- [ ] 执行后端单元测试：

```bash
/opt/homebrew/bin/mvn -s mvn-settings.xml test
```

- [ ] 执行 Testcontainers 集成测试：

```bash
export DOCKER_HOST="$(docker context inspect --format '{{.Endpoints.docker.Host}}')"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
/opt/homebrew/bin/mvn -s mvn-settings.xml -Dapi.version=1.44 test -Pintegration
```

- [ ] 执行管理端单元和构建：

```bash
npm test
npm run build
```

- [ ] 执行浏览器 E2E：登录 → 创建 `MENU_WALLET` → 查看详情 → 签发儿童 token → 调用业务查询 → 清理 → 确认业务数据消失。
- [ ] 启动 `prod` profile 并显式设置错误的测试后台开关，验证应用拒绝启动或接口不存在。
- [ ] 使用桌面和移动宽度截图检查表格、表单、弹窗无溢出和遮挡；管理后台以桌面为主，移动端只保证只读和紧急清理可用。

## 10. 验收标准

### 功能

- 测试人员无需访问数据库即可创建三类完整合成场景。
- 每个数据集都能追溯创建人、场景版本、参数摘要、资源数量和当前状态。
- 数据集账号可直接用于小程序合成登录或获取临时 API token。
- 清理后除不可变 `sys_audit_log` 和数据集登记记录外，不存在该数据集家庭、用户和业务派生数据，其他数据不受影响。
- 菜品、分类和学校菜单可通过管理端维护。

### 安全

- `prod` 不注册测试后台 Bean 或启动保护明确阻断错误配置。
- 默认配置下测试后台关闭。
- 非 ADMIN、错误密钥、跨数据集用户和任意 ID 清理均被拒绝。
- 日志、响应持久化字段不泄露 access key 和 JWT。
- 不支持真实数据复制和任意 SQL。

### 数据一致性

- 场景创建失败不留下半套业务数据。
- 同一幂等键同参不重复创建，同键异参返回 409。
- 清理支持重复调用且结果稳定。
- 钱包、确认、奖励、勋章和想吃状态满足现有唯一键与状态机。
- 新业务表未纳入清理策略时测试失败，不允许静默遗漏。

### 可维护性

- 场景通过注册表扩展，新场景不修改 Controller。
- 场景版本写入数据集，避免结构升级后误判旧数据。
- 前端只依赖公开 DTO，不直接依赖数据库字段。
- README 包含启用、创建、清理、故障处理和风险说明。

## 11. 风险与备选方案

| 风险 | 影响 | 控制措施 |
| --- | --- | --- |
| 测试后台被误开到生产 | 暴露高权限数据操作 | profile 隔离、默认关闭、强密钥、生产启动保护、专项测试 |
| 通用编辑破坏状态机 | 生成无法复现的脏数据 | 只提供场景化动作和现有业务 API，不提供表级编辑 |
| 新表未纳入清理 | 残留数据和唯一键冲突 | `information_schema` 覆盖测试、清理分类清单、场景版本 |
| 清理范围计算错误 | 误删其他测试或人工数据 | 数据集资源登记、确认码、删除计划、行数上限、事务回滚、隔离测试 |
| 共享目录被多个数据集使用 | 清理后其他场景失效 | 区分 `OWNED/REFERENCED`，引用存在时拒绝删除所有者，引用资源永不由数据集清理 |
| 审计记录长期增长 | 共享测试库容量增加 | 保持现有审计只增不删语义，按环境制定独立留存和整库重建策略，不由数据集清理绕过审计约束 |
| 创建流程包含独立事务 | 失败后留下部分数据 | 场景禁用批量独立事务入口；失败状态和补偿清理 |
| 测试数据混入真实信息 | 合规风险 | 固定合成字典、输入限制、明显前缀、禁止导入生产数据 |
| 独立前端增加维护成本 | 构建和依赖升级成本 | 首期页面控制在数据集和目录维护；若依赖不获批，改为 Vue + 原生 CSS |

## 12. 推荐里程碑

| 里程碑 | 范围 | 可独立验收产物 |
| --- | --- | --- |
| M1 后端基座 | 管理员登录、数据集表、基础家庭场景 | 可通过 API 创建、查询和清理基础家庭 |
| M2 核心场景 | 点餐钱包、完整成长、临时会话 | 可覆盖主要人工回归路径 |
| M3 管理端 | 登录、数据集、目录、学校菜单 | 测试人员无需命令行维护数据 |
| M4 加固 | 清理覆盖、生产保护、E2E、文档 | 可进入共享测试环境长期使用 |

推荐先完成 M1 和 M2，再开始管理端。这样接口和数据集模型先稳定，避免前端跟随频繁调整。
