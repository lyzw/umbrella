package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 打卡记录（F-034/F-035）。item_name 为写入时快照，项被删后记录仍可辨认。 */
@Data
@TableName("life_check_record")
public class CheckRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long childId;
    private Long itemId;
    private String itemName;
    private LocalDate checkDate;
    private LocalDateTime checkTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
}
