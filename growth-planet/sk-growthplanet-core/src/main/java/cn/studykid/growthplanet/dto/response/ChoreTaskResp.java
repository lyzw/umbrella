package cn.studykid.growthplanet.dto.response;

public record ChoreTaskResp(String taskId, String title, String description, String icon,
                           int estimatedMinutes, String rewardAmount, String cycle, int sortOrder, String status) {
}
