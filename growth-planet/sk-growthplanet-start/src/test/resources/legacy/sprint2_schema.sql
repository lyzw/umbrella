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
