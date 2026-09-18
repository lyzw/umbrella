package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 家庭表 usr_family。
 */
@Data
@TableName("usr_family")
public class Family {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String familyName;

    /** 创建者（家长）用户 ID。 */
    private Long ownerUserId;

    /** 6 位大写字母数字邀请码。 */
    private String inviteCode;

    /** 邀请码过期时间（毫秒时间戳，24h）。 */
    private Long inviteCodeExpire;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Long deleteAt = 0L;
}
