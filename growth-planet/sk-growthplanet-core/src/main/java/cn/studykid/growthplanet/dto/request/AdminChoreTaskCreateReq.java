package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 运营端代家庭创建家务任务模板请求（M3 任务库）。
 * 说明：现模型任务按家庭（family_id）归属，无全局模板表；运营代客创建必须显式指定目标家庭，
 * 全局模板建模归 B 期（与奖励库同批）。
 */
@Data
public class AdminChoreTaskCreateReq {

    @NotNull
    @Positive
    private Long familyId;

    @NotBlank
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

    @NotBlank
    @Pattern(regexp = "DAILY|WEEKLY|ONCE")
    private String cycle;

    @PositiveOrZero
    private Integer sortOrder = 0;
}
