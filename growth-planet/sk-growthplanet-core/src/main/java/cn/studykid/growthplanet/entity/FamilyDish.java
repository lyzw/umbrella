package cn.studykid.growthplanet.entity;

import cn.studykid.growthplanet.dto.DishIngredient;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 家庭私有菜品实体。
 * family_id / visibility 由后端从家长鉴权派生，客户端禁传；跨家庭 100% 隔离。
 * visibility 本期固定 PRIVATE，预留 PUBLIC 供未来社区公开（UGC）。
 * version 为手写乐观锁（沿用家务模块 transition() 约定，不用 @Version 注解）。
 */
@Data
@TableName(value = "life_family_dish", autoResultMap = true)
public class FamilyDish {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
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
    private String visibility;
    private String status;
    /** 运营审核状态（v009，D1 方案 A）：NONE 未送审 / PENDING 待审 / APPROVED 通过 / REJECTED 驳回。 */
    private String reviewStatus;
    /** 驳回原因（review_status=REJECTED 时有值，运营审核留痕）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String rejectReason;
    // ---- v011 配方字段：做法与小贴士（食材明细落 life_dish_ingredient，owner_type=FAMILY） ----
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
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
}
