package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ChoreTaskReq extends StrictRequest {
    @NotBlank
    @Size(max = 64)
    private String title;
    @Size(max = 255)
    private String description;
    @Size(max = 32)
    private String icon;
    @Min(0)
    @Max(1440)
    private Integer estimatedMinutes;
    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("9999.99")
    @Digits(integer = 4, fraction = 2)
    private BigDecimal rewardAmount;
    @NotBlank
    @Pattern(regexp = "ONCE|DAILY|WEEKLY")
    private String cycle;
    @Min(0)
    @Max(9999)
    private Integer sortOrder;
}
