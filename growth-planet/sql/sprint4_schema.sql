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
