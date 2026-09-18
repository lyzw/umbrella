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
