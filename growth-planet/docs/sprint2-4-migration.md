# Sprint 2-4 增量迁移

## 适用范围

仅适用于已完成 Sprint 1 初始化或旧六表 Sprint 1 cutover 的数据库。
`sql/sprint1_schema.sql` 仅用于空库初始化，禁止对存量库再次执行。
本次没有连接或迁移业务库；下述测试只使用 BaseIT 的隔离 Testcontainers 和合成数据。
本说明不是生产执行授权，Q-01、Q-06、Q-07 门禁仍保持未关闭。

## 执行前检查

1. 停写并暂停后台投递、隐私处理及其他写任务，确认应用实例不会自动初始化 schema。
   核验目标实例、库名、版本、字符集、权限及 Sprint 1 基线；先在隔离副本演练 DDL 锁等待和耗时。
2. 完成全量备份，保留 binlog/恢复位置，记录各表行数、`SHOW CREATE TABLE`、状态分布、
   最大业务 ID、逻辑删除行、授权撤回与权利请求记录；验证备份可恢复。
3. 以下新表必须全部不存在：`life_wallet`、`life_allowance_rule`、`life_allowance_log`、
   `life_dish_category`、`life_dish`、`life_menu_daily`、`life_menu_confirm`、
   `life_menu_item`、`life_confirm_approval`、`sys_notice_subscription`、`sys_privacy_verification`。
   任一已存在即停止，先确定是旧业务结构还是部分执行结果，禁止 DROP/TRUNCATE 后重跑。
4. 以下新增列必须不存在：`usr_child_profile.favorite_dish_ids`；
   `sys_notice.read_at/attempt_count/last_attempt_at/next_retry_at/last_error`；
   `sys_privacy_request.operator_id/evidence_ref/version`。
   同时检查新增索引、约束名没有冲突，禁止用 `IF NOT EXISTS` 掩盖结构差异。
5. 隐私幂等唯一范围从 `(requester_id, request_type, idempotency_key)` 扩大为
   `(requester_id, idempotency_key)`。执行以下只读预检，结果必须为空，不能排除逻辑删除行：

```sql
SELECT requester_id, idempotency_key, COUNT(*) AS duplicate_count
FROM sys_privacy_request
GROUP BY requester_id, idempotency_key
HAVING COUNT(*) > 1;
```

冲突须逐条人工审查并形成获批的数据处理方案，不得静默删除、合并或改写历史权利请求键。
字段依赖 MySQL JSON 表达式默认值、实际执行的 CHECK 约束及 InnoDB 外键；
隔离测试基线为 MySQL 8.0.36，不代表其他版本已验证。

## 一次性顺序

在上述基线与预检均通过后，由获授权运维逐份执行并记录脚本校验值、开始/结束时间和结果：

1. `sql/sprint2_schema.sql`：新建钱包、额度规则和流水表。
2. `sql/sprint3_catalog.sql`：增加收藏 JSON 列，新建分类、菜品、每日菜单。
3. `sql/sprint3_confirmation.sql`：新建确认单、菜品快照及审批记录。
4. `sql/sprint4_schema.sql`：扩展通知及隐私请求，新建通知授权与隐私复核凭证表。

四份脚本均只能执行一次，不可作为每次启动脚本。必须先验证本份脚本成功再执行下一份。
Sprint 2-4 脚本不回填钱包、不授予订阅授权、不创建确认单，也不改变既有授权、审计和权利请求历史。
收藏默认空 JSON 数组（含历史软删除档案）；旧通知保持未读、重试次数为零，
`SUBSCRIBE/UNAUTHORIZED` 不自动变为可投递；旧隐私请求操作人和证据引用为空，版本为零。

## 存量业务禁区

若发现旧 wallet、额度、流水或菜单确认结构，本组脚本不适用，必须停止并另行审计迁移设计。
禁止直接覆盖旧余额、补造流水、重置幂等键、释放软删除记录的唯一键或按新默认额度覆盖历史额度。
禁止把旧 `APPROVED` 直接映射为 `COMPLETED`，也不能据此补扣款或重新审批；
新确认单 CHECK 不接受 `APPROVED`。必须先核对历史扣款、流水、快照、余额和授权证据，
经过独立业务对账、异常隔离和审批后制定专用方案。本组脚本不承担这些转换。

## 失败与回退

MySQL DDL 会隐式提交，四份脚本不是一个可回滚事务，单份内前面的语句也可能已经生效。
失败后保持停写，记录错误及已完成语句，通过 `information_schema` 和 `SHOW CREATE TABLE`
核对实际结构；不得盲目整份重跑。由负责人审核恢复步骤，仅执行确认尚未完成的语句。
例如 Sprint 4 隐私唯一索引失败时，通知列和授权表可能已经创建。

优先回退到与扩展 schema 兼容的应用，保留新增列、历史数据、审计与幂等键。
需要恢复备份时必须有获批恢复方案，重放备份之后的撤回、删除和权利请求，
防止恢复出已失效的授权或已删除的个人数据；不可用删表删列代替回退。

## 验证与门禁

- 逐表核对原列和历史行、软删除行、授权与审计证据没有变化，新增表最初为空。
  检查新增列默认值、索引列序、CHECK 和外键实际存在，确认钱包/确认单没有隐式回填。
- `MigrationIT` 仅初始化 Sprint 1，独立验证旧六表到 Sprint 1 的迁移。
  `Sprint234MigrationIT` 同样仅初始化 Sprint 1，再顺序执行上述四份脚本；
  比较全部 Sprint 1 表的原列快照，覆盖新增列默认值、新表、主要唯一键及软删除后不可复用，
  包括隐私跨类型键冲突和确认单拒绝 `APPROVED`。
- 集成阶段已统一执行：2026-09-18 13:36:24全量100项通过，包含上述两类迁移测试；
  详见[验证记录](sprint2-4-verification.md)。容器通过不等于实际存量迁移、备份恢复演练、并发压测或生产上线批准。
- Q-01（处理依据、最低监护核验及正式协议）、Q-06（技术 GA、版本兼容及发布验证）、
  Q-07（独立 CHILD/PARENT 账号策略）继续保留。前端、真实微信、真机与运维验收不在本次范围。
