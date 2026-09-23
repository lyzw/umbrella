package cn.studykid.growthplanet.dto.request;

import cn.studykid.growthplanet.dto.DishIngredient;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DishReq {
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
    @NotNull @Pattern(regexp = "ON_SALE|OFF_SALE")
    private String status;

    // ---- v011 配方字段：六个全部可选，且不参与「在售」闸门（配方缺失不构成儿童安全风险） ----
    // 刻意不挂 Bean Validation 注解：规则与中文报错原因由 DishRecipeService.validate 统一给出，
    // 避免 DTO 注解抢先失败、把「食材名称重复：猪肉」降级成笼统的字段校验提示。
    private List<DishIngredient> ingredients;
    private List<String> cookSteps;
    private String cookTips;
    private Integer cookMinutes;
    private Integer servings;
    private String difficulty;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported dish field");
    }
}
