package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.entity.Schedule;
import cn.studykid.growthplanet.mapper.ScheduleMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 日程到点提醒投递（F-038 / T-021）。
 * <p>
 * 复用 F-011 通知中台：经 {@link NoticeService#recordEvent} 写 IN_APP 站内通道保底、订阅通道降级授权，
 * 投递失败不回滚日程业务（同 E-008）。每个日程独立事务，单条失败既不中止本批次，也不影响其他日程。
 * <p>
 * 幂等与补发：{@code last_remind_date} 标记同一发生日只投递一次；错过的历史时刻不补发，
 * 避免弹出已过期的提醒。
 */
@Service
public class ScheduleReminderService {
    private static final Logger log = LoggerFactory.getLogger(ScheduleReminderService.class);

    private final ScheduleMapper schedules;
    private final NoticeService notices;
    private final BusinessTime time;
    private final TransactionTemplate transaction;

    public ScheduleReminderService(ScheduleMapper schedules, NoticeService notices, BusinessTime time,
            PlatformTransactionManager transactionManager) {
        this.schedules = schedules;
        this.notices = notices;
        this.time = time;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transaction.setTimeout(20);
    }

    /** 扫描到点日程并投递站内提醒，返回本次成功投递的日程数。 */
    public int deliverDueReminders() {
        LocalDate today = time.today();
        LocalDateTime now = time.now();
        var candidates = schedules.selectList(new QueryWrapper<Schedule>()
                .eq("status", "NORMAL").eq("delete_at", 0L).le("schedule_date", today)
                .and(w -> w.isNull("last_remind_date").or().ne("last_remind_date", today)));
        int delivered = 0;
        for (Schedule candidate : candidates) {
            try {
                if (Boolean.TRUE.equals(transaction.execute(status -> deliver(candidate.getId(), today, now)))) {
                    delivered++;
                }
            } catch (RuntimeException ex) {
                // 不记录事件键或任何日程内容，仅记录日程编号以便排查。
                log.warn("schedule_reminder_delivery_failed scheduleId={}", candidate.getId());
            }
        }
        return delivered;
    }

    private boolean deliver(Long scheduleId, LocalDate today, LocalDateTime now) {
        Schedule schedule = schedules.selectOne(new QueryWrapper<Schedule>()
                .eq("id", scheduleId).eq("delete_at", 0L).last("FOR UPDATE"));
        if (schedule == null || !"NORMAL".equals(schedule.getStatus())) {
            return false;
        }
        if (today.equals(schedule.getLastRemindDate()) || !ScheduleRules.occursOn(schedule, today)) {
            return false;
        }
        if (now.isBefore(ScheduleRules.remindAt(schedule, today))) {
            return false;
        }
        String eventKey = "schedule:" + schedule.getId() + ":" + today;
        // 提醒对象：日程归属儿童 + 创建家长（同一事件键按接收人分别成行）。
        notices.recordEvent(eventKey, "SCHEDULE_REMIND", schedule.getFamilyId(), schedule.getChildId(),
                schedule.getChildId());
        if (schedule.getCreatorId() != null && !schedule.getCreatorId().equals(schedule.getChildId())) {
            notices.recordEvent(eventKey, "SCHEDULE_REMIND", schedule.getFamilyId(), schedule.getChildId(),
                    schedule.getCreatorId());
        }
        schedule.setLastRemindDate(today);
        schedules.updateById(schedule);
        return true;
    }
}
