package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("life_allowance_rule")
public class AllowanceRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long childId;
    private Long familyId;
    private BigDecimal singleLimit = new BigDecimal("30.00");
    private BigDecimal dailyLimit = new BigDecimal("30.00");
    private BigDecimal weeklyLimit = new BigDecimal("150.00");
    private BigDecimal dailyUsed = new BigDecimal("0.00");
    private BigDecimal weeklyUsed = new BigDecimal("0.00");
    private LocalDate dailyPeriod;
    private LocalDate weeklyPeriod;
    private Integer version = 0;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
}
