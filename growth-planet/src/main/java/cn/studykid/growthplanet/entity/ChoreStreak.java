package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("life_chore_streak")
public class ChoreStreak {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long childId;
    private Integer currentStreak;
    private Integer longestStreak;
    private LocalDate lastClaimDate;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
}
