-- v013_child_birth：儿童档案增加出生年月，用于按年龄判断相关信息（成长标准/营养等）。
-- 非必填：存量档案无需回填；填写后 /child/profile 返回 ageYears（当前周岁）。
-- 回滚：ALTER TABLE usr_child_profile DROP COLUMN birth_year, DROP COLUMN birth_month;
ALTER TABLE usr_child_profile
  ADD COLUMN birth_year  SMALLINT DEFAULT NULL COMMENT '出生年',
  ADD COLUMN birth_month TINYINT  DEFAULT NULL COMMENT '出生月';
