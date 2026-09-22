package cn.studykid.growthplanet.dto.response;

import java.time.LocalDateTime;

public record AllowanceLogResp(String logId, String childId, String transType, String amount,
        String scene, String refId, String balanceBefore, String balanceAfter, LocalDateTime createTime) {
}
