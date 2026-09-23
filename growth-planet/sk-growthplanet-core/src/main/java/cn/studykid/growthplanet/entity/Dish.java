package cn.studykid.growthplanet.entity;

import cn.studykid.growthplanet.dto.DishIngredient;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "life_dish", autoResultMap = true)
public class Dish {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String imageUrl;
    private BigDecimal virtualPrice;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer calories;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String tags;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> allergens;
    private String allergenStatus;
    private Integer spiceLevel;
    private String status;
    // ---- v011 配方字段：做法与小贴士（食材明细落 life_dish_ingredient，见 DishRecipeService） ----
    // updateStrategy=ALWAYS 是必需的：清空配方时才能把 null / 空数组真正写回库（R3）。
    @TableField(typeHandler = JacksonTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private List<String> cookSteps;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cookTips;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer cookMinutes;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer servings;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String difficulty;
    /** 瞬时字段：食材明细由 DishRecipeService 装配，不参与本表 SQL。 */
    @TableField(exist = false)
    private List<DishIngredient> ingredients;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
}
