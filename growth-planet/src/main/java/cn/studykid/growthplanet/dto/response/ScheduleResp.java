package cn.studykid.growthplanet.dto.response;

public record ScheduleResp(String scheduleId, String childId, String title, String category, String note,
                           String scheduleDate, String scheduleTime, String repeatType, String repeatWeekdays,
                           int remindMinutes, String status, String creatorId) {
}
