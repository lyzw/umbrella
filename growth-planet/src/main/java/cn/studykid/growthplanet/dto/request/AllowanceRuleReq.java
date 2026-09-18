package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class AllowanceRuleReq extends StrictRequest {
    @NotNull @Positive
    private Long childId;
    @NotNull @DecimalMin("0.00") @DecimalMax("9999.99") @Digits(integer = 4, fraction = 2)
    private BigDecimal singleLimit;
    @NotNull @DecimalMin("0.00") @DecimalMax("9999.99") @Digits(integer = 4, fraction = 2)
    private BigDecimal dailyLimit;
    @NotNull @DecimalMin("0.00") @DecimalMax("9999.99") @Digits(integer = 4, fraction = 2)
    private BigDecimal weeklyLimit;
    @NotNull @PositiveOrZero
    private Integer expectedVersion;
}
