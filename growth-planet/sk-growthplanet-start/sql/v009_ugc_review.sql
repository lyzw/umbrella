-- =====================================================================
-- v009_ugc_review：家庭私有菜品 UGC 审核留痕（里程碑 A 批次 2，D1 方案 A）
-- 目标：为运营端「UGC 审核队列」提供审核状态与驳回原因留痕，
--       不改动既有 status(ON_SALE/OFF_SALE) 与 visibility(PRIVATE/PUBLIC) 状态机。
-- 审核动作：通过 => visibility=PRIVATE→PUBLIC 且 review_status=APPROVED；
--           驳回 => review_status=REJECTED + reject_reason（visibility 保持 PRIVATE）。
-- 回滚：ALTER TABLE life_family_dish DROP INDEX idx_family_dish_review;
--       ALTER TABLE life_family_dish DROP CHECK chk_fdish_review;
--       ALTER TABLE life_family_dish DROP COLUMN reject_reason, DROP COLUMN review_status;
-- =====================================================================

ALTER TABLE life_family_dish ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '运营审核状态：NONE未送审/PENDING待审/APPROVED通过/REJECTED驳回';
ALTER TABLE life_family_dish ADD COLUMN reject_reason VARCHAR(255) DEFAULT NULL COMMENT '驳回原因（review_status=REJECTED 时有值）';
ALTER TABLE life_family_dish ADD CONSTRAINT chk_fdish_review CHECK (review_status IN ('NONE', 'PENDING', 'APPROVED', 'REJECTED'));
ALTER TABLE life_family_dish ADD KEY idx_family_dish_review (review_status, visibility, id);
