package cn.studykid.growthplanet.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ConfirmSubmitReq extends StrictRequest {
    @NotNull @Positive
    private Long menuId;
    @NotNull @Size(min = 1, max = 20)
    private List<@NotNull @Valid OrderLineReq> items;
    @Size(max = 255)
    private String remark;
    @Positive
    private Long previousConfirmId;
}
