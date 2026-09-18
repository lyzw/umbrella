package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "life_menu_daily", autoResultMap = true)
public class MenuDaily {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceType;
    private String ownerKey;
    private String school;
    private Long familyId;
    private LocalDate menuDate;
    private String mealType;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> dishIds;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
}
