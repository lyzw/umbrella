package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜品食材行（life_dish_ingredient）：预置菜品与家庭菜品共用一张表，由 owner_type 判别。
 * <p>
 * life_dish 与 life_family_dish 的自增序列独立，跨表无法建外键，因此归属完整性由服务层保证：
 * owner_type 由代码路径固定传入（预置路径 PRESET、家庭路径 FAMILY），绝不接受请求参数。
 */
@Data
@TableName("life_dish_ingredient")
public class DishIngredientRow {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** PRESET=预置菜品 FAMILY=家庭菜品；由服务层代码路径决定，非客户端可控。 */
    private String ownerType;
    /** owner_type 对应菜品表的主键。 */
    private Long dishId;
    private String name;
    /** 用量自由文本（200g / 适量），可空。 */
    private String amount;
    /** 展示顺序，0 起；与 delete_at 一起构成唯一键，防并发同序。 */
    private Integer sort;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
}
