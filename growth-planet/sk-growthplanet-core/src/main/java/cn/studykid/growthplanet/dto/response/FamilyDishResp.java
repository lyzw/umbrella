package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 家庭私有菜品响应。不含 visibility（后端派生，对客户端透明）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyDishResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long dishId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long familyId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long categoryId;
    private String name;
    private String imageUrl;
    private String virtualPrice;
    private Integer calories;
    private String tags;
    private List<String> allergens;
    private String allergenStatus;
    private Integer spiceLevel;
    private String status;
    private Integer version;
    // ---- v011 配方摘要（与 DishResp 对齐；明细回显走 GET /api/mini/dishes/{type}/{dishId}/recipe） ----
    private Integer cookMinutes;
    private Integer servings;
    private String difficulty;
    private Integer ingredientCount;
    private Integer stepCount;
}
