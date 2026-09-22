-- growth_planet.sql 测试库汇总建表脚本（由原迁移脚本 sprint1~v007 顺序拼接生成，原 dump 保留于 growth_planet_full_dump.sql）

-- ============ sprint1_schema ============
-- 成长星球 V0.0.1 Sprint 1 数据库结构：仅供空库初始化，不是增量迁移。
-- 字符集 utf8mb4；所有表含 id / create_time / delete_at，业务可更新表另含 update_time。
-- 身份和幂等键不因逻辑删除而复用；存量库使用 migrations 预检及增量脚本。

-- ============ usr_user ============
CREATE TABLE usr_user (
  id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
  openid        VARCHAR(64)  NOT NULL,
  unionid       VARCHAR(64)  DEFAULT NULL,
  role          VARCHAR(16)  NOT NULL DEFAULT 'UNSELECTED',
  nickname      VARCHAR(64)  DEFAULT NULL,
  avatar_url    VARCHAR(512) DEFAULT NULL,
  phone         VARCHAR(20)  DEFAULT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',  -- NORMAL/DISABLED
  token_version BIGINT       NOT NULL DEFAULT 0,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at     BIGINT       NOT NULL DEFAULT 0,
  UNIQUE KEY uk_openid (openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ usr_family ============
CREATE TABLE usr_family (
  id                 BIGINT      PRIMARY KEY AUTO_INCREMENT,
  family_name        VARCHAR(64) NOT NULL,
  owner_user_id      BIGINT      NOT NULL,              -- 创建者(家长)
  invite_code        VARCHAR(6)  DEFAULT NULL,          -- 6位大写字母数字
  invite_code_expire BIGINT      DEFAULT NULL,          -- 毫秒时间戳, 24h
  create_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at          BIGINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_invite_code (invite_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ usr_family_member ============
CREATE TABLE usr_family_member (
  id            BIGINT      PRIMARY KEY AUTO_INCREMENT,
  family_id     BIGINT      NOT NULL,
  user_id       BIGINT      NOT NULL,
  relation_label VARCHAR(32) DEFAULT NULL,             -- 关系标注(爸/妈/娃)
  role          VARCHAR(16) NOT NULL,                  -- CHILD/PARENT
  bind_status   VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  guardian_status VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED',
  application_version INT NOT NULL DEFAULT 1,
  create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at     BIGINT      NOT NULL DEFAULT 0,
  effective_child_id BIGINT GENERATED ALWAYS AS
    (CASE WHEN role = 'CHILD' AND bind_status IN ('PENDING','BOUND') AND delete_at = 0 THEN user_id ELSE NULL END) STORED,
  UNIQUE KEY uk_family_user (family_id, user_id),
  UNIQUE KEY uk_effective_child (effective_child_id),
  KEY idx_family (family_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ usr_child_profile ============
CREATE TABLE usr_child_profile (
  id            BIGINT      PRIMARY KEY AUTO_INCREMENT,
  user_id       BIGINT      NOT NULL,                  -- 关联儿童账户(唯一)
  family_id     BIGINT      NOT NULL,
  nickname      VARCHAR(64) DEFAULT NULL,
  grade         VARCHAR(32) DEFAULT NULL,
  school        VARCHAR(128) DEFAULT NULL,
  allergies     JSON        DEFAULT NULL,              -- List<String> 忌口
  dislikes      JSON        DEFAULT NULL,              -- List<String> 不爱吃
  tastes        JSON        DEFAULT NULL,              -- List<String> 偏好
  profile_status VARCHAR(16) NOT NULL DEFAULT 'INCOMPLETE', -- INCOMPLETE/COMPLETE
  create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at     BIGINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_user (user_id),
  KEY idx_family (family_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ usr_consent_log ============
CREATE TABLE usr_consent_log (
  id                 BIGINT      PRIMARY KEY AUTO_INCREMENT,
  user_id            BIGINT      NOT NULL,             -- 监护人(家长)
  child_id           BIGINT      NOT NULL,             -- 儿童
  family_id          BIGINT      NOT NULL,
  apply_id           BIGINT      DEFAULT NULL,         -- NULL 仅用于隔离存量旧同意
  application_version INT        DEFAULT NULL,
  consent_type       VARCHAR(32) NOT NULL,            -- 如 ORDER/PROFILE
  action             VARCHAR(16) NOT NULL,            -- GRANT/REVOKE
  version            VARCHAR(16) NOT NULL,            -- 同意书版本
  self_reported_age  INT         DEFAULT NULL,
  guardian_status    VARCHAR(16) NOT NULL,             -- UNVERIFIED/SELF_ATTESTED/VERIFIED
  signed_at          BIGINT      DEFAULT NULL,
  expire_at          BIGINT      DEFAULT NULL,
  create_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at          BIGINT      NOT NULL DEFAULT 0,
  KEY idx_child (child_id),
  KEY idx_consent_scope (family_id, child_id, user_id, apply_id, application_version, consent_type, id),
  KEY idx_family (family_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ sys_audit_log ============
CREATE TABLE sys_audit_log (
  id           BIGINT      PRIMARY KEY AUTO_INCREMENT,
  actor_user_id BIGINT     DEFAULT NULL,              -- 操作人
  family_id    BIGINT      DEFAULT NULL,
  action       VARCHAR(32) NOT NULL,                 -- LOGIN/ROLE/CREATE_FAMILY/JOIN/BIND/GRANT/REVOKE/EXPORT...
  target_type  VARCHAR(32) DEFAULT NULL,
  target_id    BIGINT      DEFAULT NULL,
  ip           VARCHAR(64) DEFAULT NULL,
  detail       VARCHAR(1024) DEFAULT NULL,
  request_id   VARCHAR(64) DEFAULT NULL,
  result       VARCHAR(16) DEFAULT NULL,
  error_code   VARCHAR(16) DEFAULT NULL,
  create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delete_at    BIGINT      NOT NULL DEFAULT 0,
  KEY idx_actor (actor_user_id),
  KEY idx_action (action),
  KEY idx_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_notice (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_key VARCHAR(128) NOT NULL,
  receiver_id BIGINT NOT NULL,
  family_id BIGINT NOT NULL,
  child_id BIGINT NOT NULL,
  channel VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delete_at BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_event_receiver_channel (event_key, receiver_id, channel),
  KEY idx_delivery (channel, status, id),
  KEY idx_child_scope (family_id, child_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_privacy_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  requester_id BIGINT NOT NULL,
  child_id BIGINT NOT NULL,
  family_id BIGINT NOT NULL,
  request_type VARCHAR(16) NOT NULL,
  idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(32) NOT NULL,
  due_at BIGINT DEFAULT NULL,
  verified_at BIGINT DEFAULT NULL,
  result_ref VARCHAR(512) DEFAULT NULL,
  expires_at BIGINT DEFAULT NULL,
  error_code VARCHAR(32) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_request (requester_id, request_type, idempotency_key),
  KEY idx_processing (status, due_at),
  KEY idx_child_scope (family_id, child_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ sprint2_schema ============
-- Apply once after sprint1_schema.sql (or completed Sprint 1 cutover).
-- New tables only. No cascade deletes; audit and idempotency identifiers are permanent.
CREATE TABLE life_wallet (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  child_id BIGINT NOT NULL,
  family_id BIGINT NOT NULL,
  balance DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  version INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_wallet_child (child_id),
  KEY idx_wallet_family (family_id),
  CONSTRAINT ck_wallet_balance CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE life_allowance_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  child_id BIGINT NOT NULL,
  family_id BIGINT NOT NULL,
  single_limit DECIMAL(10,2) NOT NULL DEFAULT 30.00,
  daily_limit DECIMAL(10,2) NOT NULL DEFAULT 30.00,
  weekly_limit DECIMAL(10,2) NOT NULL DEFAULT 150.00,
  daily_used DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  weekly_used DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  daily_period DATE NOT NULL,
  weekly_period DATE NOT NULL,
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_rule_child (child_id),
  KEY idx_rule_family (family_id),
  CONSTRAINT ck_rule_limits CHECK (single_limit >= 0 AND single_limit <= daily_limit
      AND daily_limit <= weekly_limit AND weekly_limit <= 9999.99),
  CONSTRAINT ck_rule_usage CHECK (daily_used >= 0 AND weekly_used >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE life_allowance_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  wallet_id BIGINT NOT NULL,
  child_id BIGINT NOT NULL,
  family_id BIGINT NOT NULL,
  operator_id BIGINT NOT NULL,
  trans_type VARCHAR(16) NOT NULL,
  scene VARCHAR(32) NOT NULL,
  ref_id BIGINT DEFAULT NULL,
  amount DECIMAL(10,2) NOT NULL,
  balance_before DECIMAL(10,2) NOT NULL,
  balance_after DECIMAL(10,2) NOT NULL,
  wallet_version INT NOT NULL,
  usage_date DATE NOT NULL,
  request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  reason VARCHAR(100) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delete_at BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ledger_business (wallet_id, scene, ref_id, trans_type),
  UNIQUE KEY uk_grant_request (operator_id, scene, request_key),
  KEY idx_ledger_child_date (child_id, usage_date, id),
  CONSTRAINT ck_ledger_amount CHECK (amount >= 0 AND balance_before >= 0 AND balance_after >= 0),
  CONSTRAINT ck_ledger_ref CHECK (trans_type <> 'DEDUCT' OR ref_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ sprint3_catalog ============
-- Apply once after sprint1_schema.sql (or the completed Sprint 1 cutover).
-- This is an incremental migration: do not rerun the ALTER or load against an unreviewed legacy catalog.
ALTER TABLE usr_child_profile
  ADD COLUMN favorite_dish_ids JSON NOT NULL DEFAULT (JSON_ARRAY());

CREATE TABLE life_dish_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(32) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at BIGINT NOT NULL DEFAULT 0,
  KEY idx_sort (sort, id),
  CONSTRAINT chk_category_sort CHECK (sort >= 0),
  CONSTRAINT chk_category_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE life_dish (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  image_url VARCHAR(255) DEFAULT NULL,
  virtual_price DECIMAL(10,2) NOT NULL DEFAULT 0,
  calories INT DEFAULT NULL,
  tags VARCHAR(255) DEFAULT NULL,
  allergens JSON NOT NULL DEFAULT (JSON_ARRAY()),
  allergen_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
  spice_level TINYINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at BIGINT NOT NULL DEFAULT 0,
  KEY idx_category (category_id, id),
  KEY idx_price (virtual_price, id),
  CONSTRAINT fk_dish_category FOREIGN KEY (category_id) REFERENCES life_dish_category (id),
  CONSTRAINT chk_dish_price CHECK (virtual_price >= 0),
  CONSTRAINT chk_dish_calories CHECK (calories IS NULL OR calories >= 0),
  CONSTRAINT chk_dish_spice CHECK (spice_level BETWEEN 0 AND 3),
  CONSTRAINT chk_dish_status CHECK (status IN ('ON_SALE', 'OFF_SALE')),
  CONSTRAINT chk_dish_allergen_status CHECK (allergen_status IN ('UNKNOWN', 'DECLARED')),
  CONSTRAINT chk_dish_allergens CHECK (JSON_TYPE(allergens) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE life_menu_daily (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_type VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  owner_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
  school VARCHAR(128) COLLATE utf8mb4_bin DEFAULT NULL,
  family_id BIGINT DEFAULT NULL,
  menu_date DATE NOT NULL,
  meal_type VARCHAR(20) NOT NULL,
  dish_ids JSON NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_source_owner_date_meal (source_type, owner_key, menu_date, meal_type),
  KEY idx_family (family_id, menu_date),
  CONSTRAINT fk_menu_family FOREIGN KEY (family_id) REFERENCES usr_family (id),
  CONSTRAINT chk_menu_owner CHECK (
    (source_type = 'FAMILY' AND family_id IS NOT NULL AND school IS NULL
      AND owner_key = CAST(family_id AS CHAR))
    OR (source_type = 'SCHOOL' AND family_id IS NULL AND school IS NOT NULL
      AND CHAR_LENGTH(TRIM(school)) > 0 AND owner_key = school)),
  CONSTRAINT chk_menu_meal CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER')),
  CONSTRAINT chk_menu_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
  CONSTRAINT chk_menu_dishes CHECK (JSON_TYPE(dish_ids) = 'ARRAY'
    AND JSON_LENGTH(dish_ids) BETWEEN 1 AND 50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ sprint3_confirmation ============
-- Apply once after sprint3_catalog.sql. New tables; no legacy APPROVED conversion.
CREATE TABLE life_menu_confirm (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  confirm_no VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  child_id BIGINT NOT NULL,
  family_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  menu_date DATE NOT NULL,
  meal_type VARCHAR(20) NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  version INT NOT NULL DEFAULT 0,
  previous_confirm_id BIGINT DEFAULT NULL,
  request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  approval_request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  is_over_limit TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(255) DEFAULT NULL,
  parent_id BIGINT DEFAULT NULL,
  completed_balance DECIMAL(10,2) DEFAULT NULL,
  completed_wallet_version INT DEFAULT NULL,
  estimated_balance DECIMAL(10,2) NOT NULL,
  submit_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_confirm_no (confirm_no),
  UNIQUE KEY uk_confirm_request (child_id, request_key),
  KEY idx_confirm_scope (family_id, child_id, status, id),
  KEY idx_confirm_menu (menu_id),
  KEY idx_confirm_previous (previous_confirm_id),
  CONSTRAINT ck_confirm_amount CHECK (total_amount >= 0),
  CONSTRAINT ck_confirm_status CHECK (status IN ('PENDING','REJECTED','CANCELLED','COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE life_menu_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  confirm_id BIGINT NOT NULL,
  dish_id BIGINT NOT NULL,
  dish_name VARCHAR(64) NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  subtotal DECIMAL(10,2) NOT NULL,
  note VARCHAR(255) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delete_at BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_confirm_dish (confirm_id, dish_id),
  CONSTRAINT ck_item_quantity CHECK (quantity BETWEEN 1 AND 9),
  CONSTRAINT ck_item_amount CHECK (unit_price >= 0 AND subtotal = unit_price * quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE life_confirm_approval (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  confirm_id BIGINT NOT NULL,
  parent_id BIGINT NOT NULL,
  action VARCHAR(20) NOT NULL,
  before_status VARCHAR(20) NOT NULL,
  after_status VARCHAR(20) NOT NULL,
  is_over_limit TINYINT NOT NULL,
  reason VARCHAR(255) DEFAULT NULL,
  suggested_items JSON DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delete_at BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_approval_confirm (confirm_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ sprint4_schema ============
-- Apply once AFTER Sprint 1/2/3 schemas. Do not run against a business database
-- without backup, row-count/duplicate preflight and DDL lock-time rehearsal.
-- Additive migration: old notices remain unread and SUBSCRIBE stays UNAUTHORIZED.
-- Rollback application only; retain these columns and durable audit/auth rows.
ALTER TABLE sys_notice
  ADD COLUMN read_at BIGINT DEFAULT NULL,
  ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
  ADD COLUMN last_attempt_at BIGINT DEFAULT NULL,
  ADD COLUMN next_retry_at BIGINT DEFAULT NULL,
  ADD COLUMN last_error VARCHAR(32) DEFAULT NULL,
  ADD KEY idx_notice_inbox (receiver_id, channel, read_at, id),
  ADD KEY idx_notice_retry (channel, status, next_retry_at, id),
  ADD CONSTRAINT chk_notice_attempts CHECK (attempt_count BETWEEN 0 AND 4);

CREATE TABLE sys_notice_subscription (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  notice_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  consent_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  authorized_at BIGINT NOT NULL,
  expires_at BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_subscription_notice (notice_id),
  KEY idx_subscription_user (user_id, status),
  CONSTRAINT chk_subscription_status CHECK (status IN ('AUTHORIZED', 'REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE sys_privacy_request
  ADD COLUMN operator_id BIGINT DEFAULT NULL,
  ADD COLUMN evidence_ref VARCHAR(128) DEFAULT NULL,
  ADD COLUMN version INT NOT NULL DEFAULT 0,
  ADD UNIQUE KEY uk_privacy_request_key (requester_id, idempotency_key),
  ADD KEY idx_privacy_requester (requester_id, child_id, create_time);

-- Reauthentication codes are stored only as SHA-256 digests, never raw codes.
-- Unique digest prevents a verified DELETE code being replayed for a new request.
CREATE TABLE sys_privacy_verification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  requester_id BIGINT NOT NULL,
  request_id BIGINT NOT NULL,
  verified_at BIGINT NOT NULL,
  UNIQUE KEY uk_privacy_verification (code_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ v002_chore_medal ============
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

-- ============ v002_schedule ============
-- V0.0.2 日程提醒（F-036~F-038）
-- 在 V0.0.1（sprint1~4）与 v002_chore_medal.sql 之后执行。纯增量迁移，不改动既有表。
-- 回滚：仅 DROP TABLE life_schedule 即可，既有业务数据不受影响。
-- 时间口径：schedule_date 为日程发生起始日；schedule_time 为本地挂钟时刻（HH:mm，不做时区换算，
--   与 BusinessTime 的 Asia/Shanghai 一致）；remind_minutes 为提前提醒分钟数（0=准点，上限 1440）。
-- 到点投递：ScheduleReminderService 扫描「今日为发生日 且 当前时刻 >= 提醒时刻 且 last_remind_date <> 今日」
--   的日程，经 F-011 通知中台投递 IN_APP 站内提醒（订阅通道降级，失败不回滚业务，同 E-008）。
-- last_remind_date 为投递幂等标记，同一发生日只投递一次，且不补发已错过的历史时刻。

CREATE TABLE life_schedule (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  family_id         BIGINT NOT NULL,
  child_id          BIGINT NOT NULL,
  creator_id        BIGINT NOT NULL,
  title             VARCHAR(64) NOT NULL,
  category          VARCHAR(16) NOT NULL DEFAULT 'OTHER',
  note              VARCHAR(255) NOT NULL DEFAULT '',
  schedule_date     DATE NOT NULL,
  schedule_time     VARCHAR(5) NOT NULL,
  repeat_type       VARCHAR(16) NOT NULL DEFAULT 'ONCE',
  repeat_weekdays   VARCHAR(32) NOT NULL DEFAULT '',
  remind_minutes    INT NOT NULL DEFAULT 0,
  status            VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  last_remind_date  DATE DEFAULT NULL,
  create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at         BIGINT NOT NULL DEFAULT 0,
  KEY idx_schedule_child_date (child_id, status, schedule_date),
  KEY idx_schedule_scan (status, schedule_date, last_remind_date),
  CONSTRAINT chk_schedule_category CHECK (category IN ('HOMEWORK', 'CLASS', 'MEDICINE', 'OTHER')),
  CONSTRAINT chk_schedule_repeat CHECK (repeat_type IN ('ONCE', 'DAILY', 'WEEKLY')),
  CONSTRAINT chk_schedule_status CHECK (status IN ('NORMAL', 'CANCELLED')),
  CONSTRAINT chk_schedule_remind CHECK (remind_minutes BETWEEN 0 AND 1440)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ v003_wallet_board ============
-- 成长星球 V0.0.2 · 零花钱看板（F-024~F-026）增量迁移
-- 说明：
--   1. 儿童维度的流水聚合已由 sprint2 的 idx_ledger_child_date (child_id, usage_date, id) 覆盖；
--   2. 家长端看板需按“家庭”统计本周支出/本周发放，补齐 (family_id, usage_date) 前缀，
--      避免家庭维度聚合成全表扫描；
--   3. 家庭虚拟总额按 life_wallet 聚合，已由 idx_wallet_family (family_id) 覆盖，无需新增。
ALTER TABLE life_allowance_log
  ADD INDEX idx_ledger_family_date (family_id, usage_date, id);

-- ============ v002_family_dish ============
-- V0.0.2 家庭私有菜品表（F-01~F-05，对应 PRD 2026-09-20-family-private-dish-prd.md）
-- 在 sprint3_catalog.sql（life_dish / life_dish_category 已建）之后执行。纯增量迁移，不改动既有表。
-- 回滚：DROP TABLE life_family_dish 即可，既有业务数据不受影响。
-- 归属：family_id 由后端从家长鉴权派生，客户端禁传；跨家庭 100% 隔离。
-- 扩展：visibility 本期固定 PRIVATE，预留 PUBLIC 供未来社区公开（UGC）。
-- 字段：复用 life_dish 全字段 + family_id + visibility + version（乐观锁）。

CREATE TABLE life_family_dish (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  family_id       BIGINT NOT NULL,
  category_id     BIGINT NOT NULL,
  name            VARCHAR(64) NOT NULL,
  image_url       VARCHAR(255) DEFAULT NULL,
  virtual_price   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  calories        INT DEFAULT NULL,
  tags            VARCHAR(255) DEFAULT NULL,
  allergens       JSON NOT NULL DEFAULT (JSON_ARRAY()),
  allergen_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
  spice_level     TINYINT NOT NULL,
  visibility      VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
  status          VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
  version         INT NOT NULL DEFAULT 0,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at       BIGINT NOT NULL DEFAULT 0,
  KEY idx_family_dish_status (family_id, status, id),
  KEY idx_family_dish_category (family_id, category_id, id),
  KEY idx_family_dish_update (family_id, update_time),
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

-- ============ v002_menu_dish_ref ============
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

-- ============ v004_health_check ============
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

-- ============ v005_child_want_eat ============
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

-- ============ v006_want_eat_medal ============
-- P2 采纳反馈闭环：孩子想吃被家长标记「已做」（COOKED）后发放的勋章定义
-- 在 sprint1~4 + v002~v005 之后执行。
-- 回滚：把这三条定义的 status 置为 'DISABLED' 即可立即停发（无需改代码或删行）；
--       如需完全回退，可再把 chk_medal_category 还原为不含 'MEAL' 的四值约束。
-- 计数口径：COUNT(usr_child_want_eat WHERE child_id = ? AND status = 'COOKED')（逻辑删除行自动排除）。
-- 幂等：MedalService.award 以 (definition_id, child_id, ref_id = 阈值) 为幂等键，重复标记 COOKED 不会重复发。

-- v002 建表时把勋章分类限定为 CHORE/HEALTH/EXCHANGE/STREAK，餐食类需先扩约束，
-- 否则下面的 INSERT 会因 CHECK 失败（注意 INSERT IGNORE 会把它降级为警告而静默跳过）。
-- 先 DROP 再 ADD 同名约束，保证脚本重复执行安全。
ALTER TABLE life_medal_definition DROP CHECK chk_medal_category;
ALTER TABLE life_medal_definition ADD CONSTRAINT chk_medal_category
  CHECK (category IN ('CHORE', 'HEALTH', 'EXCHANGE', 'STREAK', 'MEAL'));

INSERT IGNORE INTO life_medal_definition (code, name, description, icon, category, condition_type, threshold, sort_order)
VALUES
  ('MEAL_COOKED_1',  '心愿达成',   '第一个想要吃的菜被做出来', '🌟', 'MEAL', 'COUNT', 1,  80),
  ('MEAL_COOKED_10', '小小心愿官', '累计 10 次想吃被做出来',   '🍽', 'MEAL', 'COUNT', 10, 90),
  ('MEAL_COOKED_30', '餐餐有回应', '累计 30 次想吃被做出来',   '🏆', 'MEAL', 'COUNT', 30, 100);

-- ============ v007_wish_menu ============
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
