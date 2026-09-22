package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ConfirmVersionReq extends StrictRequest {
    @NotNull @PositiveOrZero
    private Integer expectedVersion;
}
