package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("life_chore_instance")
public class ChoreInstance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long familyId;
    private Long childId;
    private String status;
    private Integer version;
    private LocalDate claimDate;
    private LocalDateTime submitTime;
    private LocalDateTime confirmTime;
    private Long parentId;
    private BigDecimal rewardAmount;
    private Integer rewardGranted;
    private String medalCode;
    private String rejectReason;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
}
