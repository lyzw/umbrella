package cn.studykid.growthplanet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 运营端家务任务模板行（M3 任务库治理，跨家庭视图）。 */
@Data
@Builder
public class AdminChoreTaskResp {
    private Long id;
    private Long familyId;
    private String title;
    private String description;
    private String icon;
    private Integer estimatedMinutes;
    private BigDecimal rewardAmount;
    private String cycle;
    private Integer sortOrder;
    private String status;
    private LocalDateTime createTime;
}
