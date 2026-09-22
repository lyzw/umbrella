package cn.studykid.growthplanet.config;

import cn.studykid.growthplanet.service.ScheduleReminderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 日程提醒扫描（F-038）。
 * 开关与通知投递（notice.delivery-enabled）相互独立，默认关闭；
 * 关闭时仅不自动扫描，ScheduleReminderService 仍可被调用与测试。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "schedule.reminder-enabled", havingValue = "true")
public class ScheduleSchedulingConfiguration {
    private final ScheduleReminderService reminders;

    public ScheduleSchedulingConfiguration(ScheduleReminderService reminders) {
        this.reminders = reminders;
    }

    @Scheduled(fixedDelayString = "${schedule.scan-delay-ms:60000}",
            initialDelayString = "${schedule.scan-delay-ms:60000}")
    public void scan() {
        reminders.deliverDueReminders();
    }
}
