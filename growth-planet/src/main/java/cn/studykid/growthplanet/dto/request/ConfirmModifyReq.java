package cn.studykid.growthplanet.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConfirmModifyReq extends ConfirmRejectReq {
    @NotNull @Size(min = 1, max = 20)
    private List<@NotNull @Valid OrderLineReq> items;
}
