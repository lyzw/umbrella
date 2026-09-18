package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("life_menu_confirm")
public class MenuConfirm {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String confirmNo;
    private Long childId;
    private Long familyId;
    private Long menuId;
    private LocalDate menuDate;
    private String mealType;
    private BigDecimal totalAmount;
    private String status;
    private Integer version = 0;
    private Long previousConfirmId;
    private String requestKey;
    private String requestHash;
    private String approvalRequestHash;
    private Boolean isOverLimit;
    private String remark;
    private Long parentId;
    private BigDecimal completedBalance;
    private Integer completedWalletVersion;
    private BigDecimal estimatedBalance;
    private LocalDateTime submitTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
}
