-- P3 儿童心愿菜单（家长配上限 / 儿童选菜提交 / 家长收通知）
-- 在 sprint1~4 + v002~v006 之后执行。增量迁移：
--   1) 2 张新表（家长设置 + 心愿菜单提交单）
--   2) usr_child_want_eat 增加一列 wish_id（收编关系）+ 1 个索引
-- 明细复用 usr_child_want_eat（决策 D1：数据复用），不新建明细表。
-- 回滚：
--   DROP TABLE life_wish_menu;
--   DROP TABLE usr_family_setting;
--   ALTER TABLE usr_child_want_eat DROP KEY idx_want_eat_wish, DROP COLUMN wish_id;
-- wish_id 是"默认 0"的增量列，回滚后既有想吃逻辑无感知；既有表结构仅此一处变更。

-- ============ usr_family_setting（家庭级设置）============
CREATE TABLE usr_family_setting (
  id                   BIGINT  PRIMARY KEY AUTO_INCREMENT,
  family_id            BIGINT  NOT NULL,
  wish_menu_max_dishes TINYINT NOT NULL DEFAULT 5  COMMENT '心愿菜单可选菜品上限 1~10',
  wish_menu_enabled    TINYINT NOT NULL DEFAULT 1  COMMENT '心愿菜单功能开关 1=开启 0=关闭',
  version              INT     NOT NULL DEFAULT 0  COMMENT '手写乐观锁（项目未装配乐观锁插件）',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at   BIGINT   NOT NULL DEFAULT 0,
  UNIQUE KEY uk_family_setting (family_id, delete_at),
  CONSTRAINT fk_setting_family FOREIGN KEY (family_id) REFERENCES usr_family (id),
  CONSTRAINT chk_setting_max CHECK (wish_menu_max_dishes BETWEEN 1 AND 10),
  CONSTRAINT chk_setting_enabled CHECK (wish_menu_enabled IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭级设置（心愿菜单上限与开关）';

-- ============ life_wish_menu（心愿菜单提交单，每天一份）============
CREATE TABLE life_wish_menu (
  id             BIGINT   NOT NULL AUTO_INCREMENT,
  child_id       BIGINT   NOT NULL,
  family_id      BIGINT   NOT NULL,
  menu_date      DATE     NOT NULL,
  status         VARCHAR(16) NOT NULL COMMENT 'SUBMITTED/WITHDRAWN；无行=孩子当天未创建（可选功能）',
  max_dishes     TINYINT  NOT NULL COMMENT '提交时快照的家长上限，便于回看当时口径',
  dish_count     INT      NOT NULL DEFAULT 0,
  submit_version INT      NOT NULL DEFAULT 0 COMMENT '每次有效提交 +1；通知幂等键第二段',
  submit_time    DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at   BIGINT   NOT NULL DEFAULT 0,
  version     INT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  -- 含 delete_at：仅对未删除行唯一（与 usr_child_want_eat 同款约定）
  UNIQUE KEY uk_wish_child_date (child_id, menu_date, delete_at),
  KEY idx_wish_family_date (family_id, menu_date),
  CONSTRAINT fk_wish_family FOREIGN KEY (family_id) REFERENCES usr_family (id),
  CONSTRAINT chk_wish_status CHECK (status IN ('SUBMITTED', 'WITHDRAWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='儿童心愿菜单提交单（每天一份）';

-- ============ 明细复用：usr_child_want_eat 增加收编列 ============
-- wish_id = 0     ：普通想吃标记（未收编）
-- wish_id > 0     ：已被该心愿单的上一次提交收录（撤回后保留，用于回显"上次提交了哪些"）
ALTER TABLE usr_child_want_eat ADD COLUMN wish_id BIGINT NOT NULL DEFAULT 0 COMMENT '被收编的心愿单 id，0=未收编';
ALTER TABLE usr_child_want_eat ADD KEY idx_want_eat_wish (wish_id);
