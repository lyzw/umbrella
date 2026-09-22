package cn.studykid.growthplanet.entity;

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
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
}
