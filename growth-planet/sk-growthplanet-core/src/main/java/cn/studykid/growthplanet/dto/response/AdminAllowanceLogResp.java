package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 零花钱流水运营视图行（M4，life_allowance_log 只读）。孩子姓名默认脱敏。 */
@Data
@Builder
public class AdminAllowanceLogResp {
    private Long id;
    private Long walletId;
    private Long childId;
    private String childName;
    private Long familyId;
    private String transType;
    private String scene;
    private Long refId;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private LocalDate usageDate;
    private String reason;
    private LocalDateTime createTime;
}
