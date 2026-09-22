package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class MenuDailyResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long menuId;
    private String sourceType;
    private LocalDate menuDate;
    private String mealType;
    private String status;
    private boolean canSubmit;
    private List<MenuDishResp> dishes;
    private List<String> missingDishIds;

    @Data
    @Builder
    public static class MenuDishResp {
        @JsonUnwrapped
        private DishResp dish;
        @JsonProperty("isDisliked")
        private boolean disliked;
        @JsonProperty("isFavorite")
        private boolean favorite;
        private boolean allergyConflict;
        private boolean canSelect;
        private String safetyStatus;
    }
}
