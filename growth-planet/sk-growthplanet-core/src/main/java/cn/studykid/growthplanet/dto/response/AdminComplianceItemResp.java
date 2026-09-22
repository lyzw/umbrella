package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 合规清单项运营视图行（M5，资源=合规清单，view=CP/SA/RA，config=CP/SA）。 */
@Data
@Builder
public class AdminComplianceItemResp {
    private String itemKey;
    private String itemText;
    private boolean checked;
    private Long checkedBy;
    private LocalDateTime checkedAt;
}
