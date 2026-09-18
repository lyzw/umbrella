package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConfirmApproveReq extends ConfirmVersionReq {
    private Boolean explicitConfirm = false;
    @PositiveOrZero
    private Integer walletVersion;
    @PositiveOrZero
    private Integer ruleVersion;
    @PositiveOrZero
    private Integer confirmVersion;
    private LocalDate usageDate;
}
