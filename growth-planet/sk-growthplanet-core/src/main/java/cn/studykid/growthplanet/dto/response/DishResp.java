package cn.studykid.growthplanet.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DishResp {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long dishId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long categoryId;
    private String categoryName;
    private String name;
    private String imageUrl;
    private String virtualPrice;
    private Integer calories;
    private String tags;
    private List<String> allergens;
    private String allergenStatus;
    private Integer spiceLevel;
    private String status;
    private String sourceType;
    // ---- v011 配方摘要：轻量标量与计数平铺，列表页无需再查子表即可显示「30 分钟 · 2 人份 · 食材 5」 ----
    private Integer cookMinutes;
    private Integer servings;
    private String difficulty;
    private Integer ingredientCount;
    private Integer stepCount;
    /** 配方明细：仅详情路径填充；列表 / 菜单路径为 null（与「确无配方」的空列表语义精确分开）。 */
    private DishRecipeResp recipe;
}
