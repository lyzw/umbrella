package cn.studykid.growthplanet.service;

import cn.studykid.growthplanet.entity.Schedule;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 日程重复规则与提醒时刻计算。
 * 配置查询（ScheduleService）与到点投递（ScheduleReminderService）共用同一口径，避免两处逻辑漂移。
 */
public final class ScheduleRules {
    private ScheduleRules() {
    }

    /** 该日程在 date 当天是否发生。 */
    public static boolean occursOn(Schedule schedule, LocalDate date) {
        if (schedule.getScheduleDate() == null || date == null || date.isBefore(schedule.getScheduleDate())) {
            return false;
        }
        return switch (schedule.getRepeatType() == null ? "" : schedule.getRepeatType()) {
            case "ONCE" -> date.equals(schedule.getScheduleDate());
            case "DAILY" -> true;
            case "WEEKLY" -> weekdays(schedule.getRepeatWeekdays()).contains(date.getDayOfWeek().getValue());
            default -> false;
        };
    }

    /** 该日程在 date 当天的提醒时刻 = 发生时刻 − 提前分钟数。 */
    public static LocalDateTime remindAt(Schedule schedule, LocalDate date) {
        int ahead = schedule.getRemindMinutes() == null ? 0 : schedule.getRemindMinutes();
        return date.atTime(LocalTime.parse(schedule.getScheduleTime())).minusMinutes(ahead);
    }

    /** 解析 "1,3,5" 形式的星期集合（周一=1 ~ 周日=7）。 */
    public static Set<Integer> weekdays(String raw) {
        Set<Integer> days = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return days;
        }
        for (String part : raw.split(",")) {
            if (part.matches("[1-7]")) {
                days.add(Integer.valueOf(part));
            }
        }
        return days;
    }
}
