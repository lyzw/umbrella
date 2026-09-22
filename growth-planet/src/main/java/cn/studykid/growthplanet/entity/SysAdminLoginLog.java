package cn.studykid.growthplanet.entity;

import cn.studykid.growthplanet.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 后台登录日志 sys_admin_login_log。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_admin_login_log")
public class SysAdminLoginLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long adminId;
    private String username;
    private String ip;
    private String result;        // SUCCESS / FAILURE
    private String failReason;
    private LocalDateTime loginTime;
}
