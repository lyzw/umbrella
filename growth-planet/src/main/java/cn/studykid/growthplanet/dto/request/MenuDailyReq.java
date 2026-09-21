package cn.studykid.growthplanet.dto.request;

import cn.studykid.growthplanet.entity.DishRef;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class MenuDailyReq {
    @Size(max = 128)
    private String school;
    @NotNull
    private LocalDate menuDate;
    @NotNull @Pattern(regexp = "BREAKFAST|LUNCH|DINNER")
    private String mealType;
    @NotNull @Size(min = 1, max = 50)
    private List<@NotNull @Valid DishRef> dishIds;
    @NotNull @Pattern(regexp = "DRAFT|PUBLISHED")
    private String status = "PUBLISHED";

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported menu field");
    }
}
