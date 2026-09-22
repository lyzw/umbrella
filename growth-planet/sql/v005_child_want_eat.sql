-- P0 每日想吃（按天按餐的想吃标记）
-- 在 sprint1~4 + v002/v003/v004 之后执行。纯增量迁移，不改动既有表，无 DROP、无回填。
-- 回滚：仅 DROP 本文件创建的 1 张表即可（无种子数据）。

CREATE TABLE usr_child_want_eat (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  child_id    BIGINT       NOT NULL,
  family_id   BIGINT       NOT NULL,
  menu_date   DATE         NOT NULL,
  meal_type   VARCHAR(16)  NOT NULL            COMMENT 'BREAKFAST/LUNCH/DINNER',
  source_type VARCHAR(16)  NOT NULL            COMMENT '标记时所在菜单来源 SCHOOL/FAMILY（上下文，不进唯一键）',
  dish_type   VARCHAR(16)  NOT NULL            COMMENT '菜品类型 PRESET/FAMILY（与 DishRef 一致）',
  dish_id     BIGINT       NOT NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'MARKED' COMMENT 'MARKED/ADOPTED/COOKED（P2 状态流转用，P0 仅写 MARKED）',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at   BIGINT       NOT NULL DEFAULT 0,
  version     INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  -- 唯一键不含 source_type；含 delete_at 以保证「仅对未删除行唯一」：取消（软删）后重新想吃可再次插入。
  UNIQUE KEY uk_child_date_meal_dish (child_id, menu_date, meal_type, dish_type, dish_id, delete_at),
  KEY idx_family_date (family_id, menu_date, meal_type),
  KEY idx_child_date (child_id, menu_date),
  CONSTRAINT fk_want_eat_family FOREIGN KEY (family_id) REFERENCES usr_family (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='儿童每日想吃标记';
