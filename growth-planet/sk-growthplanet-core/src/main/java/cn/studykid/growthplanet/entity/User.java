package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户表 usr_user。
 */
@Data
@TableName("usr_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 微信 openid。 */
    private String openid;

    private String unionid;

    /** 角色：CHILD/PARENT/ADMIN/UNSELECTED。 */
    private String role;

    private String nickname;

    private String avatarUrl;

    private String phone;

    /** 状态：NORMAL/DISABLED。 */
    private String status;

    private Long tokenVersion = 0L;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记，默认 0，删除置 UNIX_TIMESTAMP()*1000。 */
    private Long deleteAt = 0L;
}
