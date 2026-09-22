package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 审批记录运营视图行（M4，life_confirm_approval 只读）。 */
@Data
@Builder
public class AdminApprovalRowResp {
    private Long id;
    private Long confirmId;
    private String confirmNo;
    private Long parentId;
    private String action;
    private String beforeStatus;
    private String afterStatus;
    private Boolean isOverLimit;
    private String reason;
    private LocalDateTime createTime;
}
