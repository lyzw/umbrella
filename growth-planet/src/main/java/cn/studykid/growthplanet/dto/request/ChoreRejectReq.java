package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ChoreRejectReq extends StrictRequest {
    @NotNull
    @Positive
    private Long id;
    @NotNull
    private Integer expectedVersion;
    @Size(max = 255)
    private String reason;
}
