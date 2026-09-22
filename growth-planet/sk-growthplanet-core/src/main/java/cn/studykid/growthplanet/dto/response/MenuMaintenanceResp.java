package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class MenuMaintenanceResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long menuId;
    private LocalDate menuDate;
    private String mealType;
    private String status;
    private List<DishResp> dishes;
    private List<String> missingDishIds;
}
