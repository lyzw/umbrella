package cn.studykid.growthplanet.common;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 后台实体公共基类：创建/更新时间自动填充 + 逻辑删除字段。
 * 与现有 life_ 与 usr_ 实体字段约定保持一致（create_time/update_time/delete_at）。
 */
@Getter
@Setter
public abstract class BaseEntity {

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除：0=未删，其余为删除时间戳（全局逻辑删除值 UNIX_TIMESTAMP()*1000）。 */
    private Long deleteAt = 0L;
}
