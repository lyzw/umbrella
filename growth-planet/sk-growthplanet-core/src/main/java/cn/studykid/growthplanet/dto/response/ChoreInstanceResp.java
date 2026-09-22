package cn.studykid.growthplanet.dto.response;

public record ChoreInstanceResp(String instanceId, String taskId, String taskTitle, String childId,
                               String status, int version, String claimDate, String submitDate, String confirmDate,
                               String parentId, String rewardAmount, boolean rewardGranted, String medalCode,
                               String rejectReason) {
}
