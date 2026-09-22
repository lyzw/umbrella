package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 监护人同意书记录运营视图行（M5，资源=同意留痕，仅 CP/SA/RA 可见）。 */
@Data
@Builder
public class AdminConsentResp {
    private Long id;
    private Long userId;
    private Long childId;
    /** 孩子昵称：隐私域隔离，仅 CP/SA 可见全名（RA 仅持 id）。 */
    private String childName;
    private Long familyId;
    private String consentType;
    private String action;
    private String version;
    private Integer selfReportedAge;
    private String guardianStatus;
    private Long signedAt;
    private Long expireAt;
    private LocalDateTime createTime;
}
