package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 家务实例运营视图行（M4，life_chore_instance 只读）。孩子姓名默认脱敏。 */
@Data
@Builder
public class AdminChoreInstanceResp {
    private Long id;
    private Long taskId;
    private Long familyId;
    private Long childId;
    private String childName;
    private String status;
    private LocalDate claimDate;
    private LocalDateTime submitTime;
    private LocalDateTime confirmTime;
    private BigDecimal rewardAmount;
    private Integer rewardGranted;
    private String medalCode;
    private String rejectReason;
}
