package cn.studykid.growthplanet.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class DishCategoryReq {
    @NotBlank @Size(max = 32)
    private String name;
    @NotNull @Min(0)
    private Integer sort = 0;
    @NotNull @Pattern(regexp = "ENABLED|DISABLED")
    private String status = "ENABLED";

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported category field");
    }
}
