package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 运营端编辑家务任务模板请求（M3 任务库）：全字段可选，传了才更新；status 仅 NORMAL/DISABLED。 */
@Data
public class AdminChoreTaskUpdateReq {

    @Size(max = 64)
    private String title;

    @Size(max = 255)
    private String description;

    @Size(max = 64)
    private String icon;

    @PositiveOrZero
    private Integer estimatedMinutes;

    @PositiveOrZero
    private BigDecimal rewardAmount;

    @Size(max = 16)
    private String cycle;

    @PositiveOrZero
    private Integer sortOrder;

    @Size(max = 16)
    private String status;
}
