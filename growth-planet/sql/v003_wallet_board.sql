-- 成长星球 V0.0.2 · 零花钱看板（F-024~F-026）增量迁移
-- 说明：
--   1. 儿童维度的流水聚合已由 sprint2 的 idx_ledger_child_date (child_id, usage_date, id) 覆盖；
--   2. 家长端看板需按“家庭”统计本周支出/本周发放，补齐 (family_id, usage_date) 前缀，
--      避免家庭维度聚合成全表扫描；
--   3. 家庭虚拟总额按 life_wallet 聚合，已由 idx_wallet_family (family_id) 覆盖，无需新增。
ALTER TABLE life_allowance_log
  ADD INDEX idx_ledger_family_date (family_id, usage_date, id);
