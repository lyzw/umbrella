package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 家庭邀请码表 usr_family_invite。
 * 一个家庭可同时存在多个邀请码（家长邀请码 / 学生邀请码 / 可撤销 / 可过期 / 可限次），
 * 取代原 usr_family 上的单字段邀请码方案。
 */
@Data
@TableName("usr_family_invite")
public class FamilyInvite {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭 ID。 */
    private Long familyId;

    /** 6 位大写字母数字邀请码，全局唯一。 */
    private String code;

    /** 邀请码类型：PARENT / CHILD（锁定加入角色）。 */
    private String inviteType;

    /** 生成者（家长）用户 ID。 */
    private Long creatorUserId;

    /** 状态：ACTIVE / REVOKED。 */
    private String status;

    /** 过期时间（毫秒时间戳）。 */
    private Long expireAt;

    /** 最大使用次数；0 = 不限制。 */
    private Integer maxUse;

    /** 已使用次数。 */
    private Integer usedCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Long deleteAt = 0L;
}
