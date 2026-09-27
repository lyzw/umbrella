package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 儿童成长记录：家长在「儿童信息」中录入身高 / 体重，服务端计算 BMI。
 * 同一天允许多条（体检与日常可能同日记录），列表按 record_date DESC 展示。
 */
@Data
@TableName(value = "usr_child_growth_record", autoResultMap = true)
public class ChildGrowthRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long childId;
    private Long familyId;
    private LocalDate recordDate;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private BigDecimal bmi;
    /** 性别 BOY/GIRL（分位对照用，同一儿童应保持一致）。 */
    private String gender;
    /** 测量时年龄（岁，1-19）：用于生长曲线分位对照，不填则不评估。 */
    private Integer ageYears;
    private String note;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
    @Version
    private Integer version;
}
