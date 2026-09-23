-- =====================================================================
-- v011_dish_recipe：菜品配方信息（食材子表 + 做法 / 小贴士 / 时长 / 份量 / 难度）
-- 目标：预置菜品与家庭菜品同时支持配方维护，为「按食材聚合采购清单」预留基础。
-- 回滚：
--   ALTER TABLE life_dish DROP CHECK chk_dish_cook_steps, DROP CHECK chk_dish_cook_minutes,
--         DROP CHECK chk_dish_servings, DROP CHECK chk_dish_difficulty;
--   ALTER TABLE life_dish DROP COLUMN cook_steps, DROP COLUMN cook_tips,
--         DROP COLUMN cook_minutes, DROP COLUMN servings, DROP COLUMN difficulty;
--   ALTER TABLE life_family_dish DROP CHECK chk_fdish_cook_steps, DROP CHECK chk_fdish_cook_minutes,
--         DROP CHECK chk_fdish_servings, DROP CHECK chk_fdish_difficulty;
--   ALTER TABLE life_family_dish DROP COLUMN cook_steps, DROP COLUMN cook_tips,
--         DROP COLUMN cook_minutes, DROP COLUMN servings, DROP COLUMN difficulty;
--   DROP TABLE IF EXISTS life_dish_ingredient;
-- =====================================================================

-- ① 食材清单：预置 / 家庭共用一张表，owner_type 判别
--    注意：life_dish 与 life_family_dish 自增序列独立，故 (dish_id) 单独无法定位；
--    跨表无法建外键，归属完整性由服务层保证（见六章）。
CREATE TABLE life_dish_ingredient (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  owner_type  VARCHAR(16) NOT NULL               COMMENT 'PRESET=预置菜品 FAMILY=家庭菜品',
  dish_id     BIGINT      NOT NULL               COMMENT 'owner_type 对应菜品表的主键',
  name        VARCHAR(32) NOT NULL               COMMENT '食材名称',
  amount      VARCHAR(32) DEFAULT NULL           COMMENT '用量（自由文本：200g / 适量）',
  sort        INT         NOT NULL DEFAULT 0     COMMENT '展示顺序，0 起',
  create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at   BIGINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dish_ingredient_order (owner_type, dish_id, delete_at, sort),
  KEY idx_dish_ingredient (owner_type, dish_id, delete_at),
  CONSTRAINT chk_ingredient_owner CHECK (owner_type IN ('PRESET', 'FAMILY')),
  CONSTRAINT chk_ingredient_sort  CHECK (sort >= 0 AND sort < 30)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品食材清单（预置/家庭共用）';

-- ② 做法与轻量配方属性：预置菜品
ALTER TABLE life_dish
  ADD COLUMN cook_steps   JSON         NOT NULL DEFAULT (JSON_ARRAY()) COMMENT '做法步骤（有序文本数组）',
  ADD COLUMN cook_tips    VARCHAR(500) DEFAULT NULL                    COMMENT '烹饪小贴士',
  ADD COLUMN cook_minutes INT          DEFAULT NULL                    COMMENT '预计烹饪时长（分钟）',
  ADD COLUMN servings     TINYINT      DEFAULT NULL                    COMMENT '份量（人份）',
  ADD COLUMN difficulty   VARCHAR(16)  DEFAULT NULL                    COMMENT '难度：EASY/MEDIUM/HARD',
  ADD CONSTRAINT chk_dish_cook_steps   CHECK (JSON_TYPE(cook_steps) = 'ARRAY'),
  ADD CONSTRAINT chk_dish_cook_minutes CHECK (cook_minutes IS NULL OR cook_minutes BETWEEN 1 AND 1440),
  ADD CONSTRAINT chk_dish_servings     CHECK (servings IS NULL OR servings BETWEEN 1 AND 20),
  ADD CONSTRAINT chk_dish_difficulty   CHECK (difficulty IS NULL OR difficulty IN ('EASY','MEDIUM','HARD'));

-- ③ 做法与轻量配方属性：家庭菜品（列定义与约束同上，仅表名/约束名副缀不同）
ALTER TABLE life_family_dish
  ADD COLUMN cook_steps   JSON         NOT NULL DEFAULT (JSON_ARRAY()) COMMENT '做法步骤（有序文本数组）',
  ADD COLUMN cook_tips    VARCHAR(500) DEFAULT NULL                    COMMENT '烹饪小贴士',
  ADD COLUMN cook_minutes INT          DEFAULT NULL                    COMMENT '预计烹饪时长（分钟）',
  ADD COLUMN servings     TINYINT      DEFAULT NULL                    COMMENT '份量（人份）',
  ADD COLUMN difficulty   VARCHAR(16)  DEFAULT NULL                    COMMENT '难度：EASY/MEDIUM/HARD',
  ADD CONSTRAINT chk_fdish_cook_steps   CHECK (JSON_TYPE(cook_steps) = 'ARRAY'),
  ADD CONSTRAINT chk_fdish_cook_minutes CHECK (cook_minutes IS NULL OR cook_minutes BETWEEN 1 AND 1440),
  ADD CONSTRAINT chk_fdish_servings     CHECK (servings IS NULL OR servings BETWEEN 1 AND 20),
  ADD CONSTRAINT chk_fdish_difficulty   CHECK (difficulty IS NULL OR difficulty IN ('EASY','MEDIUM','HARD'));
