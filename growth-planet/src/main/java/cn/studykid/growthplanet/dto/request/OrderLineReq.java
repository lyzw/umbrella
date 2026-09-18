package cn.studykid.growthplanet.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class OrderLineReq {
    @NotNull @Positive
    private Long dishId;
    @NotNull @Min(1) @Max(9)
    private Integer quantity;
    @Size(max = 255)
    private String note;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported order line field");
    }
}
