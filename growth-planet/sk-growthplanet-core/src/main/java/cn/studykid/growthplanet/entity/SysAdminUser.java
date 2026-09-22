package cn.studykid.growthplanet.entity;

import cn.studykid.growthplanet.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 后台运营账号 sys_admin_user（与 C 端 usr_user 完全隔离）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_admin_user")
public class SysAdminUser extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String name;
    private String password;
    private String salt;
    private Long roleId;
    private String status;              // ACTIVE / DISABLED
    private Long tokenVersion;          // 失效版本
    private String lastLoginIp;
    private LocalDateTime lastLoginTime;
    private Long creatorId;
}
