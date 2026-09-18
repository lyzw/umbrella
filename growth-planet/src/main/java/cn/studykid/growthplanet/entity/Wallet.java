package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("life_wallet")
public class Wallet {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long childId;
    private Long familyId;
    private BigDecimal balance = new BigDecimal("0.00");
    private Integer version = 0;
    private String status = "NORMAL";
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
}
