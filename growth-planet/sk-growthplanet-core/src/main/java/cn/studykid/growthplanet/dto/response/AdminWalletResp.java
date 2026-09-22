package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 钱包运营视图行（M4）：余额 + 额度规则（无规则行时额度字段为 null）。孩子姓名默认脱敏。 */
@Data
@Builder
public class AdminWalletResp {
    private Long id;
    private Long childId;
    private String childName;
    private Long familyId;
    private BigDecimal balance;
    private String status;
    private BigDecimal singleLimit;
    private BigDecimal dailyLimit;
    private BigDecimal dailyUsed;
    private BigDecimal weeklyLimit;
    private BigDecimal weeklyUsed;
    private LocalDateTime updateTime;
}
