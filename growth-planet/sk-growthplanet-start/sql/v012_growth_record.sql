-- =====================================================================
-- v012_growth_record：儿童成长记录（身高 / 体重 / BMI + 备注）
-- 目标：家长在「儿童信息」中记录孩子成长数据，画生长曲线并对照 WHO 标准。
-- 同一天允许多条（体检与日常可能同日记录），列表按 record_date DESC。
-- 回滚：DROP TABLE IF EXISTS usr_child_growth_record;
-- =====================================================================

CREATE TABLE usr_child_growth_record (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  child_id    BIGINT       NOT NULL               COMMENT '被记录儿童 user_id',
  family_id   BIGINT       NOT NULL               COMMENT '记录所属家庭（与儿童绑定一致）',
  record_date DATE         NOT NULL               COMMENT '测量日期（默认当天，可回填）',
  height_cm   DECIMAL(5,2) NOT NULL               COMMENT '身高（厘米）',
  weight_kg   DECIMAL(5,2) NOT NULL               COMMENT '体重（千克）',
  bmi         DECIMAL(4,1) NOT NULL               COMMENT 'BMI = 体重/身高^2，服务端计算',
  gender      VARCHAR(8)   NOT NULL               COMMENT '性别 BOY/GIRL（分位对照用）',
  age_years   INT          DEFAULT NULL           COMMENT '测量时年龄（岁，1-19，用于分位对照）',
  note        VARCHAR(200) DEFAULT NULL           COMMENT '备注（体检后 / 生病恢复期等）',
  create_by   BIGINT       NOT NULL               COMMENT '录入人（家长 user_id）',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at   BIGINT       NOT NULL DEFAULT 0,
  version     INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_growth_child (child_id, delete_at, record_date),
  CONSTRAINT chk_growth_height CHECK (height_cm BETWEEN 30.00 AND 250.00),
  CONSTRAINT chk_growth_weight CHECK (weight_kg BETWEEN 1.00 AND 200.00),
  CONSTRAINT chk_growth_bmi     CHECK (bmi BETWEEN 5.0 AND 60.0),
  CONSTRAINT chk_growth_age     CHECK (age_years IS NULL OR age_years BETWEEN 1 AND 19),
  CONSTRAINT chk_growth_gender   CHECK (gender IN ('BOY','GIRL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='儿童成长记录（身高体重BMI，家长录入）';
