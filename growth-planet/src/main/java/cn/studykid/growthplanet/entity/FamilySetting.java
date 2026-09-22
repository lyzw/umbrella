package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭级设置：无本表行 = 使用默认值（上限 5、开启），不为存量家庭回填。
 * family_id 由家长鉴权派生，客户端禁传。
 */
@Data
@TableName("usr_family_setting")
public class FamilySetting {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    /** 心愿菜单可选菜品上限（1~10）。 */
    private Integer wishMenuMaxDishes;
    /** 心愿菜单功能开关（1 开启 / 0 关闭）。 */
    private Integer wishMenuEnabled;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long deleteAt = 0L;
}
