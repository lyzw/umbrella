package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 隐私工单运营视图行（M5，资源=隐私工单，仅 CP/SA/RA 可见）。 */
@Data
@Builder
public class AdminPrivacyReqResp {
    private Long id;
    private Long requesterId;
    private Long childId;
    /**
     * 孩子昵称（PII）：隐私域隔离——仅 CP/SA 可见全名；RA 仅持 childId（本字段为 null），
     * 符合规划文档「C 端明细字段仅 CP 可见」的数据最小化要求。
     */
    private String childName;
    private Long familyId;
    private String requestType;
    private String status;
    private Long dueAt;
    private Long verifiedAt;
    private String resultRef;
    private Long expiresAt;
    private String errorCode;
    private Long operatorId;
    private Integer version;
    private LocalDateTime createTime;
}
