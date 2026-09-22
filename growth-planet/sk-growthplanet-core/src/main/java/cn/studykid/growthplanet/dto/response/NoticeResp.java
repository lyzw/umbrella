package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class NoticeResp {
    private String id;
    private String eventType;
    private String familyId;
    private String childId;
    private boolean read;
    private LocalDateTime createTime;
    private String subscriptionNoticeId;
    private String subscriptionStatus;
}
