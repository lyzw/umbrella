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
