package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("life_allowance_log")
public class AllowanceLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long walletId;
    private Long childId;
    private Long familyId;
    private Long operatorId;
    private String transType;
    private String scene;
    private Long refId;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private Integer walletVersion;
    private LocalDate usageDate;
    private String requestKey;
    private String requestHash;
    private String reason;
    private LocalDateTime createTime;
    private Long deleteAt = 0L;
}
