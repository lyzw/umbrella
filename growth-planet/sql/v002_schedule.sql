-- V0.0.2 日程提醒（F-036~F-038）
-- 在 V0.0.1（sprint1~4）与 v002_chore_medal.sql 之后执行。纯增量迁移，不改动既有表。
-- 回滚：仅 DROP TABLE life_schedule 即可，既有业务数据不受影响。
-- 时间口径：schedule_date 为日程发生起始日；schedule_time 为本地挂钟时刻（HH:mm，不做时区换算，
--   与 BusinessTime 的 Asia/Shanghai 一致）；remind_minutes 为提前提醒分钟数（0=准点，上限 1440）。
-- 到点投递：ScheduleReminderService 扫描「今日为发生日 且 当前时刻 >= 提醒时刻 且 last_remind_date <> 今日」
--   的日程，经 F-011 通知中台投递 IN_APP 站内提醒（订阅通道降级，失败不回滚业务，同 E-008）。
-- last_remind_date 为投递幂等标记，同一发生日只投递一次，且不补发已错过的历史时刻。

CREATE TABLE life_schedule (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  family_id         BIGINT NOT NULL,
  child_id          BIGINT NOT NULL,
  creator_id        BIGINT NOT NULL,
  title             VARCHAR(64) NOT NULL,
  category          VARCHAR(16) NOT NULL DEFAULT 'OTHER',
  note              VARCHAR(255) NOT NULL DEFAULT '',
  schedule_date     DATE NOT NULL,
  schedule_time     VARCHAR(5) NOT NULL,
  repeat_type       VARCHAR(16) NOT NULL DEFAULT 'ONCE',
  repeat_weekdays   VARCHAR(32) NOT NULL DEFAULT '',
  remind_minutes    INT NOT NULL DEFAULT 0,
  status            VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  last_remind_date  DATE DEFAULT NULL,
  create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  delete_at         BIGINT NOT NULL DEFAULT 0,
  KEY idx_schedule_child_date (child_id, status, schedule_date),
  KEY idx_schedule_scan (status, schedule_date, last_remind_date),
  CONSTRAINT chk_schedule_category CHECK (category IN ('HOMEWORK', 'CLASS', 'MEDICINE', 'OTHER')),
  CONSTRAINT chk_schedule_repeat CHECK (repeat_type IN ('ONCE', 'DAILY', 'WEEKLY')),
  CONSTRAINT chk_schedule_status CHECK (status IN ('NORMAL', 'CANCELLED')),
  CONSTRAINT chk_schedule_remind CHECK (remind_minutes BETWEEN 0 AND 1440)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
