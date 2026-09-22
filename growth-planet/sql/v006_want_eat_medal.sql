-- P2 采纳反馈闭环：孩子想吃被家长标记「已做」（COOKED）后发放的勋章定义
-- 在 sprint1~4 + v002~v005 之后执行。
-- 回滚：把这三条定义的 status 置为 'DISABLED' 即可立即停发（无需改代码或删行）；
--       如需完全回退，可再把 chk_medal_category 还原为不含 'MEAL' 的四值约束。
-- 计数口径：COUNT(usr_child_want_eat WHERE child_id = ? AND status = 'COOKED')（逻辑删除行自动排除）。
-- 幂等：MedalService.award 以 (definition_id, child_id, ref_id = 阈值) 为幂等键，重复标记 COOKED 不会重复发。

-- v002 建表时把勋章分类限定为 CHORE/HEALTH/EXCHANGE/STREAK，餐食类需先扩约束，
-- 否则下面的 INSERT 会因 CHECK 失败（注意 INSERT IGNORE 会把它降级为警告而静默跳过）。
-- 先 DROP 再 ADD 同名约束，保证脚本重复执行安全。
ALTER TABLE life_medal_definition DROP CHECK chk_medal_category;
ALTER TABLE life_medal_definition ADD CONSTRAINT chk_medal_category
  CHECK (category IN ('CHORE', 'HEALTH', 'EXCHANGE', 'STREAK', 'MEAL'));

INSERT IGNORE INTO life_medal_definition (code, name, description, icon, category, condition_type, threshold, sort_order)
VALUES
  ('MEAL_COOKED_1',  '心愿达成',   '第一个想要吃的菜被做出来', '🌟', 'MEAL', 'COUNT', 1,  80),
  ('MEAL_COOKED_10', '小小心愿官', '累计 10 次想吃被做出来',   '🍽', 'MEAL', 'COUNT', 10, 90),
  ('MEAL_COOKED_30', '餐餐有回应', '累计 30 次想吃被做出来',   '🏆', 'MEAL', 'COUNT', 30, 100);
