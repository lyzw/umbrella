package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ChoreSubmitReq extends StrictRequest {
    @NotNull
    @Positive
    private Long instanceId;
}
