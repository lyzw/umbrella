package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 监护人同意书记录表 usr_consent_log。
 */
@Data
@TableName("usr_consent_log")
public class ConsentLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 监护人（家长）用户 ID。 */
    private Long userId;

    /** 儿童用户 ID。 */
    private Long childId;

    private Long familyId;

    private Long applyId;

    private Integer applicationVersion;

    /** 同意书类型，如 ORDER/PROFILE。 */
    private String consentType;

    /** 动作：GRANT/REVOKE。 */
    private String action;

    /** 同意书版本。 */
    private String version;

    /** 自报年龄，不等于已核验身份。 */
    private Integer selfReportedAge;

    /** 声明/核验状态：UNVERIFIED/SELF_ATTESTED/VERIFIED，撤回由 action 表达。 */
    private String guardianStatus;

    private Long signedAt;

    private Long expireAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Long deleteAt = 0L;
}
