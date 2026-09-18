package cn.studykid.growthplanet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConfirmRejectReq extends ConfirmVersionReq {
    @NotBlank @Size(max = 255)
    private String reason;
}
