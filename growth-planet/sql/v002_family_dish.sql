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
