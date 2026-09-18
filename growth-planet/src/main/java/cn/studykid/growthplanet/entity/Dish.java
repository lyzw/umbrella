package cn.studykid.growthplanet.entity;

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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
}
