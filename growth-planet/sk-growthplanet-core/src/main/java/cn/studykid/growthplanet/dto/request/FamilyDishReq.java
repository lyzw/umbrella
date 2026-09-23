package cn.studykid.growthplanet.dto.request;

import cn.studykid.growthplanet.dto.DishIngredient;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 家庭私有菜品录入/编辑请求。
 * 不含 familyId / visibility / status —— familyId/visibility 由后端从家长鉴权派生，
 * status 由独立上下架接口控制。字段复用 DishReq 的全集。
 */
@Data
public class FamilyDishReq {
    @NotNull @Positive
    private Long categoryId;
    @NotBlank @Size(max = 64)
    private String name;
    @Size(max = 255)
    private String imageUrl;
    @NotNull @DecimalMin("0.00") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2)
    private BigDecimal virtualPrice;
    @PositiveOrZero
    private Integer calories;
    @Size(max = 255)
    private String tags;
    @NotNull @Size(max = 20)
    private List<@NotBlank @Size(max = 64) String> allergens;
    @NotNull @Pattern(regexp = "UNKNOWN|DECLARED")
    private String allergenStatus;
    @NotNull @Min(0) @Max(3)
    private Integer spiceLevel;

    // ---- v011 配方字段：与 DishReq 保持字段全集一致（六个全部可选） ----
    // 校验规则由 DishRecipeService.validate 统一执行，不在此处挂 Bean Validation 注解。
    private List<DishIngredient> ingredients;
    private List<String> cookSteps;
    private String cookTips;
    private Integer cookMinutes;
    private Integer servings;
    private String difficulty;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported family dish field: " + name);
    }
}
