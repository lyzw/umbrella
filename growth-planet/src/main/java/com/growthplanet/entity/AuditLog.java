package com.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 系统审计日志表 sys_audit_log（仅插入，不更新/不删除）。
 */
@Data
@TableName("sys_audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人。 */
    private Long actorUserId;

    private Long familyId;

    /** 动作：LOGIN/ROLE/CREATE_FAMILY/JOIN/BIND/GRANT/REVOKE/EXPORT... */
    private String action;

    private String targetType;

    private Long targetId;

    private String ip;

    private String detail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private Long deleteAt = 0L;
}
