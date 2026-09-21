package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("life_menu_item")
public class MenuItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long confirmId;
    private Long dishId;
    private String sourceType;
    private String dishName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private String note;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private Long deleteAt = 0L;
}
