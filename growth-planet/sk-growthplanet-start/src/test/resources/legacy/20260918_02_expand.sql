-- 仅适用旧版六表 schema；先完成 01 预检和人工签核。MySQL DDL 隐式提交，禁止盲目重跑。
ALTER TABLE usr_user ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE usr_family_member ADD COLUMN application_version INT NOT NULL DEFAULT 1;
ALTER TABLE usr_consent_log
  ADD COLUMN apply_id BIGINT DEFAULT NULL,
  ADD COLUMN application_version INT DEFAULT NULL,
  MODIFY COLUMN self_reported_age INT DEFAULT NULL,
  ADD KEY idx_consent_scope (family_id, child_id, user_id, apply_id, application_version, consent_type, id);
ALTER TABLE sys_audit_log
  ADD COLUMN request_id VARCHAR(64) DEFAULT NULL,
  ADD COLUMN result VARCHAR(16) DEFAULT NULL,
  ADD COLUMN error_code VARCHAR(16) DEFAULT NULL,
  ADD KEY idx_request (request_id);

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
