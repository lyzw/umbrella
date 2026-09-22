-- =====================================================================
-- v010_compliance_checklist：运营合规清单（里程碑 A 批次 4 / M5 合规与隐私中心）
-- 用途：运营人员按 checklist 逐项勾检的合规留痕（数据最小化、留存期、越权自查等）。
-- 由 AdminDataInitializer 按 item_key 幂等播种默认条目（见下方回滚注释）。
-- 本表无外键依赖，可独立执行。
-- 回滚：DROP TABLE IF EXISTS sys_compliance_checklist;
-- =====================================================================

CREATE TABLE `sys_compliance_checklist` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `item_key`    VARCHAR(64)  NOT NULL                    COMMENT '清单项静态键（播种后不变）',
  `item_text`   VARCHAR(255) NOT NULL                    COMMENT '清单项描述',
  `checked`     TINYINT      NOT NULL DEFAULT 0          COMMENT '0=未勾检 1=已勾检',
  `checked_by`  BIGINT       DEFAULT NULL                COMMENT '最近勾检人（后台 admin_id）',
  `checked_at`  DATETIME     DEFAULT NULL                COMMENT '最近勾检时间',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_at`   BIGINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_key` (`item_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营合规清单（M5 合规与隐私中心）';
