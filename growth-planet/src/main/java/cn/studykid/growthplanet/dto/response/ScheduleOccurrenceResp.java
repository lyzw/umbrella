package cn.studykid.growthplanet.dto.response;

/** 日程按重复规则展开后的单条发生项（F-037 日历/列表视图）。 */
public record ScheduleOccurrenceResp(String scheduleId, String date, String time, String title,
                                    String category, String note, boolean reminded) {
}
