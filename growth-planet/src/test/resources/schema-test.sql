-- 集成测试用 schema（含 DROP，保证可重复执行）
DROP TABLE IF EXISTS sys_audit_log;
DROP TABLE IF EXISTS usr_consent_log;
DROP TABLE IF EXISTS usr_child_profile;
DROP TABLE IF EXISTS usr_family_member;
DROP TABLE IF EXISTS usr_family;
DROP TABLE IF EXISTS usr_user;

CREATE TABLE usr_user (
  id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
  openid        VARCHAR(64)  NOT NULL,
  unionid       VARCHAR(64)  DEFAULT NULL,
  role          VARCHAR(16)  NOT NULL DEFAULT 'UNSET',
  nickname      VARCHAR(64)  DEFAULT NULL,
  avatar_url    VARCHAR(512) DEFAULT NULL,
  phone         VARCHAR(20)  DEFAULT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at     BIGINT       NOT NULL DEFAULT 0,
  UNIQUE KEY uk_openid (openid, delete_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usr_family (
  id                 BIGINT      PRIMARY KEY AUTO_INCREMENT,
  family_name        VARCHAR(64) NOT NULL,
  owner_user_id      BIGINT      NOT NULL,
  invite_code        VARCHAR(6)  DEFAULT NULL,
  invite_code_expire BIGINT      DEFAULT NULL,
  create_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at          BIGINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_invite_code (invite_code, delete_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usr_family_member (
  id            BIGINT      PRIMARY KEY AUTO_INCREMENT,
  family_id     BIGINT      NOT NULL,
  user_id       BIGINT      NOT NULL,
  relation_label VARCHAR(32) DEFAULT NULL,
  role          VARCHAR(16) NOT NULL,
  bind_status   VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  guardian_status VARCHAR(16) DEFAULT 'NOT_REQUIRED',
  create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at     BIGINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_family_user (family_id, user_id, delete_at),
  KEY idx_family (family_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usr_child_profile (
  id            BIGINT      PRIMARY KEY AUTO_INCREMENT,
  user_id       BIGINT      NOT NULL,
  family_id     BIGINT      NOT NULL,
  nickname      VARCHAR(64) DEFAULT NULL,
  grade         VARCHAR(32) DEFAULT NULL,
  school        VARCHAR(128) DEFAULT NULL,
  allergies     JSON        DEFAULT NULL,
  dislikes      JSON        DEFAULT NULL,
  tastes        JSON        DEFAULT NULL,
  profile_status VARCHAR(16) NOT NULL DEFAULT 'INCOMPLETE',
  create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at     BIGINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_user (user_id, delete_at),
  KEY idx_family (family_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usr_consent_log (
  id                 BIGINT      PRIMARY KEY AUTO_INCREMENT,
  user_id            BIGINT      NOT NULL,
  child_id           BIGINT      NOT NULL,
  family_id          BIGINT      NOT NULL,
  consent_type       VARCHAR(32) NOT NULL,
  action             VARCHAR(16) NOT NULL,
  version            VARCHAR(16) NOT NULL,
  self_reported_age  TINYINT     DEFAULT NULL,
  guardian_status    VARCHAR(16) NOT NULL,
  signed_at          BIGINT      DEFAULT NULL,
  expire_at          BIGINT      DEFAULT NULL,
  create_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at          BIGINT      NOT NULL DEFAULT 0,
  KEY idx_child (child_id),
  KEY idx_family (family_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_audit_log (
  id           BIGINT      PRIMARY KEY AUTO_INCREMENT,
  actor_user_id BIGINT     DEFAULT NULL,
  family_id    BIGINT      DEFAULT NULL,
  action       VARCHAR(32) NOT NULL,
  target_type  VARCHAR(32) DEFAULT NULL,
  target_id    BIGINT      DEFAULT NULL,
  ip           VARCHAR(64) DEFAULT NULL,
  detail       VARCHAR(1024) DEFAULT NULL,
  create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delete_at    BIGINT      NOT NULL DEFAULT 0,
  KEY idx_actor (actor_user_id),
  KEY idx_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
