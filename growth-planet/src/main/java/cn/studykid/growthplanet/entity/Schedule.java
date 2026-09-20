package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 家庭日程/提醒（F-036）。 */
@Data
@TableName("life_schedule")
public class Schedule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long childId;
    private Long creatorId;
    private String title;
    private String category;
    private String note;
    private LocalDate scheduleDate;
    /** 本地挂钟时刻，格式 HH:mm，不做时区换算。 */
    private String scheduleTime;
    private String repeatType;
    /** 每周重复时的星期集合，形如 "1,3,5"（周一=1 ~ 周日=7）；非每周重复为空串。 */
    private String repeatWeekdays;
    private Integer remindMinutes;
    private String status;
    /** 投递幂等标记：最近一次已提醒的发生日。 */
    private LocalDate lastRemindDate;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
}
