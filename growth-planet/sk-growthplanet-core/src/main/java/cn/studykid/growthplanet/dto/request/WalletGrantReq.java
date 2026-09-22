package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class WalletGrantReq extends StrictRequest {
    @NotNull @Positive
    private Long childId;
    @NotNull @DecimalMin("0.01") @DecimalMax("9999.99") @Digits(integer = 4, fraction = 2)
    private BigDecimal amount;
    @NotBlank @Size(max = 100)
    private String reason;
}
