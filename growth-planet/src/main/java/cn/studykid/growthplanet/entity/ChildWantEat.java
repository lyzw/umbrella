package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 儿童每日想吃标记：想吃绑定到 (child_id, menu_date, meal_type)，实现「每日想吃」而非终身收藏。
 * 同一道菜（dish_type+dish_id）在同一天同一餐只计一次（唯一键不含 source_type）。
 * status 预留 MARKED/ADOPTED/COOKED 三态，P2 做采纳流转时复用。
 */
@Data
@TableName(value = "usr_child_want_eat", autoResultMap = true)
public class ChildWantEat {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long childId;
    private Long familyId;
    private LocalDate menuDate;
    private String mealType;
    private String sourceType;
    private String dishType;
    private Long dishId;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
    @Version
    private Integer version;
}
