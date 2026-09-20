-- V0.0.2 家务闭环 + 勋章基座（F-028~F-032, F-048）
-- 在 V0.0.1（sprint1~4 表）之后执行。纯增量迁移，不改动既有表。
-- 回滚：仅 DROP 本文件创建的 5 张表即可，既有业务数据不受影响。
-- 金额口径：reward_amount 使用 DECIMAL(10,2)，与 life_wallet.balance 一致；
-- 家务奖励经 WalletService.credit 计入余额，trans_type=GRANT、scene=CHORE_REWARD，不计入支出额度。

CREATE TABLE life_chore_task (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  family_id       BIGINT NOT NULL,
  title           VARCHAR(64) NOT NULL,
  description     VARCHAR(255) NOT NULL DEFAULT '',
  icon            VARCHAR(32) NOT NULL DEFAULT '',
  estimated_minutes INT NOT NULL DEFAULT 0,
  reward_amount   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  cycle           VARCHAR(16) NOT NULL DEFAULT 'ONCE',
  sort_order      INT NOT NULL DEFAULT 0,
  status          VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at       BIGINT NOT NULL DEFAULT 0,
  KEY idx_chore_task_family (family_id, status, sort_order),
  CONSTRAINT chk_chore_task_cycle CHECK (cycle IN ('ONCE', 'DAILY', 'WEEKLY')),
  CONSTRAINT chk_chore_task_status CHECK (status IN ('NORMAL', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE life_chore_instance (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id         BIGINT NOT NULL,
  family_id       BIGINT NOT NULL,
  child_id        BIGINT NOT NULL,
  status          VARCHAR(16) NOT NULL DEFAULT 'CLAIMED',
  version         INT NOT NULL DEFAULT 0,
  claim_date      DATE NOT NULL,
  submit_time     DATETIME DEFAULT NULL,
  confirm_time    DATETIME DEFAULT NULL,
  parent_id       BIGINT DEFAULT NULL,
  reward_amount   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  reward_granted  TINYINT NOT NULL DEFAULT 0,
  medal_code      VARCHAR(32) DEFAULT NULL,
  reject_reason   VARCHAR(255) DEFAULT NULL,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at       BIGINT NOT NULL DEFAULT 0,
  KEY idx_chore_instance_child (child_id, status, claim_date),
  KEY idx_chore_instance_task (task_id),
  CONSTRAINT chk_chore_instance_status CHECK (status IN ('CLAIMED', 'SUBMITTED', 'CONFIRMED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE life_medal_definition (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  code            VARCHAR(32) NOT NULL,
  name            VARCHAR(64) NOT NULL,
  description     VARCHAR(255) NOT NULL DEFAULT '',
  icon            VARCHAR(32) NOT NULL DEFAULT '',
  category        VARCHAR(16) NOT NULL,
  condition_type  VARCHAR(16) NOT NULL,
  threshold       INT NOT NULL DEFAULT 1,
  sort_order      INT NOT NULL DEFAULT 0,
  status          VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at       BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_medal_code (code),
  CONSTRAINT chk_medal_category CHECK (category IN ('CHORE', 'HEALTH', 'EXCHANGE', 'STREAK')),
  CONSTRAINT chk_medal_condition CHECK (condition_type IN ('EVENT', 'COUNT', 'STREAK')),
  CONSTRAINT chk_medal_status CHECK (status IN ('NORMAL', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE life_medal_award (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  definition_id     BIGINT NOT NULL,
  family_id         BIGINT NOT NULL,
  child_id          BIGINT NOT NULL,
  awarded_at        BIGINT NOT NULL,
  consecutive_count INT NOT NULL DEFAULT 0,
  ref_id            BIGINT NOT NULL,
  create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at         BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_medal_award_unique (definition_id, child_id, ref_id),
  KEY idx_medal_award_child (child_id, definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE life_chore_streak (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  child_id        BIGINT NOT NULL,
  current_streak  INT NOT NULL DEFAULT 0,
  longest_streak  INT NOT NULL DEFAULT 0,
  last_claim_date DATE DEFAULT NULL,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at       BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_chore_streak_child (child_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 种子勋章（幂等，重复执行安全）。ref_id 在发放时由调用方决定：
-- EVENT 类用业务实例 id；COUNT/STREAK 类用阈值本身，保证每人每阈值仅发放一次。
INSERT IGNORE INTO life_medal_definition (code, name, description, icon, category, condition_type, threshold, sort_order)
VALUES
  ('CHORE_FIRST',  '初次当家',   '完成第一次家务',         '🌟', 'CHORE', 'COUNT', 1,  10),
  ('CHORE_10',     '家务小能手', '累计完成 10 次家务',      '🏅', 'CHORE', 'COUNT', 10, 20),
  ('CHORE_STREAK3','连做三日',   '连续 3 天完成家务',       '🔥', 'CHORE', 'STREAK', 3,  30);
