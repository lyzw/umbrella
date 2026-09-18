package cn.studykid.growthplanet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 家庭成员关系表 usr_family_member。
 */
@Data
@TableName("usr_family_member")
public class FamilyMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;

    private Long userId;

    /** 关系标注（爸/妈/娃）。 */
    private String relationLabel;

    /** 角色：CHILD/PARENT。 */
    private String role;

    /** 绑定状态：PENDING/BOUND/REJECTED。 */
    private String bindStatus;

    /** 审批时声明/核验状态快照，实时授权须查询同意历史。 */
    private String guardianStatus;

    private Integer applicationVersion = 1;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Long deleteAt = 0L;
}
