-- V0.0.2 健康打卡（F-033~F-035）
-- 在 V0.0.1（sprint1~4 表）+ v002/v003 之后执行。纯增量迁移，不改动既有表。
-- 健康打卡为纯行为激励，不涉及零花钱/额度护栏，无钱包链路改动。
-- 回滚：仅 DROP 本文件创建的 2 张表 + DELETE 本文件播种的勋章定义即可。

CREATE TABLE life_check_item (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  family_id    BIGINT NOT NULL,
  name         VARCHAR(32) NOT NULL,
  icon         VARCHAR(64) DEFAULT NULL,
  unit         VARCHAR(8) DEFAULT NULL,
  daily_target INT NOT NULL DEFAULT 0,
  sort_order   INT NOT NULL DEFAULT 0,
  version      INT NOT NULL DEFAULT 0,
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at    BIGINT NOT NULL DEFAULT 0,
  KEY idx_check_item_family (family_id, sort_order, id),
  CONSTRAINT fk_check_item_family FOREIGN KEY (family_id) REFERENCES usr_family (id),
  CONSTRAINT chk_check_item_target CHECK (daily_target >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE life_check_record (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  family_id   BIGINT NOT NULL,
  child_id    BIGINT NOT NULL,
  item_id     BIGINT NOT NULL,
  item_name   VARCHAR(32) NOT NULL,
  check_date  DATE NOT NULL,
  check_time  DATETIME NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at   BIGINT NOT NULL DEFAULT 0,
  KEY idx_check_record_child_date (family_id, child_id, check_date),
  KEY idx_check_record_item (family_id, item_id, check_date),
  CONSTRAINT fk_check_record_family FOREIGN KEY (family_id) REFERENCES usr_family (id),
  CONSTRAINT fk_check_record_item FOREIGN KEY (item_id) REFERENCES life_check_item (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 种子勋章：连续打卡（STREAK 类，ref_id=阈值，consecutiveCount=连续天数）。幂等，重复执行安全。
INSERT IGNORE INTO life_medal_definition (code, name, description, icon, category, condition_type, threshold, sort_order)
VALUES
  ('HEALTH_STREAK_3',  '健康打卡 3 天',  '连续 3 天打卡',   '🌱', 'HEALTH', 'STREAK', 3,  40),
  ('HEALTH_STREAK_7',  '健康打卡一周',   '连续 7 天打卡',   '🔥', 'HEALTH', 'STREAK', 7,  50),
  ('HEALTH_STREAK_14', '健康打卡双周',   '连续 14 天打卡',  '⭐', 'HEALTH', 'STREAK', 14, 60),
  ('HEALTH_STREAK_30', '健康打卡满月',   '连续 30 天打卡',  '🏆', 'HEALTH', 'STREAK', 30, 70);
