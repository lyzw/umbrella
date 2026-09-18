-- 成长星球 V0.0.1 Sprint 1 数据库结构（生产用，幂等请自行评估）
-- 字符集 utf8mb4；所有表含 id / create_time / update_time / delete_at(BIGINT DEFAULT 0 逻辑删除)
-- 业务唯一键统一 UNIQUE(biz_col, delete_at)，实现「删了还能重建」

-- ============ usr_user ============
CREATE TABLE usr_user (
  id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
  openid        VARCHAR(64)  NOT NULL,
  unionid       VARCHAR(64)  DEFAULT NULL,
  role          VARCHAR(16)  NOT NULL DEFAULT 'UNSET',   -- CHILD/PARENT/ADMIN/UNSET
  nickname      VARCHAR(64)  DEFAULT NULL,
  avatar_url    VARCHAR(512) DEFAULT NULL,
  phone         VARCHAR(20)  DEFAULT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',  -- NORMAL/DISABLED
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at     BIGINT       NOT NULL DEFAULT 0,
  UNIQUE KEY uk_openid (openid, delete_at)
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
  UNIQUE KEY uk_invite_code (invite_code, delete_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ usr_family_member ============
CREATE TABLE usr_family_member (
  id            BIGINT      PRIMARY KEY AUTO_INCREMENT,
  family_id     BIGINT      NOT NULL,
  user_id       BIGINT      NOT NULL,
  relation_label VARCHAR(32) DEFAULT NULL,             -- 关系标注(爸/妈/娃)
  role          VARCHAR(16) NOT NULL,                  -- CHILD/PARENT
  bind_status   VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING/APPROVED/REJECTED
  guardian_status VARCHAR(16) DEFAULT 'NOT_REQUIRED',  -- NOT_REQUIRED/PENDING/APPROVED/REVOKED
  create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at     BIGINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_family_user (family_id, user_id, delete_at),
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
  profile_status VARCHAR(16) NOT NULL DEFAULT 'INCOMPLETE', -- INCOMPLETE/COMPLETED
  create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at     BIGINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_user (user_id, delete_at),
  KEY idx_family (family_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ usr_consent_log ============
CREATE TABLE usr_consent_log (
  id                 BIGINT      PRIMARY KEY AUTO_INCREMENT,
  user_id            BIGINT      NOT NULL,             -- 监护人(家长)
  child_id           BIGINT      NOT NULL,             -- 儿童
  family_id          BIGINT      NOT NULL,
  consent_type       VARCHAR(32) NOT NULL,            -- 如 ORDER/PROFILE
  action             VARCHAR(16) NOT NULL,            -- GRANT/REVOKE
  version            VARCHAR(16) NOT NULL,            -- 同意书版本
  self_reported_age  TINYINT     DEFAULT NULL,         -- 自报年龄核验
  guardian_status    VARCHAR(16) NOT NULL,           -- PENDING/APPROVED/REVOKED
  signed_at          BIGINT      DEFAULT NULL,
  expire_at          BIGINT      DEFAULT NULL,
  create_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at          BIGINT      NOT NULL DEFAULT 0,
  KEY idx_child (child_id),
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
  create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delete_at    BIGINT      NOT NULL DEFAULT 0,
  KEY idx_actor (actor_user_id),
  KEY idx_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
