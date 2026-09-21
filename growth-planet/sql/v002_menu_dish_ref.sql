-- V0.0.2 菜单菜品引用结构变更（C-01/C-03，对应变更文档 2026-09-20-family-private-dish-ddl-change.md）
-- 在 v002_family_dish.sql 之后执行。
-- 变更 1：life_menu_daily 新增 version 乐观锁列（C-03）
-- 变更 2：替换 chk_menu_dishes 约束（语义改为对象数组，长度 1-50 不变）
-- 变更 3：历史 dish_ids 纯ID数组 → 对象数组（生产手动执行，见末尾注释块）
-- 回滚：见变更文档 §6.3 反向脚本
-- 注意：元素结构（{type,id}）由应用层 DishRef 反序列化 + Service 校验保证，
--       约束只校验「数组 + 长度 1-50」，与现状一致。

-- 变更 1：新增 version 乐观锁列
ALTER TABLE life_menu_daily
  ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER status;

-- 变更 2：替换 chk_menu_dishes 约束
ALTER TABLE life_menu_daily
  DROP CHECK chk_menu_dishes;

ALTER TABLE life_menu_daily
  ADD CONSTRAINT chk_menu_dishes CHECK (
    JSON_TYPE(dish_ids) = 'ARRAY'
    AND JSON_LENGTH(dish_ids) BETWEEN 1 AND 50
  );

-- 变更 4：life_menu_item 新增 source_type 列（点单快照区分菜品来源 PRESET/FAMILY）
-- 编码中发现：OrderLineReq 改为 DishRef {type,id} 后，点单快照需记录来源，
-- 否则确认时重建 OrderLineReq 无法区分预置/家庭菜品（变更文档原未覆盖，本列补充）。
ALTER TABLE life_menu_item
  ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'PRESET' AFTER dish_id;

-- 变更 5：扩展 uk_confirm_dish 唯一键，纳入 source_type
-- 预置菜品(life_dish)与家庭私有菜品(life_family_dish)自增序列独立，
-- 同一 confirm 内 PRESET#1 与 FAMILY#1 是不同菜品，需 source_type 参与唯一约束，
-- 否则混合点单会因 (confirm_id, dish_id) 重复而触发并发冲突(E-007)。
ALTER TABLE life_menu_item
  DROP INDEX uk_confirm_dish,
  ADD UNIQUE KEY uk_confirm_dish (confirm_id, dish_id, source_type);

-- 变更 3：历史数据迁移（生产手动执行；要求 MySQL 8.0.14+ JSON_TABLE）
-- 集成测试环境每次重建 schema，此时表为空，无需迁移；
-- 迁移脚本默认注释，生产执行前在目标 MySQL 版本验证语法后取消注释执行。
-- 幂等判断：首元素无 .type 路径 → 视为未迁移的纯 ID 数组。
--
-- UPDATE life_menu_daily
-- SET dish_ids = (
--   SELECT JSON_ARRAYAGG(JSON_OBJECT('type', 'PRESET', 'id', jt.dish_id))
--   FROM JSON_TABLE(dish_ids, '$[*]' COLUMNS(dish_id BIGINT PATH '$')) AS jt
-- )
-- WHERE delete_at = 0
--   AND JSON_TYPE(dish_ids) = 'ARRAY'
--   AND JSON_LENGTH(dish_ids) > 0
--   AND JSON_EXTRACT(dish_ids, '$[0].type') IS NULL;
--
-- 迁移验证（迁移后执行，期望 unmigrated = 0）：
-- SELECT COUNT(*) AS unmigrated FROM life_menu_daily
-- WHERE delete_at = 0 AND JSON_TYPE(dish_ids) = 'ARRAY'
--   AND JSON_LENGTH(dish_ids) > 0
--   AND JSON_EXTRACT(dish_ids, '$[0].type') IS NULL;
